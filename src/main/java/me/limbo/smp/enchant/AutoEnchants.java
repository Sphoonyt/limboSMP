package me.limbo.smp.enchant;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Auto enchants: while you HOLD your null totem (main hand or offhand, passives not silenced or fatigued away),
 * your darkness enchants the gear you carry (worn armor, hotbar, offhand). They are removed the moment you stop holding
 * the totem, and always before an item leaves you (drop, death, container, quit), so nothing is ever kept.
 * An enchant you already have at that level or higher is left alone; a lower one is raised and restored afterwards.
 *
 *  Incendiary  fire protection IV on all armor        Vitality    mending on all armor
 *  Entropy     sharpness III on swords and axes       Inertia     swift sneak III on leggings, depth strider III on boots
 *  Reflection  protection III on all armor            Singularity feather falling IV on boots, knockback I on iron swords
 *  Chimera     unbreaking III on all tools and armor
 */
public final class AutoEnchants implements Listener {
    private record Rule(Enchantment enchant, int level, Predicate<Material> applies) {}

    private static final Predicate<Material> ARMOR = m -> Tag.ITEMS_HEAD_ARMOR.isTagged(m) || Tag.ITEMS_CHEST_ARMOR.isTagged(m)
            || Tag.ITEMS_LEG_ARMOR.isTagged(m) || Tag.ITEMS_FOOT_ARMOR.isTagged(m);
    private static final Predicate<Material> TOOL = m -> Tag.ITEMS_SWORDS.isTagged(m) || Tag.ITEMS_AXES.isTagged(m)
            || Tag.ITEMS_PICKAXES.isTagged(m) || Tag.ITEMS_SHOVELS.isTagged(m) || Tag.ITEMS_HOES.isTagged(m);

    private final LimboPlugin plugin;
    private final NamespacedKey tagKey;
    private final Set<UUID> applied = new HashSet<>();

    public AutoEnchants(LimboPlugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "auto_enchants");
    }

    private static List<Rule> rules(String darknessId) {
        return switch (darknessId) {
            case "incendiary" -> List.of(new Rule(Enchantment.FIRE_PROTECTION, 4, ARMOR));
            case "vitality" -> List.of(new Rule(Enchantment.MENDING, 1, ARMOR));
            case "entropy" -> List.of(new Rule(Enchantment.SHARPNESS, 3, m -> Tag.ITEMS_SWORDS.isTagged(m) || Tag.ITEMS_AXES.isTagged(m)));
            case "inertia" -> List.of(new Rule(Enchantment.SWIFT_SNEAK, 3, Tag.ITEMS_LEG_ARMOR::isTagged),
                    new Rule(Enchantment.DEPTH_STRIDER, 3, Tag.ITEMS_FOOT_ARMOR::isTagged));
            case "reflection" -> List.of(new Rule(Enchantment.PROTECTION, 3, ARMOR));
            case "singularity" -> List.of(new Rule(Enchantment.FEATHER_FALLING, 4, Tag.ITEMS_FOOT_ARMOR::isTagged),
                    new Rule(Enchantment.KNOCKBACK, 1, m -> m == Material.IRON_SWORD));
            case "chimera" -> List.of(new Rule(Enchantment.UNBREAKING, 3, m -> ARMOR.test(m) || TOOL.test(m)));
            default -> List.of();
        };
    }

    // ------------------------------------------------------------ per-player tick (called by PassiveManager)

    public void tick(Player p) {
        String id = plugin.data().getDarknessId(p.getUniqueId());
        boolean on = id != null && plugin.fluxActive(p, id) && !rules(id).isEmpty();
        if (!on) {
            if (applied.remove(p.getUniqueId())) stripAll(p);
            return;
        }
        applied.add(p.getUniqueId());
        List<Rule> rules = rules(id);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) apply(inv.getItem(i), id, rules, s -> inv.setItem(slotOf(inv, s), s));
        ItemStack off = inv.getItemInOffHand();
        if (apply(off, id, rules, null)) inv.setItemInOffHand(off);
        ItemStack[] armor = inv.getArmorContents();
        boolean changed = false;
        for (ItemStack a : armor) changed |= apply(a, id, rules, null);
        if (changed) inv.setArmorContents(armor);
    }

    private static int slotOf(PlayerInventory inv, ItemStack s) {
        for (int i = 0; i < 9; i++) if (inv.getItem(i) == s) return i;
        return 0;
    }

    /** @return true if the stack was modified (a modified hotbar stack is written back through `writeBack`). */
    private boolean apply(ItemStack s, String darknessId, List<Rule> rules, java.util.function.Consumer<ItemStack> writeBack) {
        if (s == null || s.getType().isAir()) return false;
        List<Rule> mine = new ArrayList<>();
        for (Rule r : rules) if (r.applies().test(s.getType())) mine.add(r);
        if (mine.isEmpty()) return false;
        ItemMeta m = s.getItemMeta();
        if (m == null) return false;
        String tag = m.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if (tag != null && tag.startsWith(darknessId + "|")) return false; // already done for this darkness
        if (tag != null) { restore(m, tag); }                              // left over from another darkness
        StringBuilder rec = new StringBuilder(darknessId).append('|');
        boolean any = false;
        for (Rule r : mine) {
            int cur = m.getEnchantLevel(r.enchant());
            if (cur >= r.level()) continue;
            rec.append(r.enchant().getKey()).append('=').append(cur).append(';');
            m.addEnchant(r.enchant(), r.level(), true);
            any = true;
        }
        if (!any) return false;
        m.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, rec.toString());
        s.setItemMeta(m);
        if (writeBack != null) writeBack.accept(s);
        return true;
    }

    private void restore(ItemMeta m, String tag) {
        int bar = tag.indexOf('|');
        for (String part : tag.substring(bar + 1).split(";")) {
            int eq = part.lastIndexOf('=');
            if (eq < 0) continue;
            NamespacedKey k = NamespacedKey.fromString(part.substring(0, eq));
            Enchantment e = k == null ? null : Registry.ENCHANTMENT.get(k);
            if (e == null) continue;
            int prev;
            try { prev = Integer.parseInt(part.substring(eq + 1)); } catch (NumberFormatException ex) { continue; }
            if (prev > 0) m.addEnchant(e, prev, true); else m.removeEnchant(e);
        }
        m.getPersistentDataContainer().remove(tagKey);
    }

    /** Takes our enchants off one stack. @return true if it changed. */
    private boolean strip(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return false;
        ItemMeta m = s.getItemMeta();
        String tag = m.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if (tag == null) return false;
        restore(m, tag);
        s.setItemMeta(m);
        return true;
    }

    private void stripAll(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) if (strip(all[i])) inv.setItem(i, all[i]);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) stripAll(p);
        applied.clear();
    }

    // ------------------------------------------------------------ nothing is ever kept

    @EventHandler public void onQuit(PlayerQuitEvent e) { stripAll(e.getPlayer()); applied.remove(e.getPlayer().getUniqueId()); }

    @EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) { stripAll(e.getPlayer()); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        for (ItemStack s : e.getDrops()) strip(s);
        applied.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Item d = e.getItemDrop();
        ItemStack s = d.getItemStack();
        if (strip(s)) d.setItemStack(s);
    }

    @EventHandler public void onClick(InventoryClickEvent e) { sweep(e.getView().getTopInventory()); }
    @EventHandler public void onDrag(InventoryDragEvent e) { sweep(e.getView().getTopInventory()); }

    private void sweep(Inventory top) {
        if (top == null || top.getType() == InventoryType.CRAFTING) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack[] all = top.getContents();
            for (int i = 0; i < all.length; i++) if (strip(all[i])) top.setItem(i, all[i]);
        });
    }
}
