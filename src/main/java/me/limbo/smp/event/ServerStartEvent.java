package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.event.deity.Deity;
import me.limbo.smp.event.deity.DeityLines;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Server Start. The sleeping deity at spawn wakes, tells everyone (vaguely) to group up with their teams, hands
 * out darknesses with a slot machine, reveals the cursed items, then opens a Big Rift that sends every group to
 * its own random spot inside the world border. After that the server is live.
 *
 * Timeline (all lengths in config.yml, events.server-start.*):
 *   AWAKENING  the deity cracks open (~5s) and speaks
 *   GROUPING   countdown (group-seconds) while the deity tells everyone to gather in their groups
 *   ROLLING    everyone gets their darkness, shown with a slot machine title
 *   REVEAL     the cursed items for the start are revealed in chat
 *   RIFT       the Big Rift opens, pulls everyone in and sends the groups out together
 *   DONE       the deity goes quiet again
 *
 * While the event stays active, players who join later and haven't been treated yet get a quick version of the
 * same (darkness roll + cursed items + a random spot). Anyone in spectator is left alone (admins).
 */
public final class ServerStartEvent implements Listener {
    private enum Phase {
        IDLE, AWAKENING, GROUPING, ROLLING, REVEAL, RIFT, DONE;

        boolean ceremony() { return this != IDLE && this != DONE; }
    }

    private final LimboPlugin plugin;
    private final EventManager events;
    private final BigRift rift;
    private boolean active;
    private Phase phase = Phase.IDLE;
    private final Set<UUID> treated = new HashSet<>();
    private final Set<UUID> rolled = new HashSet<>();
    private final Set<UUID> participants = new HashSet<>();
    private final List<BukkitTask> script = new ArrayList<>();
    private BossBar bar;
    private boolean revealed;

    public ServerStartEvent(LimboPlugin plugin, EventManager events) {
        this.plugin = plugin;
        this.events = events;
        this.rift = new BigRift(plugin);
    }

    public boolean isActive() { return active; }
    public boolean hasBeenTreated(UUID id) { return treated.contains(id); }

    // ------------------------------------------------------------ persistence

    public void load(YamlConfiguration y) {
        active = y.getBoolean("server-start.active", false);
        phase = active ? Phase.DONE : Phase.IDLE; // a ceremony doesn't resume after a restart
        treated.clear();
        rolled.clear();
        for (String s : y.getStringList("server-start.treated")) {
            try { treated.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        for (String s : y.getStringList("server-start.rolled")) {
            try { rolled.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save(YamlConfiguration y) {
        y.set("server-start.active", active);
        y.set("server-start.treated", treated.stream().map(UUID::toString).collect(Collectors.toList()));
        y.set("server-start.rolled", rolled.stream().map(UUID::toString).collect(Collectors.toList()));
    }

    public void shutdown() {
        cancelScript();
        rift.close();
        hideBar();
    }

    // ------------------------------------------------------------ config

    private double groupSeconds() { return plugin.getConfig().getDouble("events.server-start.group-seconds", 60); }
    private double groupRadius() { return plugin.getConfig().getDouble("events.server-start.group-radius", 12); }
    private int riftOpenTicks() { return (int) (plugin.getConfig().getDouble("events.server-start.rift-open-seconds", 7) * 20); }
    private int lineTicks() { return (int) (plugin.getConfig().getDouble("events.server-start.line-seconds", 4) * 20); }

    private List<String> lines(String key) { return DeityLines.get(plugin, key); }

    // ------------------------------------------------------------ script helpers

    private void at(long delayTicks, Runnable r) {
        script.add(Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(0L, delayTicks)));
    }

    private void cancelScript() {
        for (BukkitTask t : script) t.cancel();
        script.clear();
    }

    private List<Player> eligible() {
        List<Player> l = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() != GameMode.SPECTATOR) l.add(p);
        return l;
    }

    // ------------------------------------------------------------ start / stop

    public void start(CommandSender admin) {
        if (active) {
            Msg.send(admin, "<red>The server start event is already running. Use /limbo event stop server_start first.");
            return;
        }
        Deity deity = events.deity();
        if (!deity.isPresent() && !deity.spawnDormant()) {
            Msg.send(admin, "<red>No overworld to put the deity in.");
            return;
        }
        active = true;
        treated.clear();
        rolled.clear();
        participants.clear();
        revealed = false;
        phase = Phase.AWAKENING;
        events.save();
        Msg.send(admin, "<gray>Server start event started for <white>" + eligible().size()
                + "<gray> players. The deity is waking up.");

        if (!deity.awaken(this::onAwake)) {
            Msg.send(admin, "<red>The deity couldn't wake up (is it already in a cinematic?). Event aborted.");
            active = false;
            phase = Phase.IDLE;
            events.save();
        }
    }

    public void stop(CommandSender admin) {
        if (!active) {
            Msg.send(admin, "<gray>The server start event isn't running.");
            return;
        }
        active = false;
        phase = Phase.IDLE;
        cancelScript();
        rift.close();
        hideBar();
        events.deity().cancelSpeech();
        events.save();
        Msg.send(admin, "<gray>Server start event stopped. Latecomers are no longer treated.");
    }

    // ------------------------------------------------------------ 1. the deity wakes, the groups gather

    private void onAwake() {
        if (!active) return;
        Deity deity = events.deity();
        int spoke = deity.sayAll(lines("awaken"), lineTicks());
        at(spoke + 20, this::beginGrouping);
    }

    private void beginGrouping() {
        if (!active) return;
        phase = Phase.GROUPING;
        Deity deity = events.deity();
        deity.sayAll(lines("group"), lineTicks());

        int total = (int) (groupSeconds() * 20);
        bar = BossBar.bossBar(Msg.parse("<#9b59ff>The threshold is opening"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        BukkitRunnable countdown = new BukkitRunnable() {
            int elapsed = 0;
            boolean warned = false;

            @Override public void run() {
                if (!active || phase != Phase.GROUPING) { cancel(); return; }
                int left = Math.max(0, total - elapsed);
                int secs = left / 20;
                bar.name(Msg.parse("<#9b59ff>The threshold opens in <white>" + String.format("%d:%02d", secs / 60, secs % 60)
                        + " <dark_gray>| <gray>stay close to your group"));
                bar.progress(total == 0 ? 0f : Math.max(0f, Math.min(1f, left / (float) total)));
                for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
                if (!warned && left <= 20 * 20 && total > 20 * 20 * 2) {
                    warned = true;
                    events.deity().sayAll(lines("group-warning"), lineTicks());
                }
                if (elapsed >= total) {
                    cancel();
                    hideBar();
                    beginRolling();
                }
                elapsed += 10;
            }
        };
        script.add(countdown.runTaskTimer(plugin, 0L, 10L));
    }

    private void hideBar() {
        if (bar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        bar = null;
    }

    // ------------------------------------------------------------ 2. darknesses, the slot machine

    private void beginRolling() {
        if (!active) return;
        phase = Phase.ROLLING;
        participants.clear();
        List<Player> who = eligible();
        for (Player p : who) participants.add(p.getUniqueId());

        Deity deity = events.deity();
        deity.sayAll(lines("darkness"), lineTicks());

        AtomicInteger remaining = new AtomicInteger(who.size());
        revealed = false;
        for (Player p : who) {
            Darkness d = rollFor(p);
            if (d == null) { remaining.decrementAndGet(); continue; }
            long delay = 20 + ThreadLocalRandom.current().nextInt(40); // so they don't all land on the same tick
            at(delay, () -> {
                if (!p.isOnline()) return;
                DarknessRoll.play(plugin, p, d, 5, () -> {
                    Msg.send(p, "<#9b59ff>Your darkness: <white>" + Msg.esc(d.displayName())
                            + "<gray>. <dark_gray>/limbo darknessinfo for details.");
                    if (remaining.decrementAndGet() <= 0) beginReveal();
                });
            });
        }
        if (who.isEmpty()) at(40, this::beginReveal);
        at(20L * 16, this::beginReveal); // safety net if someone logged off mid-roll
    }

    /** Fresh darkness (siphon cleared), same as /limbo reset. Marks the player as rolled for this event. */
    private Darkness rollFor(Player p) {
        Darkness d = plugin.data().assignRandom(p.getUniqueId());
        plugin.data().clearSiphon(p.getUniqueId());
        plugin.data().saveIfDirty();
        plugin.items().syncTotems(p);
        rolled.add(p.getUniqueId());
        return d;
    }

    // ------------------------------------------------------------ 3. cursed items

    private void beginReveal() {
        if (!active || revealed || phase != Phase.ROLLING) return;
        revealed = true;
        phase = Phase.REVEAL;

        List<Material> items = plugin.cursed().roll(false); // the start's cursed items
        events.deity().say("...", 20);
        int spoke = events.deity().sayAll(lines("cursed"), lineTicks());
        broadcastCursed(items);
        at(spoke + 20, this::beginRift);
    }

    private void broadcastCursed(List<Material> items) {
        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The cursed items have been revealed:"));
        for (Material m : items) Bukkit.broadcast(Msg.parse("<dark_gray>  - <white>" + Msg.esc(me.limbo.smp.item.CursedItems.prettyName(m))));
        Bukkit.broadcast(Msg.parse("<gray>Hold one and your darkness answers: your passives, and your first ability at double cooldown."));
    }

    private void sendCursedTo(Player p) {
        Msg.send(p, "<#9b59ff>The cursed items are: <white>" + Msg.esc(plugin.cursed().describe()));
        Msg.send(p, "<gray>Hold one and your darkness answers: your passives, and your first ability at double cooldown.");
    }

    // ------------------------------------------------------------ 4. the Big Rift

    private void beginRift() {
        if (!active) return;
        phase = Phase.RIFT;
        Deity deity = events.deity();
        Location at = deity.location();
        World world = events.world();
        if (at == null || world == null) { finish(); return; }

        deity.sayAll(lines("rift"), lineTicks());
        double yawRad = Math.toRadians(at.getYaw());
        Location bottom = at.clone().add(-Math.sin(yawRad) * 16, 0, Math.cos(yawRad) * 16);
        rift.open(bottom, at.getYaw(), riftOpenTicks());

        List<Player> here = new ArrayList<>();
        for (Player p : eligible()) if (participants.contains(p.getUniqueId())) here.add(p);
        rift.pull(here);

        at(riftOpenTicks() + 40L, () -> {
            // groups are decided now: whoever is next to each other travels together
            List<Player> going = new ArrayList<>();
            for (Player p : eligible()) if (participants.contains(p.getUniqueId())) going.add(p);
            List<List<Player>> groups = BigRift.groups(going, groupRadius());
            rift.sendOut(world, groups, p -> {
                treated.add(p.getUniqueId());
                p.showTitle(Title.title(Msg.parse("<#9b59ff><bold>THE LIMBO BEGINS"), Msg.parse("<gray>Survive."),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(800))));
            }, this::finish);
        });
    }

    private void finish() {
        if (!active) return;
        rift.close();
        phase = Phase.DONE;
        events.deity().say("...", 20);
        events.deity().sleep();
        events.save();
        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The server has started."));
        // anyone who joined during the ceremony and missed it
        for (Player p : eligible()) if (!treated.contains(p.getUniqueId())) treatLatecomer(p);
    }

    // ------------------------------------------------------------ latecomers

    /** Players who join while the event is running and haven't been treated yet. */
    public void treatLatecomer(Player p) {
        if (!active || treated.contains(p.getUniqueId())) return;
        if (phase.ceremony()) return; // the ceremony is still running: they're swept up by it or picked up at the end
        World world = events.world();
        if (world == null) return;

        Darkness d = rolled.contains(p.getUniqueId()) ? plugin.data().getDarkness(p) : rollFor(p);
        if (d == null) return;
        events.save();
        DarknessRoll.play(plugin, p, d, 5, () -> {
            if (!p.isOnline()) return;
            Msg.send(p, "<#9b59ff>Your darkness: <white>" + Msg.esc(d.displayName())
                    + "<gray>. <dark_gray>/limbo darknessinfo for details.");
            sendCursedTo(p);
            rift.sendOut(world, List.of(List.of(p)), q -> {
                treated.add(q.getUniqueId());
                events.save();
            }, null);
        });
    }

    // ------------------------------------------------------------ protection during the ceremony

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (active && phase.ceremony() && e.getEntity() instanceof Player) e.setCancelled(true);
    }

    // ------------------------------------------------------------ status

    public List<String> describe() {
        List<String> l = new ArrayList<>();
        l.add("<gray>Server start: " + (active ? "<green>active <gray>(" + phase.name().toLowerCase() + ")" : "<red>inactive")
                + " <dark_gray>(" + treated.size() + " players treated)");
        return l;
    }
}
