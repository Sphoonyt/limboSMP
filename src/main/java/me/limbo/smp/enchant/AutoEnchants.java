package me.limbo.smp.enchant;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The auto-enchant passives were removed. This only exists to clean up after them: any item still carrying one of the
 * enchants they added (tagged auto_enchants) gets it taken off again when its owner joins, so nothing stays enchanted.
 * It can be deleted once every player has joined once since the update.
 */
public final class AutoEnchants implements Listener {
    private final NamespacedKey tagKey;

    public AutoEnchants(LimboPlugin plugin) {
        this.tagKey = new NamespacedKey(plugin, "auto_enchants");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { cleanse(e.getPlayer()); }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) cleanse(p);
    }

    private void cleanse(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] all = inv.getContents();
        for (int i = 0; i < all.length; i++) if (strip(all[i])) inv.setItem(i, all[i]);
    }

    private boolean strip(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return false;
        ItemMeta m = s.getItemMeta();
        String tag = m.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        if (tag == null) return false;
        int bar = tag.indexOf('|');
        for (String part : tag.substring(bar + 1).split(";")) {
            int eq = part.lastIndexOf('=');
            if (eq < 0) continue;
            NamespacedKey k = NamespacedKey.fromString(part.substring(0, eq));
            Enchantment en = k == null ? null : Registry.ENCHANTMENT.get(k);
            if (en == null) continue;
            int prev;
            try { prev = Integer.parseInt(part.substring(eq + 1)); } catch (NumberFormatException ex) { continue; }
            if (prev > 0) m.addEnchant(en, prev, true); else m.removeEnchant(en);
        }
        m.getPersistentDataContainer().remove(tagKey);
        s.setItemMeta(m);
        return true;
    }
}
