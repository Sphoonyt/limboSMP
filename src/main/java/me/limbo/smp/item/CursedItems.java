package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The cursed item. Admins build a POOL of items (/limbo cursed pool add|remove|list|clear). /limbo cursed roll picks
 * ONE of them at random to be the cursed item right now (a different one than before when the pool allows it).
 *
 * While it is the cursed item:
 *  - it has an enchant glint whenever it is in your hotbar or offhand (and not anywhere else in your inventory),
 *  - holding it (main hand or offhand) lets you use your FIRST channel at double cooldown. No passives, no flux.
 *
 * The glint is a real item component (enchantment_glint_override) tagged with a marker so we only ever remove our own.
 * It is stripped when the item leaves the hotbar/offhand, is dropped, goes into a container, or stops being cursed.
 * State (pool, current item) lives in cursed.yml and survives restarts. Automatic rotation is off unless
 * cursed-items.rotation-hours is set above 0.
 */
public final class CursedItems implements Listener {
    private final LimboPlugin plugin;
    private final File file;
    private final NamespacedKey glintKey;
    private final List<Material> pool = new ArrayList<>();
    private Material current;
    private long rolledAt;
    private BukkitTask task;
    private BukkitTask glintTask;

    public CursedItems(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cursed.yml");
        this.glintKey = new NamespacedKey(plugin, "cursed_glint");
    }

    // ------------------------------------------------------------ persistence / lifecycle

    public void load() {
        pool.clear();
        current = null;
        rolledAt = 0L;
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        rolledAt = y.getLong("rolled-at", 0L);
        for (String s : y.getStringList("pool")) {
            Material m = Material.matchMaterial(s);
            if (m != null && !pool.contains(m)) pool.add(m);
        }
        String cur = y.getString("current");
        if (cur != null) current = Material.matchMaterial(cur);
        // an older version stored a list of active items; take its first one
        if (current == null) {
            for (String s : y.getStringList("active")) {
                Material m = Material.matchMaterial(s);
                if (m != null) { current = m; break; }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("rolled-at", rolledAt);
        List<String> names = new ArrayList<>();
        for (Material m : pool) names.add(m.name());
        y.set("pool", names);
        y.set("current", current == null ? null : current.name());
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save cursed.yml: " + ex.getMessage());
        }
    }

    public void start() {
        // optional automatic rotation, checked once a minute
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long rot = rotationMillis();
            if (!enabled() || rot <= 0 || pool.isEmpty()) return;
            if (System.currentTimeMillis() - rolledAt >= rot) roll(true);
        }, 200L, 1200L);
        glintTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) syncGlint(p);
        }, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        if (glintTask != null) glintTask.cancel();
        task = null;
        glintTask = null;
        for (Player p : Bukkit.getOnlinePlayers()) stripAll(p);
        save();
    }

    // ------------------------------------------------------------ settings

    public boolean enabled() { return plugin.getConfig().getBoolean("cursed-items.enabled", true); }

    public void setEnabled(boolean on) {
        plugin.getConfig().set("cursed-items.enabled", on);
        plugin.saveConfig();
        if (!on) for (Player p : Bukkit.getOnlinePlayers()) stripAll(p);
    }

    /** Hours between automatic re-rolls. 0 (default) = only when an admin rolls. */
    private long rotationMillis() {
        return (long) (plugin.getConfig().getDouble("cursed-items.rotation-hours", 0.0) * 3_600_000L);
    }

    // ------------------------------------------------------------ the pool (admins)

    public List<Material> pool() { return Collections.unmodifiableList(new ArrayList<>(pool)); }

    /** @return false if it was already in the pool or can't be an item. */
    public boolean addToPool(Material m) {
        if (m == null || !m.isItem() || m.isAir() || pool.contains(m)) return false;
        pool.add(m);
        save();
        return true;
    }

    /** @return false if it wasn't in the pool. Removing the current cursed item un-curses it. */
    public boolean removeFromPool(Material m) {
        if (!pool.remove(m)) return false;
        if (m == current) {
            current = null;
            stripEveryone();
        }
        save();
        return true;
    }

    public void clearPool() {
        pool.clear();
        current = null;
        stripEveryone();
        save();
    }

    // ------------------------------------------------------------ rolling

    /**
     * Picks one item from the pool at random to be the cursed item (not the same one as before unless it's the only
     * one). Returns it, or null if the pool is empty.
     */
    public Material roll(boolean announce) {
        if (pool.isEmpty()) return null;
        List<Material> options = new ArrayList<>(pool);
        if (options.size() > 1) options.remove(current);
        return setCurrent(options.get(ThreadLocalRandom.current().nextInt(options.size())), announce);
    }

    /** Makes a specific item the cursed item (it is added to the pool if it isn't there). */
    public Material setCurrent(Material m, boolean announce) {
        if (m == null || !m.isItem() || m.isAir()) return null;
        if (!pool.contains(m)) pool.add(m);
        Material old = current;
        current = m;
        rolledAt = System.currentTimeMillis();
        save();
        if (old != m) stripEveryone();
        for (Player p : Bukkit.getOnlinePlayers()) syncGlint(p);
        if (announce) {
            Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The cursed item has changed: <white>" + Msg.esc(prettyName(m))));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.6f);
        }
        return m;
    }

    // ------------------------------------------------------------ queries

    public Material current() { return enabled() ? current : null; }

    /** Kept as a list for callers that print "the cursed items": zero or one entry. */
    public List<Material> active() {
        Material c = current();
        return c == null ? List.of() : List.of(c);
    }

    public boolean isActive(ItemStack s) {
        return enabled() && current != null && s != null && s.getType() == current;
    }

    public boolean isHoldingActive(Player p) {
        return isActive(p.getInventory().getItemInMainHand()) || isActive(p.getInventory().getItemInOffHand());
    }

    public long millisUntilRotation() {
        long rot = rotationMillis();
        return rot <= 0 ? -1L : Math.max(0L, rolledAt + rot - System.currentTimeMillis());
    }

    public static String prettyName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public String describe() {
        Material c = current();
        return c == null ? "none" : prettyName(c);
    }

    // ------------------------------------------------------------ the glint (hotbar + offhand only)

    /** "ᴄᴜʀsᴇᴅ <item name>": the item's own name, prefixed, while it is the cursed item. */
    private static net.kyori.adventure.text.Component cursedName(Material m) {
        return net.kyori.adventure.text.Component.text(me.limbo.smp.util.SmallCaps.of("cursed") + " ", net.kyori.adventure.text.format.TextColor.color(0x9b59ff))
                .append(net.kyori.adventure.text.Component.translatable(m.translationKey(), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /**
     * Sets what we put on this stack: bit 1 = enchant glint, bit 2 = the "cursed" name. Tagged so we only ever
     * remove our own. @return true if the stack changed.
     */
    private boolean mark(ItemStack s, boolean glint, boolean named) {
        if (s == null || s.getType().isAir()) return false;
        ItemMeta m = s.getItemMeta();
        if (m == null) return false;
        Byte cur = m.getPersistentDataContainer().get(glintKey, PersistentDataType.BYTE);
        int have = cur == null ? 0 : cur;
        int want = (glint ? 1 : 0) | (named ? 2 : 0);
        if (have == want) return false;
        m.setEnchantmentGlintOverride(glint ? Boolean.TRUE : null);
        m.itemName(named ? cursedName(s.getType()) : null);
        if (want == 0) m.getPersistentDataContainer().remove(glintKey);
        else m.getPersistentDataContainer().set(glintKey, PersistentDataType.BYTE, (byte) want);
        s.setItemMeta(m);
        return true;
    }

    /** Cursed item: named everywhere in your inventory, glowing only in the hotbar and offhand. Cheap: type checks only. */
    public void syncGlint(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean on = enabled() && current != null;
        if (!on) return;
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack s = storage[i];
            if (s == null || s.getType() != current) continue;
            if (mark(s, i < 9, true)) inv.setItem(i, s);
        }
        ItemStack off = inv.getItemInOffHand();
        if (off.getType() == current && mark(off, true, true)) inv.setItemInOffHand(off);
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (ItemStack a : armor) if (a != null && a.getType() == current) changed |= mark(a, false, true);
        if (changed) inv.setArmorContents(armor);
    }

    /** Removes our glint from EVERYTHING the player carries (item changed, cursed items turned off, quit...). */
    public void stripAll(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) {
            ItemStack s = all[i];
            if (s != null && !s.getType().isAir() && s.hasItemMeta() && mark(s, false, false)) inv.setItem(i, s);
        }
    }

    private void stripEveryone() {
        for (Player p : Bukkit.getOnlinePlayers()) stripAll(p);
    }

    private void stripContainer(Inventory inv) {
        if (inv == null) return;
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) {
            ItemStack s = all[i];
            if (s != null && !s.getType().isAir() && s.hasItemMeta() && mark(s, false, false)) inv.setItem(i, s);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        stripAll(e.getPlayer()); // anything left over from a crash or a changed pool
        syncGlint(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Item drop = e.getItemDrop();
        ItemStack s = drop.getItemStack();
        if (s.hasItemMeta() && mark(s, false, false)) drop.setItemStack(s);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        for (ItemStack s : e.getDrops()) if (s != null && s.hasItemMeta()) mark(s, false, false);
    }

    /** Anything moved into a chest, shulker, hopper-fed block... loses the glint on the next tick. */
    @EventHandler
    public void onClick(InventoryClickEvent e) { sweepOpenContainer(e.getView().getTopInventory()); }

    @EventHandler
    public void onDrag(InventoryDragEvent e) { sweepOpenContainer(e.getView().getTopInventory()); }

    private void sweepOpenContainer(Inventory top) {
        if (top == null || top.getType() == InventoryType.CRAFTING) return;
        Bukkit.getScheduler().runTask(plugin, () -> stripContainer(top));
    }
}
