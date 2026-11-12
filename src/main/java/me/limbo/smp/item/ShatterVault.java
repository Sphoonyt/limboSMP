package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Reality Shatter vault: 9 slots (a dispenser) that only open while you have Reality Shatter up, by crouching and
 * right clicking your null totem. Contents are saved in vault.yml, drop where you die (unless keepInventory) and can't
 * be reached any other way.
 */
public final class ShatterVault implements Listener {
    /** Marks our inventory so we recognise it when it closes. */
    public static final class Holder implements InventoryHolder {
        final UUID owner;
        Inventory inv;
        Holder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final LimboPlugin plugin;
    private final File file;
    private final Map<UUID, ItemStack[]> stored = new HashMap<>();

    public ShatterVault(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vault.yml");
    }

    public void load() {
        stored.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                String b64 = y.getString(k);
                if (b64 == null) continue;
                stored.put(UUID.fromString(k), ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(b64)));
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not read vault of " + k + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack[]> e : stored.entrySet()) {
            boolean any = false;
            for (ItemStack s : e.getValue()) if (s != null && !s.getType().isAir()) any = true;
            if (any) y.set(e.getKey().toString(), Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(e.getValue())));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save vault.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory();
        save();
    }

    /** Opens the vault. The caller has already checked that Reality Shatter is up. */
    public void open(Player p) {
        Holder h = new Holder(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(h, InventoryType.DISPENSER, Component.text("Shattered Reality", TextColor.color(0xa5cdff)));
        h.inv = inv;
        ItemStack[] items = stored.get(p.getUniqueId());
        if (items != null) inv.setContents(items);
        p.openInventory(inv);
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    /** Closes the vault if this player has it open (Reality Shatter ended). */
    public void closeFor(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        stored.put(h.owner, e.getInventory().getContents());
        save();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        ItemStack[] items = stored.remove(e.getEntity().getUniqueId());
        if (items == null) return;
        if (e.getKeepInventory()) { stored.put(e.getEntity().getUniqueId(), items); return; }
        for (ItemStack s : items) if (s != null && !s.getType().isAir()) e.getDrops().add(s);
        save();
    }
}
