package me.limbo.smp.progress;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The progression system. Features are locked or unlocked by commands (/limbo unlock|lock|unlocks), so the whole
 * server can be gated without any external plugin. Events flip these flags when they finish.
 *
 * Locked by default on a fresh install (config: unlocks.lock-by-default). State lives in unlocks.yml.
 *  - NETHER:    portals into the Nether are cancelled.
 *  - VILLAGERS: villagers can't be traded with.
 *  - RIFTS:     shadow rifts don't spawn on their own, and Entropy only gets the early (half) strength.
 *  - ENCHANTS:  enchants are capped (events.progression.enchant-cap, 0 = no cap) at the table and the anvil.
 */
public final class Unlocks implements Listener {
    public enum Feature {
        NETHER("nether", "The Nether"),
        VILLAGERS("villagers", "Villager trading"),
        RIFTS("rifts", "Shadow rifts"),
        ENCHANTS("enchants", "Max enchant levels");

        private final String id;
        private final String label;

        Feature(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() { return id; }
        public String label() { return label; }

        public static @Nullable Feature parse(String s) {
            if (s == null) return null;
            String l = s.toLowerCase(Locale.ROOT);
            if (l.equals("villager") || l.equals("trading")) return VILLAGERS;
            if (l.equals("rift")) return RIFTS;
            for (Feature f : values()) if (f.id.equals(l)) return f;
            return null;
        }
    }

    private final LimboPlugin plugin;
    private final File file;
    private final Set<Feature> unlocked = EnumSet.noneOf(Feature.class);

    public Unlocks(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "unlocks.yml");
    }

    // ------------------------------------------------------------ state

    public void load() {
        unlocked.clear();
        if (!file.exists()) {
            if (!plugin.getConfig().getBoolean("unlocks.lock-by-default", true)) unlocked.addAll(EnumSet.allOf(Feature.class));
            save();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("unlocked")) {
            Feature f = Feature.parse(s);
            if (f != null) unlocked.add(f);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (Feature f : unlocked) ids.add(f.id());
        y.set("unlocked", ids);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save unlocks.yml: " + ex.getMessage());
        }
    }

    public boolean isUnlocked(Feature f) { return unlocked.contains(f); }

    public void unlock(Feature f) {
        if (unlocked.add(f)) save();
    }

    public void lock(Feature f) {
        if (unlocked.remove(f)) save();
    }

    // ------------------------------------------------------------ Nether

    private boolean netherSealed(@Nullable Location to) {
        if (isUnlocked(Feature.NETHER)) return false;
        World w = to == null ? null : to.getWorld();
        return w != null && w.getEnvironment() == World.Environment.NETHER;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!netherSealed(e.getTo())) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "<red>The Nether is sealed.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (netherSealed(e.getTo())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ villagers

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerClick(PlayerInteractEntityEvent e) {
        if (isUnlocked(Feature.VILLAGERS) || !(e.getRightClicked() instanceof Villager)) return;
        e.setCancelled(true);
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            e.getPlayer().sendActionBar(Msg.parse("<gray>The villager turns away from you."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent e) {
        if (isUnlocked(Feature.VILLAGERS)) return;
        if (e.getInventory() instanceof MerchantInventory mi && mi.getMerchant() instanceof Villager) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ enchant cap

    private int enchantCap() {
        return isUnlocked(Feature.ENCHANTS) ? 0 : plugin.getConfig().getInt("events.progression.enchant-cap", 3);
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

    // ------------------------------------------------------------ commands: /limbo unlock|lock|unlocks

    public void list(org.bukkit.command.CommandSender s) {
        for (Feature f : Feature.values()) {
            Msg.send(s, "<white>" + f.id() + " <dark_gray>- <gray>" + f.label() + ": "
                    + (isUnlocked(f) ? "<green>unlocked" : "<red>locked"));
        }
    }

    public void command(org.bukkit.command.CommandSender s, boolean unlockIt, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "<gray>/limbo " + (unlockIt ? "unlock" : "lock") + " <" + featureIds() + ">");
            return;
        }
        Feature f = Feature.parse(a[1]);
        if (f == null) {
            Msg.send(s, "<red>Unknown feature. Valid: " + featureIds());
            return;
        }
        if (unlockIt) unlock(f); else lock(f);
        Msg.send(s, "<white>" + f.label() + " <gray>is now " + (unlockIt ? "<green>unlocked" : "<red>locked") + "<gray>.");
    }

    public static String featureIds() {
        StringBuilder sb = new StringBuilder();
        for (Feature f : Feature.values()) {
            if (sb.length() > 0) sb.append("|");
            sb.append(f.id());
        }
        return sb.toString();
    }

    /** Players don't need to know the details, but it's handy for debugging. */
    public void describeTo(Player p) { list(p); }
}
