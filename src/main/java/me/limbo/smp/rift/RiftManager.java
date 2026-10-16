package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shadow rifts are pure particle constructs (no blocks). Each one has an invisible Interaction entity
 * as its click hitbox. 3 (configurable) rifts spawn at random sun-exposed spots inside the world border
 * every 24h (configurable). State lives in rifts.yml (real-time timestamps, survives restarts).
 */
public final class RiftManager {
    public enum Type { AUTO, MANUAL }

    public record Rift(String id, Location location, Type type, double yaw) {}

    private static final int MAX_ATTEMPTS = 150;

    private final LimboPlugin plugin;
    private final File file;
    private final NamespacedKey idKey;
    private final Map<String, Rift> rifts = new LinkedHashMap<>();
    private final Map<String, UUID> entities = new HashMap<>();

    private double intervalHours = 24.0;
    private int count = 3;
    private long lastSpawn = 0L;
    private long nextSpawn = 0L;
    private boolean spawning = false;
    private int tick = 0;
    private BukkitTask visuals;
    private BukkitTask scheduler;

    public RiftManager(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rifts.yml");
        this.idKey = new NamespacedKey(plugin, "rift_id");
    }

    // ------------------------------------------------------------ persistence

    public void load() {
        rifts.clear();
        intervalHours = plugin.getConfig().getDouble("rifts.spawn-interval-hours", 24.0);
        count = plugin.getConfig().getInt("rifts.spawn-count", 3);
        lastSpawn = 0L;
        nextSpawn = 0L;
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        intervalHours = y.getDouble("settings.interval-hours", intervalHours);
        count = y.getInt("settings.count", count);
        lastSpawn = y.getLong("spawn.last", 0L);
        nextSpawn = y.getLong("spawn.next", 0L);
        ConfigurationSection sec = y.getConfigurationSection("data");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            String worldName = sec.getString(id + ".world");
            World w = worldName == null ? null : Bukkit.getWorld(worldName);
            if (w == null) {
                plugin.getLogger().warning("Rift " + id + " is in a world that isn't loaded, skipping.");
                continue;
            }
            Location l = new Location(w, sec.getDouble(id + ".x"), sec.getDouble(id + ".y"), sec.getDouble(id + ".z"));
            Type t = "auto".equalsIgnoreCase(sec.getString(id + ".type")) ? Type.AUTO : Type.MANUAL;
            rifts.put(id, new Rift(id, l, t, sec.getDouble(id + ".yaw")));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("settings.interval-hours", intervalHours);
        y.set("settings.count", count);
        y.set("spawn.last", lastSpawn);
        y.set("spawn.next", nextSpawn);
        for (Rift r : rifts.values()) {
            String b = "data." + r.id();
            y.set(b + ".world", r.location().getWorld().getName());
            y.set(b + ".x", r.location().getX());
            y.set(b + ".y", r.location().getY());
            y.set(b + ".z", r.location().getZ());
            y.set(b + ".yaw", r.yaw());
            y.set(b + ".type", r.type().name().toLowerCase());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save rifts.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ lifecycle

    public void start() {
        if (nextSpawn == 0L) nextSpawn = System.currentTimeMillis(); // never spawned: first batch soon
        Bukkit.getScheduler().runTaskLater(plugin, () -> rifts.values().forEach(this::ensureEntity), 40L);

        visuals = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick += 4;
            double range = plugin.getConfig().getDouble("rifts.particle-range", 40);
            for (Rift r : rifts.values()) {
                Location l = r.location();
                World w = l.getWorld();
                if (w == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
                Location mid = l.clone().add(0, 1.6, 0);
                if (w.getNearbyPlayers(mid, range).isEmpty()) continue;
                RiftVisuals.render(l, r.yaw(), tick, r.id().hashCode());
                if (tick % 120 == 0) w.playSound(mid, Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 0.5f);
            }
        }, 20L, 4L);

        scheduler = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            rifts.values().forEach(this::ensureEntity); // repairs hitboxes that got killed/lost
            if (!spawning && System.currentTimeMillis() >= nextSpawn) spawnCycle(count);
        }, 100L, 600L);
    }

    public void shutdown() {
        if (visuals != null) visuals.cancel();
        if (scheduler != null) scheduler.cancel();
    }

    // ------------------------------------------------------------ queries

    public Collection<Rift> all() { return new ArrayList<>(rifts.values()); }
    public @Nullable Rift get(String id) { return rifts.get(id); }
    public boolean exists(String id) { return rifts.containsKey(id); }
    public double intervalHours() { return intervalHours; }
    public int count() { return count; }
    public long nextSpawnMillis() { return nextSpawn; }
    public boolean isSpawning() { return spawning; }

    public @Nullable Rift nearest(Location from, double radius) {
        Rift best = null;
        double bestD = radius * radius;
        for (Rift r : rifts.values()) {
            if (r.location().getWorld() != from.getWorld()) continue;
            double d = r.location().distanceSquared(from);
            if (d <= bestD) { bestD = d; best = r; }
        }
        return best;
    }

    public @Nullable String riftIdOf(Entity e) {
        if (!(e instanceof Interaction)) return null;
        return e.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------ settings

    private long intervalMillis() { return (long) (intervalHours * 3_600_000L); }

    public void setIntervalHours(double hours) {
        intervalHours = hours;
        if (lastSpawn > 0L) nextSpawn = lastSpawn + intervalMillis();
        save();
    }

    public void setCount(int n) {
        count = n;
        save();
    }

    // ------------------------------------------------------------ create / remove

    public Rift create(Location loc, Type type) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Rift r = new Rift(id, loc.clone(), type, ThreadLocalRandom.current().nextDouble(Math.PI));
        rifts.put(id, r);
        save();
        ensureEntity(r);
        return r;
    }

    public boolean remove(String id, boolean effects) {
        Rift r = rifts.remove(id);
        if (r == null) return false;
        UUID eid = entities.remove(id);
        if (eid != null) {
            Entity e = Bukkit.getEntity(eid);
            if (e != null) e.remove();
        }
        if (effects && r.location().getWorld() != null) {
            Location c = r.location().clone().add(0, 1.6, 0);
            ParticleUtil.burst(c, ParticleUtil.RIFT_ACCENT, 40);
            c.getWorld().playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.6f);
        }
        save();
        return true;
    }

    // ------------------------------------------------------------ click hitboxes

    private void ensureEntity(Rift r) {
        Location loc = r.location();
        World w = loc.getWorld();
        if (w == null || !w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        UUID known = entities.get(r.id());
        if (known != null) {
            Entity ex = Bukkit.getEntity(known);
            if (ex != null && ex.isValid()) return;
        }
        for (Entity en : w.getNearbyEntities(loc, 1.5, 3.0, 1.5)) {
            if (r.id().equals(riftIdOf(en))) {
                entities.put(r.id(), en.getUniqueId());
                return;
            }
        }
        Interaction i = w.spawn(loc, Interaction.class, ent -> {
            ent.setInteractionWidth(1.8f);
            ent.setInteractionHeight(3.4f);
            ent.setResponsive(true);
            ent.setInvulnerable(true);
            ent.setPersistent(true);
            ent.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, r.id());
        });
        entities.put(r.id(), i.getUniqueId());
    }

    /** A chunk's entities finished loading: drop orphaned/duplicate hitboxes, recreate missing ones. */
    public void onEntitiesLoad(org.bukkit.Chunk chunk, List<Entity> list) {
        for (Entity en : list) {
            String id = riftIdOf(en);
            if (id == null) continue;
            UUID known = entities.get(id);
            boolean duplicate = known != null && !known.equals(en.getUniqueId()) && Bukkit.getEntity(known) != null;
            if (!rifts.containsKey(id) || duplicate) {
                en.remove();
                continue;
            }
            entities.put(id, en.getUniqueId());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Rift r : rifts.values()) {
                Location l = r.location();
                if (l.getWorld() == chunk.getWorld() && (l.getBlockX() >> 4) == chunk.getX() && (l.getBlockZ() >> 4) == chunk.getZ()) {
                    ensureEntity(r);
                }
            }
        });
    }

    // ------------------------------------------------------------ random spawning

    private @Nullable World spawnWorld() {
        String name = plugin.getConfig().getString("rifts.world", "");
        if (name != null && !name.isBlank()) {
            World w = Bukkit.getWorld(name);
            if (w != null) return w;
            plugin.getLogger().warning("rifts.world '" + name + "' not found, using the default overworld.");
        }
        for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == World.Environment.NORMAL) return w;
        return null;
    }

    /** Spawns `n` rifts at random sun-exposed spots inside the world border (replaces old auto rifts). */
    public void spawnCycle(int n) {
        if (spawning) return;
        World w = spawnWorld();
        if (w == null) {
            plugin.getLogger().warning("No overworld found for rift spawning.");
            nextSpawn = System.currentTimeMillis() + intervalMillis();
            return;
        }
        spawning = true;
        if (plugin.getConfig().getBoolean("rifts.replace-on-spawn", true)) {
            for (Rift r : all()) if (r.type() == Type.AUTO) remove(r.id(), true);
        }
        attempt(w, n, 0, new ArrayList<>());
    }

    private void attempt(World w, int wanted, int attempts, List<Location> found) {
        if (found.size() >= wanted || attempts >= MAX_ATTEMPTS) {
            finish(found, wanted);
            return;
        }
        WorldBorder border = w.getWorldBorder();
        double half = Math.max(1.0, border.getSize() / 2.0 - 32.0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = (int) Math.floor(border.getCenter().getX() + r.nextDouble(-half, half));
        int z = (int) Math.floor(border.getCenter().getZ() + r.nextDouble(-half, half));

        w.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err == null) {
                        Location spot = evaluate(w, x, z, found);
                        if (spot != null) found.add(spot);
                    }
                    attempt(w, wanted, attempts + 1, found);
                }));
    }

    /** Valid = solid non-liquid ground, 4 blocks of clear air above it, full sky light, inside the border, spaced out. */
    private @Nullable Location evaluate(World w, int x, int z, List<Location> found) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= w.getMinHeight()) return null;
        Block ground = w.getBlockAt(x, y, z);
        if (!ground.getType().isSolid() || ground.isLiquid()) return null;
        for (int i = 1; i <= 4; i++) {
            Block b = w.getBlockAt(x, y + i, z);
            if (!b.isPassable() || b.isLiquid()) return null;
        }
        if (w.getBlockAt(x, y + 1, z).getLightFromSky() < 15) return null; // not exposed to the sun

        Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
        if (!w.getWorldBorder().isInside(loc)) return null;

        double spacing = plugin.getConfig().getDouble("rifts.min-spacing", 48.0);
        for (Rift r : rifts.values()) {
            if (r.location().getWorld() == w && r.location().distance(loc) < spacing) return null;
        }
        for (Location l : found) if (l.distance(loc) < spacing) return null;
        return loc;
    }

    private void finish(List<Location> found, int wanted) {
        for (Location l : found) create(l, Type.AUTO);
        long now = System.currentTimeMillis();
        lastSpawn = now;
        nextSpawn = now + intervalMillis();
        spawning = false;
        save();
        if (found.size() < wanted) {
            plugin.getLogger().warning("Only found " + found.size() + "/" + wanted + " valid rift spots.");
        }
        if (!found.isEmpty() && plugin.getConfig().getBoolean("rifts.announce", true)) {
            Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The void tears open... new shadow rifts have appeared."));
        }
    }
}
