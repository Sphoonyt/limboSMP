package me.limbo.smp.trust;

import me.limbo.smp.LimboPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One-way trust: if A trusts B, A's abilities never affect B. (B must /trust A for the reverse.)
 * Enforced via Targeting.canHarm().
 */
public final class TrustManager {
    private final LimboPlugin plugin;
    private final File file;
    private final Map<UUID, Set<UUID>> trusts = new HashMap<>();

    public TrustManager(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trust.yml");
    }

    public void load() {
        trusts.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("trust");
        if (sec == null) return;
        for (String owner : sec.getKeys(false)) {
            try {
                Set<UUID> set = new HashSet<>();
                for (String s : sec.getStringList(owner)) set.add(UUID.fromString(s));
                trusts.put(UUID.fromString(owner), set);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Bad UUID in trust.yml under " + owner);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : trusts.entrySet()) {
            y.set("trust." + e.getKey(), e.getValue().stream().map(UUID::toString).toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save trust.yml: " + ex.getMessage());
        }
    }

    public boolean isTrusting(UUID owner, UUID other) {
        Set<UUID> s = trusts.get(owner);
        return s != null && s.contains(other);
    }

    public boolean trust(UUID owner, UUID other) {
        boolean added = trusts.computeIfAbsent(owner, k -> new HashSet<>()).add(other);
        if (added) save();
        return added;
    }

    public boolean untrust(UUID owner, UUID other) {
        Set<UUID> s = trusts.get(owner);
        boolean removed = s != null && s.remove(other);
        if (removed) save();
        return removed;
    }

    public Set<UUID> trustedBy(UUID owner) {
        return trusts.getOrDefault(owner, Set.of());
    }
}
