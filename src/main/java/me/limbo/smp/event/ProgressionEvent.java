package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Progression event. At the start N players (default 8) get the mark: purple nametag + glowing. The mark moves when
 * the holder dies to a player (the killer takes it), and goes to a random online non-holder when the holder dies
 * naturally, logs off, or was killed by someone who already holds it. A holder who logged off gets nothing back.
 *
 * While it runs: enchant levels are capped, the Nether is sealed, and the world border is at its starting size.
 * When it ends all of that is lifted, the border grows, the blessed trials start and the holders are rewarded.
 * (Blessed trials, the reward and the pedestal end condition are placeholders until they're defined.)
 */
public final class ProgressionEvent implements Listener {
    private static final String TEAM = "limbo_marked";

    private final LimboPlugin plugin;
    private final EventManager events;
    private boolean active;
    private long startedAt;
    private final Set<UUID> holders = new LinkedHashSet<>();
    private final Map<UUID, String> previousTeam = new HashMap<>();
    private BukkitTask refresh;

    public ProgressionEvent(LimboPlugin plugin, EventManager events) {
        this.plugin = plugin;
        this.events = events;
    }

    public boolean isActive() { return active; }
    public Set<UUID> holders() { return Collections.unmodifiableSet(holders); }

    // ------------------------------------------------------------ persistence / lifecycle

    public void load(YamlConfiguration y) {
        active = y.getBoolean("progression.active", false);
        startedAt = y.getLong("progression.started-at", 0L);
        holders.clear();
        for (String s : y.getStringList("progression.holders")) {
            try { holders.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save(YamlConfiguration y) {
        y.set("progression.active", active);
        y.set("progression.started-at", startedAt);
        y.set("progression.holders", holders.stream().map(UUID::toString).collect(Collectors.toList()));
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
    }

    public void shutdown() {
        if (refresh != null) refresh.cancel();
        refresh = null;
    }

    // ------------------------------------------------------------ start / end / stop

    public void start(CommandSender admin) {
        if (active) {
            Msg.send(admin, "<red>The progression event is already running.");
            return;
        }
        List<Player> pool = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() != GameMode.SPECTATOR) pool.add(p);
        if (pool.isEmpty()) {
            Msg.send(admin, "<red>Nobody is online.");
            return;
        }
        Collections.shuffle(pool);
        int n = Math.min(plugin.getConfig().getInt("events.progression.holders", 8), pool.size());

        active = true;
        startedAt = System.currentTimeMillis();
        holders.clear();

        World w = events.world();
        double start = plugin.getConfig().getDouble("events.progression.border-start", 0);
        if (w != null && start > 0) w.getWorldBorder().setSize(start);

        for (int i = 0; i < n; i++) giveStatus(pool.get(i), "You have been marked.");
        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The progression event has begun. <gray>" + n
                + " players carry the mark..."));
        events.save();
        Msg.send(admin, "<gray>Progression event started with <white>" + n + "<gray> holders.");
    }

    /** The event is completed: restrictions lift, the border grows, the trials begin, holders are rewarded. */
    public void end(CommandSender admin) {
        if (!active) {
            Msg.send(admin, "<gray>The progression event isn't running.");
            return;
        }
        List<UUID> finalHolders = new ArrayList<>(holders);
        active = false;

        World w = events.world();
        double endSize = plugin.getConfig().getDouble("events.progression.border-end", 0);
        double seconds = plugin.getConfig().getDouble("events.progression.border-expand-seconds", 60);
        if (w != null && endSize > 0) w.getWorldBorder().setSize(endSize, (long) seconds);

        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The progression event is over. <gray>Max enchants are unlocked, the Nether is open"
                + (endSize > 0 ? ", the world border is expanding" : "") + "."));
        blessedTrialsHook();
        rewardHook(finalHolders);
        clearAll();
        events.save();
        Msg.send(admin, "<gray>Progression event ended.");
    }

    /** Aborts the event without unlocking anything. */
    public void stop(CommandSender admin) {
        if (!active) {
            Msg.send(admin, "<gray>The progression event isn't running.");
            return;
        }
        active = false;
        clearAll();
        events.save();
        Msg.send(admin, "<gray>Progression event aborted (nothing was unlocked).");
    }

    /** Placeholder: the blessed trials aren't defined yet. */
    private void blessedTrialsHook() {
        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The blessed trials have begun."));
        plugin.getLogger().info("Progression event ended: blessed trials hook fired (no mechanics yet).");
    }

    /** Placeholder: the holders' reward isn't defined yet. */
    private void rewardHook(List<UUID> finalHolders) {
        plugin.getLogger().info("Progression event ended: reward hook for " + finalHolders.size() + " holders (not defined yet).");
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
        if (!active) return;
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
        if (!active) return;
        Player quitter = e.getPlayer();
        if (!holders.contains(quitter.getUniqueId())) return;
        removeStatus(quitter); // nothing is restored when they come back
        Player next = randomRecipient(quitter);
        if (next != null) giveStatus(next, "The mark has chosen you.");
        events.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (active && holders.contains(e.getPlayer().getUniqueId())) { // e.g. still marked across a restart
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (e.getPlayer().isOnline()) applyVisuals(e.getPlayer());
            }, 5L);
        }
    }

    // ------------------------------------------------------------ restrictions while it runs

    private boolean netherSealed(Location to) {
        return active && to != null && to.getWorld() != null && to.getWorld().getEnvironment() == World.Environment.NETHER;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!netherSealed(e.getTo())) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "<red>The Nether is sealed until the progression event ends.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (netherSealed(e.getTo())) e.setCancelled(true);
    }

    private int enchantCap() {
        return active ? plugin.getConfig().getInt("events.progression.enchant-cap", 3) : 0;
    }

    /** Enchanting table: clamp anything above the cap. */
    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        int cap = enchantCap();
        if (cap <= 0) return;
        e.getEnchantsToAdd().replaceAll((ench, level) -> Math.min(level, cap));
    }

    /** Anvil: refuse results that would be above the cap (items that were already above it are left alone). */
    @EventHandler(ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent e) {
        int cap = enchantCap();
        if (cap <= 0) return;
        ItemStack result = e.getResult();
        if (result == null) return;
        boolean over = false;
        for (Map.Entry<Enchantment, Integer> en : result.getEnchantments().entrySet()) {
            if (en.getValue() > cap) over = true;
        }
        if (!over && result.getItemMeta() instanceof EnchantmentStorageMeta sm) {
            for (int level : sm.getStoredEnchants().values()) if (level > cap) over = true;
        }
        if (over) e.setResult(null);
    }

    // ------------------------------------------------------------ status

    public List<String> describe() {
        List<String> l = new ArrayList<>();
        l.add("<gray>Progression: " + (active ? "<green>active" : "<red>inactive"));
        if (active) {
            List<String> names = new ArrayList<>();
            for (UUID id : holders) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                names.add(n != null ? n : id.toString().substring(0, 8));
            }
            l.add("<gray>Holders (" + holders.size() + "): <white>" + Msg.esc(String.join(", ", names)));
        }
        return l;
    }
}
