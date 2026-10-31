package me.limbo.smp.event.deity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The big deity at spawn. It is a pure particle construct (like the rifts), driven by a small state machine:
 *
 *   NONE -> DORMANT (a breathing cocoon, while everyone waits) -> AWAKENING (cracks, ~5s) -> AWAKE (full figure)
 *   AWAKE -> EXPANDING (the finale: swells, explodes) -> NONE
 *
 * It "speaks" through action bar subtitles (say / sayAll). State and position survive restarts (events.yml).
 * Everything it looks like is in DeityVisuals.
 */
public final class Deity {
    public enum State { NONE, DORMANT, AWAKENING, AWAKE, EXPANDING }

    private static final int STEP = 4;               // render every 4 ticks
    private static final int AWAKEN_TICKS = 100;     // 5s
    private static final int SWELL_TICKS = 80;       // finale charge-up
    private static final int SHOCKWAVE_TICKS = 40;   // finale explosion

    private final LimboPlugin plugin;
    private State state = State.NONE;
    private String worldName;
    private double x, y, z;
    private float yaw;
    private boolean located;

    private int stateTicks;
    private long renderTick;
    private Runnable onAwake, onBurst, onGone;
    private BukkitTask task;

    private String subtitle;
    private int subtitleUntil;
    private int nextSubtitleSend;
    private final List<BukkitTask> speech = new ArrayList<>();

    public Deity(LimboPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------ persistence / lifecycle

    public void load(YamlConfiguration cfg) {
        String s = cfg.getString("deity.state", "NONE");
        try { state = State.valueOf(s); } catch (IllegalArgumentException e) { state = State.NONE; }
        if (state == State.AWAKENING) state = State.DORMANT;
        if (state == State.EXPANDING) state = State.NONE;
        worldName = cfg.getString("deity.world");
        this.x = cfg.getDouble("deity.x");
        this.y = cfg.getDouble("deity.y");
        this.z = cfg.getDouble("deity.z");
        this.yaw = (float) cfg.getDouble("deity.yaw");
        located = worldName != null;
        stateTicks = 0;
    }

    public void save(YamlConfiguration cfg) {
        cfg.set("deity.state", state.name());
        if (located) {
            cfg.set("deity.world", worldName);
            cfg.set("deity.x", x);
            cfg.set("deity.y", y);
            cfg.set("deity.z", z);
            cfg.set("deity.yaw", yaw);
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, STEP);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        cancelSpeech();
    }

    // ------------------------------------------------------------ queries

    public State state() { return state; }
    public boolean isPresent() { return state != State.NONE; }

    /** Where the deity stands (its feet), or null if no world is available. */
    public @Nullable Location location() {
        if (!located) return null;
        World w = Bukkit.getWorld(worldName);
        return w == null ? null : new Location(w, x, y, z, yaw, 0f);
    }

    /** Its heart, roughly where the energy of the finale gathers. */
    public @Nullable Location heart() {
        Location l = location();
        return l == null ? null : l.add(0, 9, 0);
    }

    private double hearingRadius() {
        return plugin.getConfig().getDouble("events.deity.hearing-radius", 160.0);
    }

    /** Players who can see / hear the deity. */
    public Collection<Player> audience() {
        Location l = location();
        if (l == null) return List.of();
        return l.getWorld().getNearbyPlayers(l, hearingRadius());
    }

    // ------------------------------------------------------------ placing it

    /** Moves the deity (and remembers it). Facing is the yaw it looks towards. */
    public void place(Location where, float facingYaw) {
        worldName = where.getWorld().getName();
        x = where.getX();
        y = where.getY();
        z = where.getZ();
        yaw = facingYaw;
        located = true;
        plugin.events().save();
    }

    /** Default spot: the overworld's spawn, on the surface. */
    private boolean ensureLocated() {
        if (located && location() != null) return true;
        World w = plugin.events().world();
        if (w == null) return false;
        Location sp = w.getSpawnLocation();
        int top = w.getHighestBlockYAt(sp.getBlockX(), sp.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        place(new Location(w, sp.getBlockX() + 0.5, top + 1, sp.getBlockZ() + 0.5), 0f);
        return true;
    }

    // ------------------------------------------------------------ state changes

    /** Puts the sleeping deity at spawn (or wherever it was placed). */
    public boolean spawnDormant() {
        if (!ensureLocated()) return false;
        setState(State.DORMANT);
        return true;
    }

    /** Removes it instantly. */
    public void remove() {
        setState(State.NONE);
        cancelSpeech();
        subtitle = null;
    }

    /** Dormant -> awake, with the cracking cinematic. `done` runs the moment it is fully awake. */
    public boolean awaken(@Nullable Runnable done) {
        if (!ensureLocated()) return false;
        if (state == State.AWAKE) {
            if (done != null) done.run();
            return true;
        }
        if (state == State.AWAKENING || state == State.EXPANDING) return false;
        if (state == State.NONE) setState(State.DORMANT);
        onAwake = done;
        setState(State.AWAKENING);
        return true;
    }

    /** Awake -> dormant again (it goes quiet). */
    public void sleep() {
        if (state == State.NONE) return;
        Location l = location();
        if (l != null && state == State.AWAKE) {
            DeityVisuals.shell(l.getWorld(), l.clone().add(0, 6, 0), 5, 60, DeityVisuals.PURPLE, DeityVisuals.VOID, 2f);
            l.getWorld().playSound(l, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 3f, 0.5f);
        }
        setState(State.DORMANT);
    }

    /**
     * The finale: it swells, shakes, explodes in a shockwave and is gone.
     * @param atBurst the instant of the explosion (do the unlocks / border here)
     * @param gone    after the shockwave has faded
     */
    public boolean expandAndShatter(@Nullable Runnable atBurst, @Nullable Runnable gone) {
        if (!ensureLocated()) return false;
        if (state == State.EXPANDING) return false;
        if (state != State.AWAKE) setState(State.AWAKE);
        onBurst = atBurst;
        onGone = gone;
        setState(State.EXPANDING);
        return true;
    }

    private void setState(State s) {
        state = s;
        stateTicks = 0;
        plugin.events().save();
    }

    // ------------------------------------------------------------ voice (action bar subtitles)

    /** Shows one line for `ticks` ticks (re-sent so it doesn't fade early). Replaces any current line. */
    public void say(String line, int ticks) {
        subtitle = line;
        subtitleUntil = Bukkit.getCurrentTick() + ticks;
        nextSubtitleSend = 0;
    }

    /**
     * Plays several lines one after another. Returns how many ticks the whole thing takes, so callers can
     * schedule the next step after it.
     */
    public int sayAll(List<String> lines, int ticksPerLine) {
        int at = 0;
        for (String line : lines) {
            if (at == 0) say(line, ticksPerLine);
            else speech.add(Bukkit.getScheduler().runTaskLater(plugin, () -> say(line, ticksPerLine), at));
            at += ticksPerLine;
        }
        return at;
    }

    public void cancelSpeech() {
        for (BukkitTask t : speech) t.cancel();
        speech.clear();
    }

    private void sendSubtitle() {
        if (subtitle == null) return;
        int now = Bukkit.getCurrentTick();
        if (now >= subtitleUntil) { subtitle = null; return; }
        if (now < nextSubtitleSend) return;
        nextSubtitleSend = now + 30; // action bar lasts about 3s, refresh before it fades
        var msg = Msg.parse("<#9b59ff>◈ <#d9c2ff><italic>" + Msg.esc(subtitle));
        for (Player p : audience()) p.sendActionBar(msg);
    }

    // ------------------------------------------------------------ the tick

    private void tick() {
        renderTick += STEP;
        stateTicks += STEP;
        sendSubtitle();

        Location base = location();
        if (base == null || state == State.NONE) return;
        World w = base.getWorld();
        boolean watched = !w.getNearbyPlayers(base, 200).isEmpty();

        switch (state) {
            case DORMANT -> {
                if (watched) DeityVisuals.cocoon(new DeityVisuals.Brush(base, yaw, 1.0, 0), renderTick, 0);
                if (renderTick % 100 == 0) w.playSound(base.clone().add(0, 4, 0), Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.5f);
            }
            case AWAKENING -> tickAwakening(w, base, watched);
            case AWAKE -> {
                double grow = Math.min(1.0, stateTicks / 80.0);
                double scale = 0.4 + 0.6 * (1 - Math.pow(1 - grow, 3)); // ease-out growth after it breaks free
                if (watched) DeityVisuals.figure(new DeityVisuals.Brush(base, yaw, scale, 0), renderTick, 0);
                if (renderTick % 160 == 0) w.playSound(base.clone().add(0, 6, 0), Sound.BLOCK_BEACON_AMBIENT, 3f, 0.5f);
            }
            case EXPANDING -> tickExpanding(w, base, watched);
            default -> {}
        }
    }

    private void tickAwakening(World w, Location base, boolean watched) {
        double progress = Math.min(1.0, stateTicks / (double) AWAKEN_TICKS);
        if (watched) DeityVisuals.cocoon(new DeityVisuals.Brush(base, yaw, 1.0, 0.04 * progress), renderTick, progress);
        Location mid = base.clone().add(0, 4.5, 0);
        if (stateTicks % 20 == 0) w.playSound(mid, Sound.ENTITY_WARDEN_HEARTBEAT, 3f, (float) (0.5 + progress));
        if (stateTicks == 60) w.playSound(mid, Sound.BLOCK_END_PORTAL_SPAWN, 2.5f, 0.6f);

        if (stateTicks >= AWAKEN_TICKS) { // it breaks open
            if (watched) {
                DeityVisuals.shell(w, mid, 4, 120, DeityVisuals.LIGHT, DeityVisuals.PURPLE, 2f);
                for (int i = 0; i < 4; i++) {
                    DeityVisuals.ring(w, base.clone().add(0, 0.3 + i * 0.4, 0), 3 + i * 3, 50 + i * 20,
                            DeityVisuals.LIGHT, DeityVisuals.PURPLE, 1.8f);
                }
                w.spawnParticle(Particle.EXPLOSION_EMITTER, mid, 3, 1.5, 2, 1.5, 0, null, true);
            }
            w.playSound(mid, Sound.ENTITY_ENDER_DRAGON_GROWL, 4f, 0.6f);
            w.playSound(mid, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.5f);
            setState(State.AWAKE);
            Runnable r = onAwake;
            onAwake = null;
            if (r != null) r.run();
        }
    }

    private void tickExpanding(World w, Location base, boolean watched) {
        Location heart = base.clone().add(0, 9, 0);
        if (stateTicks < SWELL_TICKS) { // swell, shake, pull everything inwards
            double t = stateTicks / (double) SWELL_TICKS;
            if (watched) {
                DeityVisuals.figure(new DeityVisuals.Brush(base, yaw, 1.0 + t * 1.2, 0.05 + 0.3 * t), renderTick, Math.min(1.0, t * 1.4));
                double radius = 28 * (1 - t) + 4;
                DeityVisuals.shell(w, heart, radius, 50, DeityVisuals.PURPLE, DeityVisuals.LIGHT, 1.8f);
                DeityVisuals.shell(w, heart, radius * 0.6, 30, DeityVisuals.LIGHT, DeityVisuals.PURPLE, 1.4f);
            }
            if (stateTicks % 8 == 0) {
                w.playSound(heart, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 4f, (float) (0.5 + t * 1.2));
                w.playSound(heart, Sound.ENTITY_WARDEN_HEARTBEAT, 4f, (float) (0.6 + t));
            }
            return;
        }
        int e = stateTicks - SWELL_TICKS;
        if (e == 0) { // the burst
            w.playSound(heart, Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.5f);
            w.playSound(heart, Sound.ENTITY_WARDEN_SONIC_BOOM, 6f, 0.6f);
            w.playSound(heart, Sound.ENTITY_ENDER_DRAGON_DEATH, 5f, 0.7f);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 8; i++) {
                w.spawnParticle(Particle.EXPLOSION_EMITTER, heart.clone().add(r.nextDouble(-7, 7), r.nextDouble(-5, 7), r.nextDouble(-7, 7)),
                        1, 0, 0, 0, 0, null, true);
            }
            DeityVisuals.shell(w, heart, 2.5, 160, DeityVisuals.LIGHT, DeityVisuals.LIGHT, 3.5f);
            Runnable burst = onBurst;
            onBurst = null;
            if (burst != null) burst.run();
        }
        if (watched) { // expanding shockwaves, a sphere and a ground ring
            double radius = 4 + e * 1.4;
            DeityVisuals.shell(w, heart, radius, 140, DeityVisuals.LIGHT, DeityVisuals.PURPLE, 2.5f);
            DeityVisuals.ring(w, base.clone().add(0, 0.5, 0), radius * 1.1, 160, DeityVisuals.PURPLE, DeityVisuals.VOID, 2.5f);
        }
        if (e >= SHOCKWAVE_TICKS) {
            setState(State.NONE);
            Runnable gone = onGone;
            onGone = null;
            if (gone != null) gone.run();
        }
    }
}
