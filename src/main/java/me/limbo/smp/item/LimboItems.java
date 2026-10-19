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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import me.limbo.smp.darkness.Channel;
import me.limbo.smp.util.SmallCaps;
import me.limbo.smp.darkness.Passive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LimboItems {
    private final LimboPlugin plugin;
    private final NamespacedKey orbKey;
    private final NamespacedKey lanternKey;
    private final NamespacedKey pluginKey;
    private final NamespacedKey totemKey;
    private final NamespacedKey totemVersionKey;
    private final NamespacedKey soulKey;
    private final NamespacedKey soulDarknessKey;
    /** Bump when the totem description format changes so existing totems get rewritten. */
    private static final int TOTEM_VERSION = 3;

    public LimboItems(LimboPlugin plugin) {
        this.plugin = plugin;
        this.orbKey = new NamespacedKey(plugin, "null_orb");
        this.lanternKey = new NamespacedKey(plugin, "shadow_lantern");
        this.pluginKey = new NamespacedKey(plugin, "plugin_item");
        this.totemKey = new NamespacedKey(plugin, "totem_darkness");
        this.totemVersionKey = new NamespacedKey(plugin, "totem_version");
        this.soulKey = new NamespacedKey(plugin, "player_soul");
        this.soulDarknessKey = new NamespacedKey(plugin, "soul_darkness");
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
     * The Null Totem for a player: its texture tint and its description follow that player's darkness.
     * Base item is a Heart of the Sea (inert, not placeable); the resource pack swaps in the totem texture
     * when custom_model_data string "null_totem" is present and tints the white part with colors[0].
     * Players without the pack just see a heart of the sea.
     */
    public ItemStack createOrb(Player owner) {
        return buildTotem(plugin.data().getDarkness(owner));
    }

    /** A neutral totem (white tint, no darkness listed). */
    public ItemStack createOrb() {
        return buildTotem(null);
    }

    private ItemStack buildTotem(@Nullable Darkness d) {
        ItemStack it = new ItemStack(Material.HEART_OF_THE_SEA);
        applyTotem(it, d);
        return it;
    }

    /** (Re)writes name, description, tint and tags of a totem for the given darkness. */
    private void applyTotem(ItemStack it, @Nullable Darkness d) {
        ItemMeta m = it.getItemMeta();
        m.displayName(plain(plugin.getConfig().getString("items.null-totem.name", "<#9b59ff>Null Totem")));
        m.lore(totemLore(d));
        CustomModelDataComponent cmd = m.getCustomModelDataComponent();
        cmd.setStrings(List.of("null_totem"));
        cmd.setColors(List.of(d != null ? d.color() : Color.WHITE));
        m.setCustomModelDataComponent(cmd);
        var pdc = m.getPersistentDataContainer();
        pdc.set(orbKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(totemKey, PersistentDataType.STRING, d != null ? d.id() : "none");
        pdc.set(totemVersionKey, PersistentDataType.INTEGER, TOTEM_VERSION);
        markPluginItem(m);
        it.setItemMeta(m);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Totem description: short and vague on purpose. Small caps.
     *   Passives:  - passive (darkness colour) x2, - flux passive (white)
     *   Channels:  - channel names (darkness colour)
     */
    private List<Component> totemLore(@Nullable Darkness d) {
        List<Component> l = new ArrayList<>();
        l.add(plain("<gray>" + SmallCaps.of("A Totem forged from the Limbo.")));
        l.add(plain("<gray>" + SmallCaps.of("Grants you the ability to activate your darkness.")));
        if (d == null) return l;

        String c = "<" + hex(d.color()) + ">";
        l.add(Component.empty());
        l.add(plain("<gray>" + SmallCaps.of("Passives:")));
        for (Passive p : d.passives()) l.add(plain(c + "- " + Msg.esc(SmallCaps.of(p.name()))));
        if (d.fluxPassive() != null) l.add(plain("<white>- " + Msg.esc(SmallCaps.of(d.fluxPassive().name()))));
        l.add(Component.empty());
        l.add(plain("<gray>" + SmallCaps.of("Channels:")));
        for (Channel ch : d.channels()) l.add(plain(c + "- " + Msg.esc(SmallCaps.of(ch.name()))));
        return l;
    }

    /**
     * Keeps every totem in the player's inventory matching the player's CURRENT darkness (texture tint and
     * description). Cheap: it only rewrites a totem whose stored darkness differs. Called every passive tick,
     * so switching darkness (or picking up someone else's totem) updates it within half a second.
     */
    public void syncTotems(Player p) {
        Darkness d = plugin.data().getDarkness(p);
        String want = d != null ? d.id() : "none";
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i <= 40; i++) { // 0-35 storage, 36-39 armor, 40 offhand
            ItemStack s = inv.getItem(i);
            if (!isOrb(s)) continue;
            var pdc = s.getItemMeta().getPersistentDataContainer();
            String cur = pdc.get(totemKey, PersistentDataType.STRING);
            Integer ver = pdc.get(totemVersionKey, PersistentDataType.INTEGER);
            if (want.equals(cur) && ver != null && ver == TOTEM_VERSION) continue;
            applyTotem(s, d);
            inv.setItem(i, s);
        }
    }

    // ------------------------------------------------------------ player souls

    /**
     * A player soul: the victim's head with an enchant glint. The lore names the darkness they had when they
     * died. Used for tracking (crouch + right click) and for the rift teleport.
     */
    public ItemStack createSoul(Player victim, @Nullable Darkness d) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setPlayerProfile(victim.getPlayerProfile());
        m.displayName(plain("<white>" + Msg.esc(victim.getName()) + "'s Soul"));
        String c = d != null ? "<" + hex(d.color()) + ">" : "<gray>";
        m.lore(List.of(plain(c + "Darkness: " + Msg.esc(d != null ? d.displayName() : "None"))));
        m.setEnchantmentGlintOverride(true);
        var pdc = m.getPersistentDataContainer();
        pdc.set(soulKey, PersistentDataType.STRING, victim.getUniqueId().toString());
        pdc.set(soulDarknessKey, PersistentDataType.STRING, d != null ? d.id() : "none");
        markPluginItem(m);
        it.setItemMeta(m);
        return it;
    }

    public boolean isSoul(ItemStack s) {
        return s != null && s.getType() == Material.PLAYER_HEAD && s.hasItemMeta()
                && s.getItemMeta().getPersistentDataContainer().has(soulKey, PersistentDataType.STRING);
    }

    /** The darkness id stored on a soul (the victim's darkness when they died), or null if not a soul. */
    public @Nullable String soulDarkness(ItemStack s) {
        if (!isSoul(s)) return null;
        return s.getItemMeta().getPersistentDataContainer().get(soulDarknessKey, PersistentDataType.STRING);
    }

    public int countSouls(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) if (isSoul(s)) n += s.getAmount();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (isSoul(off)) n += off.getAmount();
        return n;
    }

    /** Removes one soul (of the given darkness, or any if null). */
    public boolean consumeSoul(Player p, @Nullable String darknessId) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i <= 40; i++) {
            if (i >= 36 && i <= 39) continue; // armor
            ItemStack s = inv.getItem(i);
            if (!isSoul(s)) continue;
            if (darknessId != null && !darknessId.equals(soulDarkness(s))) continue;
            if (s.getAmount() <= 1) inv.setItem(i, null);
            else s.setAmount(s.getAmount() - 1);
            return true;
        }
        return false;
    }

    public ItemStack createLantern() {
        ItemStack it = new ItemStack(Material.SOUL_LANTERN);
        ItemMeta m = it.getItemMeta();
        m.displayName(plain(plugin.getConfig().getString("items.shadow-lantern.name", "<#6fe3ff>Shadow Lantern")));
        m.lore(List.of(
                lore("A lantern which uses darkness as its fuel."),
                lore("Consume it to restore your power after it's been"),
                lore("taken from you by another player.")));
        m.setEnchantmentGlintOverride(true);
        applyModel(m, "items.shadow-lantern.item-model");
        m.getPersistentDataContainer().set(lanternKey, PersistentDataType.BYTE, (byte) 1);
        markPluginItem(m);
        it.setItemMeta(m);
        return it;
    }

    /**
     * Tags an item as a Limbo plugin item (they glow when dropped on the ground). Call this on every new
     * custom item, including future mythic ones.
     */
    public void markPluginItem(ItemMeta m) {
        m.getPersistentDataContainer().set(pluginKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isPluginItem(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(pluginKey, PersistentDataType.BYTE)
                || pdc.has(orbKey, PersistentDataType.BYTE)
                || pdc.has(lanternKey, PersistentDataType.BYTE)
                || pdc.has(soulKey, PersistentDataType.STRING);
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
