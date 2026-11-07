package me.limbo.smp.status;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * On-screen status feedback. Every duration-based status (stunned, tethered, silenced...) is shown to the affected
 * player as a screen shader (/posteffect, 26.3) from the resource pack. Optionally (screen-fx.mode) it can be a boss bar
 * instead, or both.
 *
 * Why this class is more than a command wrapper:
 *  - A post effect's numbers are fixed in its JSON, so fading is done with three pre-made strengths per effect
 *    (limbo:name_0 full, _1 ~60%, _2 ~30%) that are stepped through. Nothing ever snaps on or off.
 *  - Effects that blink (Kinetic Thief / Spinecutter stuns) are capped at the middle strength and fade out slowly,
 *    and re-applying an effect that is already showing just extends it (no remove/add churn, no flicker).
 *  - Kinetic Thief's slow is the same look as its stun but covers far less of the screen. Both can be active, only
 *    the stronger one is drawn, and they cross-fade when the stun ends.
 *  - At most screen-fx.max-stacked status looks are drawn at once, so a pile of effects never covers the screen.
 *  - The ambient layer (darkness fatigue) is hidden whenever any status look is showing: statuses always cover it.
 */
public final class ScreenFx implements Listener {

    /** One status look. `file` is the base name of the post effects in assets/limbo/post_effect. */
    public enum Fx {
        // victims
        ENCHAIN("enchain", "Enchained", BossBar.Color.RED, 1, null, 0, 0),
        SILENCE("silence", "Silenced", BossBar.Color.GREEN, 1, null, 0, 0),
        REPOSE("repose", "Time slowed", BossBar.Color.PINK, 1, null, 0, 0),
        AXIOM_GROUND("axiom", "Grounded", BossBar.Color.PURPLE, 1, null, 0, 0),
        BLACK_HOLE("black_hole", "Black Hole", BossBar.Color.PURPLE, 1, null, 0, 0),
        /** Stun and slow from Kinetic Thief share a look; the slow covers less. Blinky, so capped at 60%. */
        KINETIC_STUN("kinetic_stun", "Stunned", BossBar.Color.WHITE, 1, "kinetic", 1, 1),
        KINETIC_SLOW("kinetic_slow", "Slowed", BossBar.Color.WHITE, 1, "kinetic", 0, 0),
        SPINECUTTER("spinecutter", "Stunned", BossBar.Color.RED, 1, null, 0, 1),
        // on yourself
        EVOLUTION("evolution", "Evolved", BossBar.Color.GREEN, 1, null, 0, 0),
        BLACK_FLASH("black_flash", "Black Flash", BossBar.Color.RED, 1, null, 0, 0),
        CONSTRUCT("construct", "Unseen", BossBar.Color.BLUE, 1, null, 0, 0),
        // ambient: always loses to the statuses above
        FATIGUE("fatigue", "Darkness fatigue", BossBar.Color.WHITE, 0, null, 0, 0);

        final String file;
        final String title;
        final BossBar.Color barColor;
        /** 0 = ambient (hidden by any status), 1 = status. */
        final int layer;
        /** Looks in the same group never draw together: only the highest rank does. */
        final String group;
        final int rank;
        /** Strongest strength allowed: 0 = full, 1 = ~60%. Used by blinky effects. */
        final int cap;

        Fx(String file, String title, BossBar.Color barColor, int layer, String group, int rank, int cap) {
            this.file = file;
            this.title = title;
            this.barColor = barColor;
            this.layer = layer;
            this.group = group;
            this.rank = rank;
            this.cap = cap;
        }

        public static Fx byName(String s) {
            for (Fx f : values()) if (f.name().equalsIgnoreCase(s) || f.file.equalsIgnoreCase(s)) return f;
            return null;
        }
    }

    /** Strength index meaning "not drawn". 0 = full, 1 = mid, 2 = faint. */
    private static final int OFF = 3;
    private static final int OUT_STEP_RUNS = 2; // runs (2 ticks each) per fade-out step: 4 ticks

    private static final class Entry {
        long until;
        long start;
        long shownAt;
        int level = OFF;
        int outWait = 0;
        BossBar bar;
    }

    private final LimboPlugin plugin;
    private final Map<UUID, Map<Fx, Entry>> state = new HashMap<>();
    private final CommandSender silent;
    private BukkitTask task;

    public ScreenFx(LimboPlugin plugin) {
        this.plugin = plugin;
        CommandSender s;
        try {
            s = Bukkit.createCommandSender(feedback -> { });
        } catch (Throwable t) {
            s = Bukkit.getConsoleSender();
        }
        this.silent = s;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 2L, 2L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new ArrayList<>(state.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) wipe(p);
        }
        state.clear();
    }

    // ------------------------------------------------------------ settings

    /** shader (default) | bossbar | both | off */
    public String mode() {
        return plugin.getConfig().getString("screen-fx.mode", "shader").toLowerCase(Locale.ROOT);
    }

    public void setMode(String mode) {
        plugin.getConfig().set("screen-fx.mode", mode.toLowerCase(Locale.ROOT));
        plugin.saveConfig();
    }

    private boolean shaders() { String m = mode(); return m.equals("shader") || m.equals("both"); }
    private boolean bars() { String m = mode(); return m.equals("bossbar") || m.equals("both"); }
    private int maxStacked() { return Math.max(1, plugin.getConfig().getInt("screen-fx.max-stacked", 2)); }

    // ------------------------------------------------------------ api

    /** Shows `fx` to the entity if it is a player (mobs have no screen). Safe to call every tick: it just extends. */
    public void show(Entity who, Fx fx, int ticks) {
        if (!(who instanceof Player p) || ticks <= 0 || mode().equals("off")) return;
        long now = System.currentTimeMillis();
        Entry e = state.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Fx.class)).computeIfAbsent(fx, k -> new Entry());
        long until = now + ticks * 50L;
        if (e.until <= now) { e.start = now; e.shownAt = now; e.until = until; }   // newly shown
        else if (until > e.until) e.until = until;                                // already showing: just extend
        process(p);
    }

    /** Starts the fade-out of `fx` right now (the effect ended early: cleansed, died, chain broke...). */
    public void clear(Entity who, Fx fx) {
        if (!(who instanceof Player p)) return;
        Map<Fx, Entry> m = state.get(p.getUniqueId());
        if (m == null) return;
        Entry e = m.get(fx);
        if (e != null) e.until = 0;
    }

    /** Removes everything this player sees, no fade. */
    public void clearAll(Player p) {
        wipe(p);
        state.remove(p.getUniqueId());
    }

    /** Ambient layers (darkness fatigue) call this every tick they are true; they stop the moment it isn't. */
    public void ambient(Player p, Fx fx, boolean on) {
        if (!on) return;
        if (fx == Fx.FATIGUE && !plugin.getConfig().getBoolean("screen-fx.fatigue-shader", false)) return;
        show(p, fx, 24);
    }

    public boolean isShowing(UUID id, Fx fx) {
        Map<Fx, Entry> m = state.get(id);
        Entry e = m == null ? null : m.get(fx);
        return e != null && e.level < OFF;
    }

    // ------------------------------------------------------------ loop

    private void run() {
        for (UUID id : new ArrayList<>(state.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { state.remove(id); continue; }
            process(p);
        }
    }

    private void process(Player p) {
        Map<Fx, Entry> m = state.get(p.getUniqueId());
        if (m == null) return;
        long now = System.currentTimeMillis();

        // 1. what is active, and which of it may be drawn
        List<Fx> active = new ArrayList<>();
        for (Map.Entry<Fx, Entry> en : m.entrySet()) if (en.getValue().until > now) active.add(en.getKey());

        Map<Fx, Integer> target = new EnumMap<>(Fx.class);
        for (Fx fx : m.keySet()) target.put(fx, OFF);

        // statuses: one per group (highest rank), newest first, at most max-stacked
        List<Fx> all = new ArrayList<>();
        for (Fx fx : active) if (fx.layer == 1) all.add(fx);
        List<Fx> candidates = new ArrayList<>();
        for (Fx fx : all) {
            boolean beaten = false;
            for (Fx o : all) if (o != fx && fx.group != null && fx.group.equals(o.group) && o.rank > fx.rank) beaten = true;
            if (!beaten) candidates.add(fx);
        }
        candidates.sort(Comparator.comparingLong((Fx f) -> m.get(f).shownAt).reversed());
        int allowed = maxStacked();
        for (int i = 0; i < candidates.size() && i < allowed; i++) target.put(candidates.get(i), candidates.get(i).cap);

        // 2. the ambient layer only exists while no status is on screen (or still fading out)
        boolean statusVisible = false;
        for (Map.Entry<Fx, Entry> en : m.entrySet()) if (en.getKey().layer == 1 && en.getValue().level < OFF) statusVisible = true;
        for (Fx fx : active) if (fx.layer == 0 && !statusVisible && target.get(fx) == OFF) target.put(fx, fx.cap);

        // 3. step every look one notch toward where it should be
        for (Fx fx : new ArrayList<>(m.keySet())) {
            Entry e = m.get(fx);
            int want = target.get(fx);
            int next = e.level;
            if (e.level > want) { next = e.level - 1; e.outWait = 0; }                  // fading in / settling up: every run
            else if (e.level < want) {                                                    // fading out: slower, every other run
                if (++e.outWait >= OUT_STEP_RUNS) { next = e.level + 1; e.outWait = 0; }
            } else e.outWait = 0;
            if (next != e.level) { apply(p, fx, e.level, next); e.level = next; }
            updateBar(p, fx, e, now);
            if (e.level >= OFF && e.until <= now) {
                dropBar(p, e);
                m.remove(fx);
            }
        }
        if (m.isEmpty()) state.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------ drawing

    private void apply(Player p, Fx fx, int from, int to) {
        if (!shaders()) return;
        if (to < OFF) post(p, true, fx.file + "_" + to);   // add the new strength first so there is never a bare frame...
        if (from < OFF) post(p, false, fx.file + "_" + from); // ...then drop the old one (same tick)
    }

    /** Runs one /posteffect add|remove. @return what the command returned (false = it failed or did nothing). */
    public boolean raw(Player p, boolean add, String effectId) {
        String cmd = "posteffect " + (add ? "add " : "remove ") + p.getUniqueId() + " " + effectId;
        try {
            boolean ok = Bukkit.dispatchCommand(plugin.getConfig().getBoolean("screen-fx.console-sender", true) ? Bukkit.getConsoleSender() : silent, cmd);
            if (plugin.isDebug(p.getUniqueId())) plugin.getLogger().info("[fx] " + cmd + " -> " + ok);
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().warning("posteffect failed (" + cmd + "): " + t);
            return false;
        }
    }

    private void post(Player p, boolean add, String id) {
        String cmd = "posteffect " + (add ? "add " : "remove ") + p.getUniqueId() + " limbo:" + id;
        try {
            if (plugin.isDebug(p.getUniqueId())) plugin.getLogger().info("[fx] " + cmd);
            Bukkit.dispatchCommand(plugin.getConfig().getBoolean("screen-fx.console-sender", true) ? Bukkit.getConsoleSender() : silent, cmd);
        } catch (Throwable t) {
            plugin.getLogger().warning("posteffect failed (" + cmd + "): " + t);
        }
    }

    private void wipe(Player p) {
        Map<Fx, Entry> m = state.get(p.getUniqueId());
        if (m != null) for (Entry e : m.values()) dropBar(p, e);
        try {
            Bukkit.dispatchCommand(plugin.getConfig().getBoolean("screen-fx.console-sender", true) ? Bukkit.getConsoleSender() : silent,
                    "posteffect clear " + p.getUniqueId());
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ boss bars (mode bossbar / both)

    private void updateBar(Player p, Fx fx, Entry e, long now) {
        if (!bars() || fx.layer == 0) { dropBar(p, e); return; }
        if (e.until <= now) { dropBar(p, e); return; }
        long total = Math.max(1L, e.until - e.start);
        float progress = Math.max(0f, Math.min(1f, (e.until - now) / (float) total));
        if (e.bar == null) {
            e.bar = BossBar.bossBar(Msg.parse("<white>" + Msg.esc(fx.title)), progress, fx.barColor, BossBar.Overlay.PROGRESS);
            p.showBossBar(e.bar);
        } else {
            e.bar.progress(progress);
        }
    }

    private void dropBar(Player p, Entry e) {
        if (e.bar != null) {
            p.hideBossBar(e.bar);
            e.bar = null;
        }
    }

    // ------------------------------------------------------------ lifecycle

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // a stale effect from before a crash/relog must never stick to the screen
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) wipe(e.getPlayer()); }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { state.remove(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) { clearAll(e.getEntity()); }
}
