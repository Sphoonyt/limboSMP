package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rift GUIs.
 *  - Menu: craft a Null Totem or Shadow Lantern. Ops also see "edit requirements" buttons.
 *  - Editor (ops): deposit the items that should be required, click confirm. Deposited items are
 *    always handed back to the op; they only define the recipe.
 */
public final class RiftMenu implements Listener {
    private static final int SLOT_ORB = 11, SLOT_LANTERN = 15, SLOT_EDIT_ORB = 20, SLOT_EDIT_LANTERN = 24;
    private static final int DEPOSIT_SLOTS = 27;       // editor slots 0-26
    private static final int SLOT_CANCEL = 27, SLOT_CONFIRM = 31;

    private static final class MenuHolder implements InventoryHolder {
        final String riftId;
        Inventory inv;
        MenuHolder(String riftId) { this.riftId = riftId; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class EditorHolder implements InventoryHolder {
        final String recipeId;
        Inventory inv;
        EditorHolder(String recipeId) { this.recipeId = recipeId; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final LimboPlugin plugin;

    public RiftMenu(LimboPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------ opening

    public void open(Player p, String riftId) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) return;
        MenuHolder h = new MenuHolder(riftId);
        Inventory inv = Bukkit.createInventory(h, 27, Msg.parse("<#9b59ff>Shadow Rift"));
        h.inv = inv;
        render(inv, p);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.7f);
    }

    private void openEditor(Player p, String recipeId) {
        EditorHolder h = new EditorHolder(recipeId);
        Inventory inv = Bukkit.createInventory(h, 36, Msg.parse("<gold>Edit requirements: " + recipeTitle(recipeId)));
        h.inv = inv;
        for (int i = DEPOSIT_SLOTS; i < 36; i++) inv.setItem(i, pane());
        inv.setItem(SLOT_CANCEL, button(Material.RED_CONCRETE, "<red>Cancel",
                "<gray>Close without changing anything.", "<gray>Your items are returned."));
        List<String> confirm = new ArrayList<>();
        confirm.add("<gray>Put the required items above, then click.");
        confirm.add("<gray>Your items are returned to you.");
        confirm.add("");
        confirm.add("<gray>Current requirement:");
        for (RiftCrafting.Ingredient i : plugin.crafting().ingredients(recipeId)) {
            confirm.add("<dark_gray>- <white>" + i.amount() + "x " + pretty(i.material()));
        }
        inv.setItem(SLOT_CONFIRM, button(Material.LIME_CONCRETE, "<green>Confirm", confirm.toArray(new String[0])));
        p.openInventory(inv);
    }

    /** Closes any open editor so deposited items are returned (called on plugin disable). */
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder h = p.getOpenInventory().getTopInventory().getHolder();
            if (h instanceof EditorHolder || h instanceof MenuHolder) p.closeInventory();
        }
    }

    // ------------------------------------------------------------ rendering

    private void render(Inventory inv, Player p) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(SLOT_ORB, recipeIcon(p, RiftCrafting.ORB, plugin.items().createOrb(p)));
        inv.setItem(SLOT_LANTERN, recipeIcon(p, RiftCrafting.LANTERN, plugin.items().createLantern()));
        if (p.hasPermission("limbo.admin")) {
            inv.setItem(SLOT_EDIT_ORB, button(Material.COMMAND_BLOCK, "<gold>Edit requirements",
                    "<gray>Admin: change what the", "<gray>Null Totem costs."));
            inv.setItem(SLOT_EDIT_LANTERN, button(Material.COMMAND_BLOCK, "<gold>Edit requirements",
                    "<gray>Admin: change what the", "<gray>Shadow Lantern costs."));
        }
    }

    private ItemStack recipeIcon(Player p, String recipeId, ItemStack base) {
        ItemMeta m = base.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (m.lore() != null) lore.addAll(m.lore());
        lore.add(Component.empty());
        lore.add(line("<gray>Requires:"));
        boolean all = true;
        for (RiftCrafting.Ingredient i : plugin.crafting().ingredients(recipeId)) {
            int have = plugin.crafting().count(p.getInventory(), i.material());
            boolean ok = have >= i.amount();
            all &= ok;
            lore.add(line((ok ? "<green>✔ " : "<red>✘ ") + "<gray>" + i.amount() + "x " + pretty(i.material())
                    + " <dark_gray>(you have " + have + ")"));
        }
        lore.add(Component.empty());
        lore.add(line(all ? "<#9b59ff>Click to craft" : "<red>Missing ingredients"));
        m.lore(lore);
        base.setItemMeta(m);
        return base;
    }

    private ItemStack button(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(line(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(line(s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        it.setItemMeta(m);
        return it;
    }

    private static Component line(String mini) {
        return Msg.parse(mini).decoration(TextDecoration.ITALIC, false);
    }

    private static String pretty(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            if (w.equals("of")) sb.append(w);
            else sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String recipeTitle(String id) {
        return id.equals(RiftCrafting.ORB) ? "Null Totem" : "Shadow Lantern";
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (holder instanceof MenuHolder mh) {
            e.setCancelled(true);
            if (e.getClickedInventory() != top) return;
            if (!plugin.rifts().exists(mh.riftId)) {
                Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
                Msg.send(p, "<gray>The rift has collapsed.");
                return;
            }
            switch (e.getRawSlot()) {
                case SLOT_ORB -> craftAt(p, mh, top, RiftCrafting.ORB);
                case SLOT_LANTERN -> craftAt(p, mh, top, RiftCrafting.LANTERN);
                case SLOT_EDIT_ORB -> adminEdit(p, RiftCrafting.ORB);
                case SLOT_EDIT_LANTERN -> adminEdit(p, RiftCrafting.LANTERN);
                default -> {}
            }
            return;
        }

        if (holder instanceof EditorHolder eh) {
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
            int raw = e.getRawSlot();
            if (raw >= DEPOSIT_SLOTS && raw < 36) { // control row: never movable
                e.setCancelled(true);
                if (!p.hasPermission("limbo.admin")) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
                    return;
                }
                if (raw == SLOT_CANCEL) Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
                else if (raw == SLOT_CONFIRM) confirm(p, eh, top);
            }
            // clicks in deposit slots / player inventory behave normally (shift-click can only reach free deposit slots)
        }
    }

    private void craftAt(Player p, MenuHolder mh, Inventory top, String recipeId) {
        if (plugin.crafting().craft(p, recipeId)) {
            var rift = plugin.rifts().get(mh.riftId);
            if (rift != null) plugin.rifts().onCraft(rift, p, recipeTitle(recipeId));
        }
        render(top, p);
    }

    private void adminEdit(Player p, String recipeId) {
        if (!p.hasPermission("limbo.admin")) return;
        Bukkit.getScheduler().runTask(plugin, () -> openEditor(p, recipeId));
    }

    private void confirm(Player p, EditorHolder eh, Inventory inv) {
        Map<Material, Integer> sum = new EnumMap<>(Material.class);
        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            sum.merge(it.getType(), it.getAmount(), Integer::sum);
        }
        if (sum.isEmpty()) {
            Msg.send(p, "<red>Deposit at least one item first.");
            return;
        }
        List<RiftCrafting.Ingredient> list = new ArrayList<>();
        sum.forEach((m, n) -> list.add(new RiftCrafting.Ingredient(m, n)));
        plugin.crafting().setIngredients(eh.recipeId, list);
        Msg.send(p, "<#9b59ff>" + recipeTitle(eh.recipeId) + " requirements updated.");
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory()); // close event returns the items
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof MenuHolder) {
            e.setCancelled(true);
        } else if (holder instanceof EditorHolder) {
            for (int raw : e.getRawSlots()) {
                if (raw >= DEPOSIT_SLOTS && raw < 36) { e.setCancelled(true); return; }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof EditorHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < DEPOSIT_SLOTS; i++) {
            ItemStack it = e.getInventory().getItem(i);
            if (it == null || it.getType().isAir()) continue;
            plugin.items().give(p, it);
            e.getInventory().setItem(i, null);
        }
    }
}
