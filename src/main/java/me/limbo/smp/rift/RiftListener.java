package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Clicking a rift's hitbox opens the rift menu; keeps hitboxes in sync with loaded chunks. */
public final class RiftListener implements Listener {
    private final LimboPlugin plugin;

    public RiftListener(LimboPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        String id = plugin.rifts().riftIdOf(clicked);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.rifts().exists(id)) {
            clicked.remove(); // leftover hitbox of a removed rift
            return;
        }
        plugin.menu().open(e.getPlayer(), id);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        plugin.rifts().onEntitiesLoad(e.getChunk(), e.getEntities());
    }
}
