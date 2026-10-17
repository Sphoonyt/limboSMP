package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import me.limbo.smp.darkness.Darkness;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class LimboItems {
    private final LimboPlugin plugin;
    private final NamespacedKey orbKey;
    private final NamespacedKey lanternKey;

    public LimboItems(LimboPlugin plugin) {
        this.plugin = plugin;
        this.orbKey = new NamespacedKey(plugin, "null_orb");
        this.lanternKey = new NamespacedKey(plugin, "shadow_lantern");
    }

    private Component plain(String mini) {
        return Msg.parse(mini).decoration(TextDecoration.ITALIC, false);
    }

    private Component lore(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private void applyModel(ItemMeta m, String path) {
        String s = plugin.getConfig().getString(path, "");
        if (s != null && !s.isBlank()) {
            NamespacedKey k = NamespacedKey.fromString(s);
            if (k != null) m.setItemModel(k);
        }
    }

    /**
     * The Null Totem. Base item is a Heart of the Sea (inert, not placeable); the resource pack swaps in the
     * totem texture when custom_model_data string "null_totem" is present, and the white part of the texture
     * is tinted with colors[0]. Players without the pack just see a heart of the sea.
     */
    public ItemStack createOrb(Color tint) {
        ItemStack it = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta m = it.getItemMeta();
        m.displayName(plain(plugin.getConfig().getString("items.null-totem.name", "<#9b59ff>Null Totem")));
        m.lore(List.of(
                lore("A shard of Limbo, bound to no one."),
                lore("Keep it in your inventory to awaken your passives."),
                lore("Hold it to feel your flux.")));
        CustomModelDataComponent cmd = m.getCustomModelDataComponent();
        cmd.setStrings(List.of("null_totem"));
        cmd.setColors(List.of(tint));
        m.setCustomModelDataComponent(cmd);
        m.getPersistentDataContainer().set(orbKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** A totem tinted with this player's darkness colour (white if they have none). */
    public ItemStack createOrb(Player owner) {
        Darkness d = plugin.data().getDarkness(owner);
        return createOrb(d != null ? d.color() : Color.WHITE);
    }

    public ItemStack createOrb() { return createOrb(Color.WHITE); }

    public ItemStack createLantern() {
        ItemStack it = new ItemStack(Material.SOUL_LANTERN);
        ItemMeta m = it.getItemMeta();
        m.displayName(plain(plugin.getConfig().getString("items.shadow-lantern.name", "<#6fe3ff>Shadow Lantern")));
        m.lore(List.of(
                lore("Right click to reclaim a siphoned darkness.")));
        m.setEnchantmentGlintOverride(true);
        applyModel(m, "items.shadow-lantern.item-model");
        m.getPersistentDataContainer().set(lanternKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isOrb(ItemStack s) {
        return s != null && s.getType() == Material.HEART_OF_THE_SEA && s.hasItemMeta()
                && s.getItemMeta().getPersistentDataContainer().has(orbKey, PersistentDataType.BYTE);
    }

    public boolean isLantern(ItemStack s) {
        return s != null && s.getType() == Material.SOUL_LANTERN && s.hasItemMeta()
                && s.getItemMeta().getPersistentDataContainer().has(lanternKey, PersistentDataType.BYTE);
    }

    /** Main inventory (hotbar + storage) and offhand. Not armor, ender chests or shulkers. */
    public boolean hasOrbInInventory(Player p) {
        for (ItemStack s : p.getInventory().getStorageContents()) if (isOrb(s)) return true;
        return isOrb(p.getInventory().getItemInOffHand());
    }

    public boolean isHoldingOrb(Player p) {
        return isOrb(p.getInventory().getItemInMainHand()) || isOrb(p.getInventory().getItemInOffHand());
    }

    public void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }
}
