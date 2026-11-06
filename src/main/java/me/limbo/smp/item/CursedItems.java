package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Cursed items. A pool of items that do nothing but exist for crafting (can't be placed, used or worn). Every day
 * (cursed-items.rotation-hours) a different set of them is "active". Holding an active cursed item (main hand or
 * offhand) acts like a stand-in for the null totem:
 *  - it runs your darkness's passives (and flux passive, see cursed-items.flux),
 *  - and lets you use your FIRST channel, at double its cooldown (see InputListener / InputDispatcher).
 * State lives in cursed.yml and survives restarts.
 */
public final class CursedItems implements Listener {
    /** Used when cursed-items.pool is empty. All of these are inert crafting materials. */
    private static final List<String> DEFAULT_POOL = List.of(
            "IRON_NUGGET", "GOLD_NUGGET", "FLINT", "LEATHER", "RABBIT_HIDE", "PHANTOM_MEMBRANE",
            "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "BRICK", "NETHER_BRICK", "CLAY_BALL", "COPPER_INGOT",
            "AMETHYST_SHARD", "SHULKER_SHELL", "ARMADILLO_SCUTE", "BREEZE_ROD", "ECHO_SHARD", "NETHERITE_SCRAP",
            "SLIME_BALL", "GUNPOWDER");

    private final LimboPlugin plugin;
    private final File file;
    private final List<Material> active = new ArrayList<>();
    private long rolledAt;
    private BukkitTask task;

    public CursedItems(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cursed.yml");
    }

    // ------------------------------------------------------------ persistence / lifecycle

    public void load() {
        active.clear();
        rolledAt = 0L;
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        rolledAt = y.getLong("rolled-at", 0L);
        for (String s : y.getStringList("active")) {
            Material m = Material.matchMaterial(s);
            if (m != null) active.add(m);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("rolled-at", rolledAt);
        List<String> names = new ArrayList<>();
        for (Material m : active) names.add(m.name());
        y.set("active", names);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save cursed.yml: " + ex.getMessage());
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled() || active.isEmpty()) return;
            if (System.currentTimeMillis() - rolledAt >= rotationMillis()) roll(true);
        }, 200L, 1200L); // checked once a minute
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        save();
    }

    // ------------------------------------------------------------ settings

    public boolean enabled() { return plugin.getConfig().getBoolean("cursed-items.enabled", true); }

    public void setEnabled(boolean on) {
        plugin.getConfig().set("cursed-items.enabled", on);
        plugin.saveConfig();
    }

    private long rotationMillis() {
        return (long) (plugin.getConfig().getDouble("cursed-items.rotation-hours", 24.0) * 3_600_000L);
    }

    private int activeCount() { return Math.max(1, plugin.getConfig().getInt("cursed-items.active-count", 2)); }

    public List<Material> pool() {
        List<String> names = plugin.getConfig().getStringList("cursed-items.pool");
        if (names.isEmpty()) names = DEFAULT_POOL;
        List<Material> out = new ArrayList<>();
        for (String s : names) {
            Material m = Material.matchMaterial(s);
            if (m == null || !m.isItem() || m.isAir()) {
                plugin.getLogger().warning("cursed-items.pool: ignoring unknown item '" + s + "'");
                continue;
            }
            if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------ rolling

    /** Picks a new active set that avoids last time's items where the pool allows it. */
    public List<Material> roll(boolean announce) {
        List<Material> pool = pool();
        if (pool.isEmpty()) {
            plugin.getLogger().warning("Cursed items: the pool is empty.");
            return active();
        }
        int n = Math.min(activeCount(), pool.size());
        List<Material> fresh = new ArrayList<>(pool);
        fresh.removeAll(active);
        if (fresh.size() < n) { // pool too small to avoid repeats entirely: top up from the old ones
            List<Material> old = new ArrayList<>(pool);
            old.removeAll(fresh);
            Collections.shuffle(old);
            while (fresh.size() < n && !old.isEmpty()) fresh.add(old.remove(0));
        }
        Collections.shuffle(fresh);
        active.clear();
        active.addAll(fresh.subList(0, n));
        rolledAt = System.currentTimeMillis();
        save();
        if (announce) {
            Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The cursed items have changed: <white>" + Msg.esc(describe())));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.6f);
        }
        return active();
    }

    // ------------------------------------------------------------ queries

    public List<Material> active() { return Collections.unmodifiableList(new ArrayList<>(active)); }

    public boolean isActive(ItemStack s) {
        return enabled() && s != null && !s.getType().isAir() && active.contains(s.getType());
    }

    public boolean isHoldingActive(Player p) {
        return isActive(p.getInventory().getItemInMainHand()) || isActive(p.getInventory().getItemInOffHand());
    }

    public long millisUntilRotation() {
        return Math.max(0L, rolledAt + rotationMillis() - System.currentTimeMillis());
    }

    public static String prettyName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public String describe() {
        if (active.isEmpty()) return "none";
        List<String> names = new ArrayList<>();
        for (Material m : active) names.add(prettyName(m));
        return String.join(", ", names);
    }

    // ------------------------------------------------------------ feedback when one is picked up in hand

    private void notifyHeld(Player p) {
        Darkness d = plugin.data().getDarkness(p);
        if (d == null) return;
        p.sendActionBar(Msg.parse("<dark_purple>A cursed item. <gray>Your <white>" + Msg.esc(d.displayName()) + "<gray> answers. <dark_gray>(first ability, double cooldown)"));
        p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 0.6f);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        ItemStack now = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (isActive(now)) notifyHeld(e.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isActive(e.getMainHandItem()) || isActive(e.getOffHandItem())) notifyHeld(e.getPlayer());
    }
}
