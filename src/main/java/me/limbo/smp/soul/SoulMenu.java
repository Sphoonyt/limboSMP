package me.limbo.smp.soul;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lists every online player with the same darkness as the soul. Left click one to start tracking them. */
public final class SoulMenu implements Listener {
    private static final class Holder implements InventoryHolder {
        final String darknessId;
        final Map<Integer, UUID> targets = new HashMap<>();
        Inventory inv;
        Holder(String darknessId) { this.darknessId = darknessId; }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Picker shown when the player carries souls of more than one darkness. */
    private static final class Picker implements InventoryHolder {
        final Map<Integer, String> darknesses = new HashMap<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final LimboPlugin plugin;

    public SoulMenu(LimboPlugin plugin) { this.plugin = plugin; }

    private static Component line(String mini) {
        return Msg.parse(mini).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Soul tracking is started from a rift's menu. With souls of one darkness it goes straight to the target list,
     * with several it first asks which soul to use.
     */
    public void openFromRift(Player p) {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.List<ItemStack> all = new java.util.ArrayList<>(java.util.Arrays.asList(p.getInventory().getStorageContents()));
        all.add(p.getInventory().getItemInOffHand());
        for (ItemStack it : all) {
            if (!plugin.items().isSoul(it)) continue;
            String id = plugin.items().soulDarkness(it);
            if (id != null) counts.merge(id, it.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            Msg.send(p, "<red>You need a player soul to track someone.");
            return;
        }
        if (counts.size() == 1) { open(p, counts.keySet().iterator().next()); return; }

        Picker h = new Picker();
        Inventory inv = Bukkit.createInventory(h, 9, Msg.parse("<#9b59ff>Soul Tracking <gray>- choose a soul"));
        h.inv = inv;
        int slot = 0;
        for (Map.Entry<String, Integer> en : counts.entrySet()) {
            if (slot >= 9) break;
            Darkness d = plugin.registry().get(en.getKey());
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta m = it.getItemMeta();
            m.displayName(line("<white>" + Msg.esc(d != null ? d.displayName() : "Unknown") + " soul <gray>x" + en.getValue()));
            m.lore(List.of(line("<gray>Click to pick who to track")));
            m.setEnchantmentGlintOverride(true);
            it.setItemMeta(m);
            inv.setItem(slot, it);
            h.darknesses.put(slot++, en.getKey());
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.7f);
    }

    public void open(Player p, String darknessId) {
        Darkness d = plugin.registry().get(darknessId);
        String name = d != null ? d.displayName() : "Unknown";

        List<Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(o -> !o.getUniqueId().equals(p.getUniqueId()))
                .filter(o -> darknessId.equals(plugin.data().getDarknessId(o.getUniqueId())))
                .limit(54)
                .map(o -> (Player) o)
                .toList();

        Holder h = new Holder(darknessId);
        int size = Math.max(9, ((targets.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(h, size, Msg.parse("<#9b59ff>Player Soul <gray>- " + Msg.esc(name)));
        h.inv = inv;

        if (targets.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta m = none.getItemMeta();
            m.displayName(line("<red>Nobody online has this darkness"));
            none.setItemMeta(m);
            inv.setItem(4, none);
        }
        for (int i = 0; i < targets.size(); i++) {
            Player t = targets.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setPlayerProfile(t.getPlayerProfile());
            m.displayName(line("<white>" + Msg.esc(t.getName())));
            m.lore(List.of(line("<gray>Left click to track")));
            head.setItemMeta(m);
            inv.setItem(i, head);
            h.targets.put(i, t.getUniqueId());
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.7f);
    }

    @EventHandler
    public void onPickerClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Picker pk)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top || !(e.getWhoClicked() instanceof Player p)) return;
        String id = pk.darknesses.get(e.getRawSlot());
        if (id != null) Bukkit.getScheduler().runTask(plugin, () -> open(p, id));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top || !e.getClick().isLeftClick()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        UUID targetId = h.targets.get(e.getRawSlot());
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            Msg.send(p, "<red>That player is no longer online.");
            return;
        }
        boolean consume = plugin.getConfig().getBoolean("souls.tracking-consumes-soul", true);
        if (consume && !plugin.items().consumeSoul(p, h.darknessId)) {
            Msg.send(p, "<red>You need a player soul with this darkness.");
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            return;
        }
        Darkness d = plugin.registry().get(h.darknessId);
        Color color = d != null ? d.color() : Color.PURPLE;
        plugin.soulTracker().start(p, target, color);
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        var ho = e.getView().getTopInventory().getHolder();
        if (ho instanceof Holder || ho instanceof Picker) e.setCancelled(true);
    }
}
