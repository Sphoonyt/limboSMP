package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.event.deity.Deity;
import me.limbo.smp.event.deity.DeityLines;
import me.limbo.smp.progress.Unlocks;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * The transition event: the end of the first stage and the start of the real server.
 *
 * The deity reawakens at spawn and summons up to 8 (events.progression.holders) of the people standing near it.
 * They are levitated, marked (purple name tag + glowing, nothing else) and launched in different directions, and
 * take no fall damage for it. The mark moves: a holder killed by another player hands it to the killer; a holder
 * who dies naturally or logs off hands it to a random player, and one who merely lost connection gets it back
 * when they return (if it is still with the player who took their place).
 *
 * Blocks (unmineable netherite) are set in a ring around the deity, one per holder. When every block has a holder
 * standing on it, the event finishes: everyone is levitated, the deity swells and explodes, the border grows,
 * the Nether, villagers and max enchants unlock, and each holder is rewarded with a null totem (claimed from chat)
 * and 3 Devotion Cores (the reroll item).
 *
 * Commands: /limbo event start progression, end (play the finale now), stop (abort, nothing unlocks).
 */
public final class ProgressionEvent implements Listener {
    private static final String TEAM = "limbo_marked";

    /** A holder who lost connection and may get the mark back. */
    private record Displaced(UUID replacement, long until) {}

    private final LimboPlugin plugin;
    private final EventManager events;
    private final Pedestals pedestals;

    private boolean active;
    private boolean running;     // intro finished: the pedestals are live
    private boolean finishing;   // the finale is playing
    private long startedAt;
    private int hold;            // ticks every pedestal has been occupied for
    private final Set<UUID> holders = new LinkedHashSet<>();
    private final Map<UUID, String> previousTeam = new HashMap<>();
    private final Map<UUID, Displaced> displaced = new HashMap<>();
    private final Set<UUID> noFall = new HashSet<>();
    private final List<BukkitTask> script = new ArrayList<>();
    private BukkitTask refresh;
    private BukkitTask pedestalTask;
    private BossBar bar;

    public ProgressionEvent(LimboPlugin plugin, EventManager events) {
        this.plugin = plugin;
        this.events = events;
        this.pedestals = new Pedestals(plugin);
    }

    public boolean isActive() { return active; }
    public Set<UUID> holders() { return Collections.unmodifiableSet(holders); }
    public Pedestals pedestals() { return pedestals; }

    // ------------------------------------------------------------ config

    private int lineTicks() { return (int) (plugin.getConfig().getDouble("events.progression.line-seconds", 4) * 20); }
    private List<String> lines(String key) { return DeityLines.get(plugin, key); }

    // ------------------------------------------------------------ persistence / lifecycle

    public void load(YamlConfiguration y) {
        active = y.getBoolean("progression.active", false);
        running = y.getBoolean("progression.running", active); // an event from before pedestals existed counts as running
        startedAt = y.getLong("progression.started-at", 0L);
        holders.clear();
        for (String s : y.getStringList("progression.holders")) {
            try { holders.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        pedestals.load(y);
    }

    public void save(YamlConfiguration y) {
        y.set("progression.active", active);
        y.set("progression.running", running);
        y.set("progression.started-at", startedAt);
        y.set("progression.holders", holders.stream().map(UUID::toString).collect(Collectors.toList()));
        pedestals.save(y);
    }

    public void startTasks() {
        // drop stale team entries that are no longer holders (e.g. after a crash)
        Team t = mainBoard().getTeam(TEAM);
        if (t != null) {
            for (String entry : new ArrayList<>(t.getEntries())) {
                Player p = Bukkit.getPlayerExact(entry);
                if (p == null || !holders.contains(p.getUniqueId())) t.removeEntry(entry);
            }
        }
        refresh = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshStatus, 40L, 40L);
        pedestalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pedestalTick, 40L, 5L);
    }

    public void shutdown() {
        if (refresh != null) refresh.cancel();
        if (pedestalTask != null) pedestalTask.cancel();
        refresh = null;
        pedestalTask = null;
        cancelScript();
        hideBar();
    }

    private void at(long delay, Runnable r) {
        script.add(Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(0L, delay)));
    }

    private void cancelScript() {
        for (BukkitTask t : script) t.cancel();
        script.clear();
    }

    // ------------------------------------------------------------ start

    public void start(CommandSender admin) {
        if (active) {
            Msg.send(admin, "<red>The progression event is already running.");
            return;
        }
        Deity deity = events.deity();
        if (!deity.isPresent() && !deity.spawnDormant()) {
            Msg.send(admin, "<red>No overworld to put the deity in.");
            return;
        }
        Location dl = deity.location();
        if (dl == null) { Msg.send(admin, "<red>The deity has no location."); return; }

        // the people who are "there": near the deity, not in spectator (admins watch from spectator)
        double radius = plugin.getConfig().getDouble("events.progression.summon-radius", 60);
        List<Player> pool = new ArrayList<>();
        for (Player p : dl.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(dl) <= radius * radius) pool.add(p);
        }
        if (pool.isEmpty()) {
            Msg.send(admin, "<red>Nobody is within " + (int) radius + " blocks of the deity. Get people to spawn first.");
            return;
        }
        Collections.shuffle(pool);
        int n = Math.min(plugin.getConfig().getInt("events.progression.holders", 8), pool.size());
        List<UUID> chosen = new ArrayList<>();
        for (int i = 0; i < n; i++) chosen.add(pool.get(i).getUniqueId());

        active = true;
        running = false;
        finishing = false;
        hold = 0;
        startedAt = System.currentTimeMillis();
        holders.clear();
        displaced.clear();

        World w = events.world();
        double start = plugin.getConfig().getDouble("events.progression.border-start", 0);
        if (w != null && start > 0) w.getWorldBorder().setSize(start);

        events.save();
        Msg.send(admin, "<gray>Progression event started: the deity is waking, <white>" + n + "<gray> players will be marked.");
        if (!deity.awaken(() -> intro(chosen))) {
            Msg.send(admin, "<red>The deity is busy with another cinematic. Event aborted.");
            active = false;
            events.save();
        }
    }

    // ------------------------------------------------------------ the intro: summon, levitate, mark, launch

    private void intro(List<UUID> chosen) {
        if (!active) return;
        Deity deity = events.deity();
        int spoke = deity.sayAll(lines("reawaken"), lineTicks());

        at(spoke + 20, () -> summon(chosen));
    }

    private void summon(List<UUID> chosen) {
        if (!active) return;
        Deity deity = events.deity();
        deity.sayAll(lines("summon"), lineTicks());

        List<Player> marked = new ArrayList<>();
        for (UUID id : chosen) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || p.getGameMode() == GameMode.SPECTATOR) continue;
            marked.add(p);
            noFall.add(id);
            giveStatus(p, "You have been marked.");
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1, false, false, false));
            Location above = p.getLocation().add(0, 1, 0);
            ParticleUtil.burst(above, Color.fromRGB(155, 89, 255), 50);
            p.getWorld().playSound(above, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.6f);
        }
        if (marked.isEmpty()) {
            plugin.getLogger().warning("Progression event: nobody to mark, ending it.");
            active = false;
            events.save();
            return;
        }

        // the blocks appear while they hover
        placePedestals(marked.size());

        // levitate for 3s, then launch each one in their own direction
        at(60, () -> {
            if (!active) return;
            double offset = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            List<Player> order = new ArrayList<>(marked);
            Collections.shuffle(order);
            for (int i = 0; i < order.size(); i++) {
                Player p = order.get(i);
                if (!p.isOnline()) continue;
                p.removePotionEffect(PotionEffectType.LEVITATION);
                launch(p, offset + Math.PI * 2 * i / order.size());
            }
            events.deity().sayAll(lines("launch"), lineTicks());
            at(lineTicks() + 40L, () -> {
                if (!active) return;
                running = true;
                hold = 0;
                events.save();
                events.deity().sayAll(lines("pedestals"), lineTicks());
            });
        });
    }

    private void placePedestals(int count) {
        Deity deity = events.deity();
        Location dl = deity.location();
        if (dl == null) return;
        double radius = plugin.getConfig().getDouble("events.progression.pedestal-radius", 18);
        Material mat = Material.matchMaterial(plugin.getConfig().getString("events.progression.pedestal-block", "NETHERITE_BLOCK"));
        if (mat == null || !mat.isBlock()) mat = Material.NETHERITE_BLOCK;
        pedestals.place(dl.getWorld(), dl, count, radius, mat);
        for (int i = 0; i < pedestals.size(); i++) { // a little flourish as each one appears
            Location top = pedestals.top(i);
            ParticleUtil.burst(top.clone().add(0, 0.5, 0), Color.fromRGB(155, 89, 255), 30);
            top.getWorld().playSound(top, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        }
    }

    /** Carries the player in a flat arc for a couple of seconds; they take no fall damage until they land. */
    private void launch(Player p, double angle) {
        Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
        double speed = plugin.getConfig().getDouble("events.progression.launch-speed", 1.5);
        int ticks = plugin.getConfig().getInt("events.progression.launch-ticks", 40);
        noFall.add(p.getUniqueId());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);

        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                if (t >= ticks) { cancel(); watchLanding(p); return; }
                double y = t < 10 ? 0.75 : t < 25 ? 0.06 : -0.05;
                Vector v = dir.clone().multiply(speed).setY(y);
                WorldBorder border = p.getWorld().getWorldBorder();
                if (!border.isInside(p.getLocation().add(dir.clone().multiply(speed * 4)))) v.setX(0).setZ(0);
                p.setVelocity(v);
                Location l = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 6, 0.3, 0.4, 0.3, 0,
                        new Particle.DustTransition(Color.fromRGB(155, 89, 255), Color.fromRGB(10, 8, 16), 1.8f), true);
                p.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0.1, 0.1, 0.1, 0.02, null, true);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void watchLanding(Player p) {
        new BukkitRunnable() {
            int waited = 0, grounded = 0;

            @Override public void run() {
                waited += 4;
                if (!p.isOnline()) { noFall.remove(p.getUniqueId()); cancel(); return; }
                if (p.isOnGround() || p.isInWater()) grounded += 4; else grounded = 0;
                if (grounded >= 20 || waited >= 600) { noFall.remove(p.getUniqueId()); cancel(); }
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && noFall.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ the pedestals

    private void pedestalTick() {
        if (!active || !running || finishing || pedestals.isEmpty()) { hideBar(); return; }
        int total = pedestals.size();
        boolean[] occupied = new boolean[total];
        for (UUID id : holders) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            int i = pedestals.indexAt(p);
            if (i >= 0) occupied[i] = true;
        }
        int count = 0;
        for (boolean b : occupied) if (b) count++;

        Deity deity = events.deity();
        Location heart = deity.heart();
        boolean beamTick = Bukkit.getCurrentTick() % 10 == 0;
        for (int i = 0; i < total; i++) {
            Location top = pedestals.top(i);
            World w = top.getWorld();
            if (w == null || !w.isChunkLoaded(top.getBlockX() >> 4, top.getBlockZ() >> 4)) continue;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Particle.DustTransition d = occupied[i]
                    ? new Particle.DustTransition(Color.fromRGB(228, 205, 255), Color.fromRGB(155, 89, 255), 1.4f)
                    : new Particle.DustTransition(Color.fromRGB(155, 89, 255), Color.fromRGB(10, 8, 16), 1.1f);
            for (int k = 0; k < (occupied[i] ? 10 : 4); k++) {
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, top.clone().add(r.nextDouble(-0.3, 0.3), r.nextDouble(0, 16), r.nextDouble(-0.3, 0.3)),
                        1, 0, 0, 0, 0, d, true);
            }
            if (occupied[i] && beamTick && heart != null && heart.getWorld() == w) { // energy to the deity
                Location from = top.clone().add(0, 1, 0);
                for (int k = 0; k <= 14; k++) {
                    double f = k / 14.0;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                            from.clone().add(heart.clone().subtract(from).toVector().multiply(f)), 1, 0, 0, 0, 0,
                            new Particle.DustTransition(Color.fromRGB(228, 205, 255), Color.fromRGB(155, 89, 255), 1.2f), true);
                }
            }
        }

        // progress, shown to the marked players
        int holdNeeded = (int) (plugin.getConfig().getDouble("events.progression.pedestal-hold-seconds", 3) * 20);
        if (count == total && holders.size() >= total) {
            if (hold == 0) deity.sayAll(lines("charging"), lineTicks());
            hold += 5;
            showBar("<#9b59ff>Hold your ground <gray>" + Math.max(0, (holdNeeded - hold + 19) / 20) + "s",
                    holdNeeded == 0 ? 1f : Math.min(1f, hold / (float) holdNeeded), BossBar.Color.WHITE);
            if (hold >= holdNeeded) beginFinale();
        } else {
            if (hold > 0) {
                hold = 0;
                deity.cancelSpeech();
            }
            showBar("<#9b59ff>The stones <white>" + count + "<gray>/<white>" + total + " <gray>- every marked player on a stone",
                    total == 0 ? 0f : count / (float) total, BossBar.Color.PURPLE);
        }
    }

    private void showBar(String text, float progress, BossBar.Color color) {
        if (bar == null) bar = BossBar.bossBar(Msg.parse(text), progress, color, BossBar.Overlay.PROGRESS);
        bar.name(Msg.parse(text));
        bar.progress(Math.max(0f, Math.min(1f, progress)));
        bar.color(color);
        for (UUID id : holders) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showBossBar(bar);
        }
    }

    private void hideBar() {
        if (bar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        bar = null;
    }

    // ------------------------------------------------------------ the finale

    /** /limbo event end progression: play the finale now (it also plays by itself when the stones are all held). */
    public void end(CommandSender admin) {
        if (!active) {
            Msg.send(admin, "<gray>The progression event isn't running.");
            return;
        }
        if (finishing) {
            Msg.send(admin, "<gray>The finale is already playing.");
            return;
        }
        Msg.send(admin, "<gray>Starting the finale.");
        beginFinale();
    }

    private void beginFinale() {
        if (!active || finishing) return;
        finishing = true;
        hideBar();
        cancelScript();
        List<UUID> finalHolders = new ArrayList<>(holders);
        Deity deity = events.deity();
        if (!deity.isPresent()) deity.spawnDormant();
        Location dl = deity.location();

        // everyone floats up
        double radius = plugin.getConfig().getDouble("events.progression.finale-radius", 150);
        if (dl != null) {
            for (Player p : dl.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(dl) > radius * radius) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 110, 1, false, false, false));
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
            }
        }
        deity.sayAll(lines("finale"), lineTicks());
        boolean ok = deity.expandAndShatter(() -> burst(finalHolders, radius), this::gone);
        if (!ok) { // couldn't play the cinematic, still complete the event
            burst(finalHolders, radius);
            gone();
        }
    }

    /** The deity explodes: everything for the main server is enabled and the holders are rewarded. */
    private void burst(List<UUID> finalHolders, double radius) {
        World w = events.world();
        Deity deity = events.deity();
        Location dl = deity.location();

        // border
        if (w != null) {
            double end = plugin.getConfig().getDouble("events.progression.border-end", 0);
            double grow = plugin.getConfig().getDouble("events.progression.border-grow", 500);
            double seconds = plugin.getConfig().getDouble("events.progression.border-expand-seconds", 60);
            WorldBorder b = w.getWorldBorder();
            double target = end > 0 ? end : (grow > 0 ? b.getSize() + grow : 0);
            if (target > 0) b.setSize(target, (long) seconds);
        }

        // everything for the main server
        plugin.unlocks().unlock(Unlocks.Feature.NETHER);
        plugin.unlocks().unlock(Unlocks.Feature.VILLAGERS);
        plugin.unlocks().unlock(Unlocks.Feature.ENCHANTS);

        // everyone drifts back down gently
        if (dl != null) {
            for (Player p : dl.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(dl) > radius * radius) continue;
                p.removePotionEffect(PotionEffectType.LEVITATION);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, false, false, false));
                noFall.add(p.getUniqueId());
                watchLanding(p);
            }
        }

        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The deity is gone. <gray>The Nether is open, villagers will trade again, "
                + "and the world is wider than it was."));
        blessedTrialsHook();
        rewardHook(finalHolders);
        clearAll();

        active = false;
        running = false;
        events.save();
    }

    /** After the shockwave has faded. */
    private void gone() {
        pedestals.remove();
        finishing = false;
        events.save();
    }

    /** Placeholder: the blessed trials aren't defined yet. */
    private void blessedTrialsHook() {
        plugin.getLogger().info("Progression event ended: blessed trials hook fired (no mechanics yet).");
    }

    /** Each holder gets a null totem (claimed from chat if their inventory is full) and rerolls to keep or share. */
    private void rewardHook(List<UUID> finalHolders) {
        int rerolls = plugin.getConfig().getInt("events.progression.reroll-reward", 3);
        for (UUID id : finalHolders) {
            plugin.rewards().grantTotem(id);
            plugin.rewards().grantCores(id, rerolls);
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Msg.send(p, "<#9b59ff>You held the mark to the end. <gray>You earned <white>" + rerolls
                        + " Devotion Core" + (rerolls == 1 ? "" : "s") + "<gray>. Keep them or share them: right click one to start a reroll ritual.");
            }
        }
        plugin.getLogger().info("Progression event ended: rewarded " + finalHolders.size() + " holders.");
    }

    // ------------------------------------------------------------ stop (abort)

    /** Aborts the event without unlocking anything. */
    public void stop(CommandSender admin) {
        if (!active && !finishing && pedestals.isEmpty()) {
            Msg.send(admin, "<gray>The progression event isn't running.");
            return;
        }
        active = false;
        running = false;
        finishing = false;
        cancelScript();
        hideBar();
        events.deity().cancelSpeech();
        for (Player p : Bukkit.getOnlinePlayers()) p.removePotionEffect(PotionEffectType.LEVITATION);
        noFall.clear();
        pedestals.remove();
        clearAll();
        events.save();
        Msg.send(admin, "<gray>Progression event aborted (nothing was unlocked).");
    }

    // ------------------------------------------------------------ the mark

    private Scoreboard mainBoard() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private Team team() {
        Team t = mainBoard().getTeam(TEAM);
        if (t == null) t = mainBoard().registerNewTeam(TEAM);
        t.color(NamedTextColor.DARK_PURPLE);
        return t;
    }

    private void giveStatus(Player p, String message) {
        holders.add(p.getUniqueId());
        applyVisuals(p);
        Msg.send(p, "<dark_purple>" + message);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
    }

    private void removeStatus(Player p) {
        holders.remove(p.getUniqueId());
        clearVisuals(p);
    }

    private void applyVisuals(Player p) {
        Team t = team();
        Team current = mainBoard().getEntryTeam(p.getName());
        if (current != null && !current.getName().equals(TEAM)) previousTeam.put(p.getUniqueId(), current.getName());
        t.addEntry(p.getName());
        p.setGlowing(true);
    }

    private void clearVisuals(Player p) {
        Team t = mainBoard().getTeam(TEAM);
        if (t != null) t.removeEntry(p.getName());
        String prev = previousTeam.remove(p.getUniqueId());
        if (prev != null) {
            Team pt = mainBoard().getTeam(prev);
            if (pt != null) pt.addEntry(p.getName());
        }
        p.setGlowing(false);
    }

    private void clearAll() {
        for (UUID id : new ArrayList<>(holders)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                clearVisuals(p);
                Msg.send(p, "<gray>The mark fades.");
            }
        }
        holders.clear();
        displaced.clear();
        Team t = mainBoard().getTeam(TEAM);
        if (t != null) for (String entry : new ArrayList<>(t.getEntries())) t.removeEntry(entry);
    }

    private void refreshStatus() {
        if (!active) return;
        Team t = team();
        for (UUID id : holders) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (!p.isGlowing()) p.setGlowing(true);
            if (!t.hasEntry(p.getName())) t.addEntry(p.getName());
        }
    }

    private Player randomRecipient(Player exclude) {
        List<Player> pool = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(exclude) || holders.contains(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR) continue;
            pool.add(p);
        }
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ------------------------------------------------------------ passing the mark

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!active || finishing) return;
        Player victim = e.getEntity();
        if (!holders.contains(victim.getUniqueId())) return;
        Player killer = victim.getKiller();
        removeStatus(victim);

        Player next;
        String why;
        if (killer != null && !killer.equals(victim) && !holders.contains(killer.getUniqueId())
                && killer.getGameMode() != GameMode.SPECTATOR) {
            next = killer;
            why = "You took the mark from " + Msg.esc(victim.getName()) + ".";
        } else {
            next = randomRecipient(victim); // natural death, or the killer is already marked
            why = "The mark has chosen you.";
        }
        if (next != null) giveStatus(next, why);
        events.save();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player quitter = e.getPlayer();
        noFall.remove(quitter.getUniqueId());
        if (!active || finishing) return;
        if (!holders.contains(quitter.getUniqueId())) return;
        removeStatus(quitter);
        Player next = randomRecipient(quitter);
        if (next != null) giveStatus(next, "The mark has chosen you.");

        // a dropped connection (timeout / error) isn't the same as logging off: they get it back if they return soon
        PlayerQuitEvent.QuitReason why = e.getReason();
        boolean dropped = why == PlayerQuitEvent.QuitReason.TIMED_OUT || why == PlayerQuitEvent.QuitReason.ERRONEOUS_STATE;
        double graceMinutes = plugin.getConfig().getDouble("events.progression.rejoin-grace-minutes", 10);
        if (dropped && graceMinutes > 0) {
            displaced.put(quitter.getUniqueId(), new Displaced(next == null ? null : next.getUniqueId(),
                    System.currentTimeMillis() + (long) (graceMinutes * 60_000L)));
        }
        events.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joiner = e.getPlayer();
        if (active && holders.contains(joiner.getUniqueId())) { // e.g. still marked across a restart
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (joiner.isOnline()) applyVisuals(joiner);
            }, 5L);
            return;
        }
        Displaced d = displaced.remove(joiner.getUniqueId());
        if (!active || finishing || d == null || d.until() < System.currentTimeMillis()) return;
        // give it back, taking it from whoever got it in their place (if they still have it)
        if (d.replacement() != null) {
            Player rep = Bukkit.getPlayer(d.replacement());
            if (rep == null || !holders.contains(rep.getUniqueId())) return;
            removeStatus(rep);
            Msg.send(rep, "<gray>The mark returns to its first holder.");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joiner.isOnline() && active) {
                giveStatus(joiner, "Your mark has returned to you.");
                events.save();
            }
        }, 10L);
    }

    // ------------------------------------------------------------ status

    public List<String> describe() {
        List<String> l = new ArrayList<>();
        l.add("<gray>Progression: " + (active ? "<green>active" + (finishing ? " <gray>(finale)" : running ? " <gray>(stones live)" : " <gray>(intro)") : "<red>inactive"));
        if (active) {
            List<String> names = new ArrayList<>();
            for (UUID id : holders) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                names.add(n != null ? n : id.toString().substring(0, 8));
            }
            l.add("<gray>Holders (" + holders.size() + "): <white>" + Msg.esc(String.join(", ", names)));
            l.add("<gray>Stones: <white>" + pedestals.size());
        }
        return l;
    }
}
