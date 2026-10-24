package me.limbo.smp.soul;

import me.limbo.smp.LimboPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Crouch + right click with a player soul opens the tracking menu. Souls can never be placed as heads. */
public final class SoulListener implements Listener {
    private final LimboPlugin plugin;

    public SoulListener(LimboPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!plugin.items().isSoul(item)) return;
        e.setCancelled(true); // never place the head
        if (!p.isSneaking()) return;
        String darkness = plugin.items().soulDarkness(item);
        if (darkness != null) plugin.soulMenu().open(p, darkness);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.items().isSoul(e.getItemInHand())) e.setCancelled(true);
    }
}
