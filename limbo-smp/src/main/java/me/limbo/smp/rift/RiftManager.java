package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shadow rifts: ops-placed, unbreakable (except creative) blocks stored in rifts.yml. */
public final class RiftManager {
    private final LimboPlugin plugin;
    private final File file;
    private final Map<String, Location> rifts = new LinkedHashMap<>();
    private BukkitTask ambience;

    public RiftManager(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rifts.yml");
    }

    private static String key(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    public void load() {
        rifts.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("rifts")) {
            String[] p = s.split(",");
            if (p.length != 4) continue;
            World w = Bukkit.getWorld(p[0]);
            if (w == null) {
                plugin.getLogger().warning("Rift world not loaded, skipping: " + s);
                continue;
            }
            try {
                rifts.put(s, new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Bad rift entry: " + s);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("rifts", new ArrayList<>(rifts.keySet()));
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save rifts.yml: " + ex.getMessage());
        }
    }

    public boolean isRift(Block b) { return b != null && rifts.containsKey(key(b)); }

    public Material riftMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("rifts.block", "CRYING_OBSIDIAN"));
        return (m != null && m.isBlock()) ? m : Material.CRYING_OBSIDIAN;
    }

    public void create(Block b) {
        b.setType(riftMaterial());
        rifts.put(key(b), b.getLocation());
        save();
    }

    public boolean remove(Block b, boolean clearBlock) {
        boolean removed = rifts.remove(key(b)) != null;
        if (removed) {
            if (clearBlock) b.setType(Material.AIR);
            save();
        }
        return removed;
    }

    public List<String> list() { return new ArrayList<>(rifts.keySet()); }

    public void startAmbience() {
        ambience = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double range = plugin.getConfig().getDouble("rifts.particle-range", 32);
            for (Location l : rifts.values()) {
                World w = l.getWorld();
                if (w == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
                Location c = l.clone().add(0.5, 1.1, 0.5);
                if (w.getNearbyPlayers(c, range).isEmpty()) continue;
                ParticleUtil.riftAmbience(c);
            }
        }, 20L, 10L);
    }

    public void shutdown() {
        if (ambience != null) ambience.cancel();
    }
}
