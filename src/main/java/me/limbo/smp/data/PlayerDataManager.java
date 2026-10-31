package me.limbo.smp.data;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** players.yml: darkness assignment, siphon expiry, rerolls and unclaimed null totems (epoch millis, real-time, survives restarts). */
public final class PlayerDataManager {
    private static final class Entry {
        String darkness;
        long siphonUntil;
        int rerolls;
        int totemClaims;
    }

    private final LimboPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private boolean dirty;

    public PlayerDataManager(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Entry e = new Entry();
                e.darkness = plugin.registry().normalize(sec.getString(key + ".darkness")); // legacy ids -> renamed ids
                e.siphonUntil = sec.getLong(key + ".siphon-until", 0L);
                e.rerolls = sec.getInt(key + ".rerolls", 0);
                e.totemClaims = sec.getInt(key + ".totem-claims", 0);
                entries.put(id, e);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Bad UUID in players.yml: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            String base = "players." + me.getKey();
            y.set(base + ".darkness", me.getValue().darkness);
            y.set(base + ".siphon-until", me.getValue().siphonUntil);
            if (me.getValue().rerolls > 0) y.set(base + ".rerolls", me.getValue().rerolls);
            if (me.getValue().totemClaims > 0) y.set(base + ".totem-claims", me.getValue().totemClaims);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save players.yml: " + ex.getMessage());
        }
    }

    public void saveIfDirty() { if (dirty) save(); }

    private Entry entry(UUID id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    // ---- darkness ----

    public String getDarknessId(UUID id) {
        Entry e = entries.get(id);
        return e == null ? null : e.darkness;
    }

    public Darkness getDarkness(Player p) { return getDarkness(p.getUniqueId()); }

    public Darkness getDarkness(UUID id) {
        return plugin.registry().get(getDarknessId(id));
    }

    public void setDarkness(UUID id, String darknessId) {
        entry(id).darkness = darknessId;
        dirty = true;
    }

    /** Completely random pick among registered darknesses. Returns null if none registered. */
    public Darkness assignRandom(UUID id) {
        List<Darkness> all = plugin.registry().all();
        if (all.isEmpty()) return null;
        Darkness d = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        setDarkness(id, d.id());
        return d;
    }

    /** @return true if a new darkness was assigned. */
    public boolean assignIfMissing(Player p) {
        if (getDarknessId(p.getUniqueId()) != null) return false;
        return assignRandom(p.getUniqueId()) != null;
    }

    // ---- siphon ----

    public long siphonRemaining(UUID id) {
        Entry e = entries.get(id);
        if (e == null) return 0;
        return Math.max(0, e.siphonUntil - System.currentTimeMillis());
    }

    public boolean isSiphoned(UUID id) { return siphonRemaining(id) > 0; }

    public void siphon(UUID id, long durationMillis) {
        entry(id).siphonUntil = System.currentTimeMillis() + durationMillis;
        dirty = true;
    }

    public void clearSiphon(UUID id) {
        Entry e = entries.get(id);
        if (e != null) {
            e.siphonUntil = 0;
            dirty = true;
        }
    }

    // ---- rerolls + unclaimed null totems (rewards) ----

    public int getRerolls(UUID id) {
        Entry e = entries.get(id);
        return e == null ? 0 : e.rerolls;
    }

    public void addRerolls(UUID id, int amount) {
        Entry e = entry(id);
        e.rerolls = Math.max(0, e.rerolls + amount);
        dirty = true;
    }

    /** @return true if the player had a reroll to spend. */
    public boolean takeReroll(UUID id) {
        Entry e = entries.get(id);
        if (e == null || e.rerolls <= 0) return false;
        e.rerolls--;
        dirty = true;
        return true;
    }

    public int getTotemClaims(UUID id) {
        Entry e = entries.get(id);
        return e == null ? 0 : e.totemClaims;
    }

    public void addTotemClaims(UUID id, int amount) {
        Entry e = entry(id);
        e.totemClaims = Math.max(0, e.totemClaims + amount);
        dirty = true;
    }
}
