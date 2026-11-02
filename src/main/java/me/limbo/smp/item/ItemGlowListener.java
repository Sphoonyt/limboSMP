package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Every plugin item (see LimboItems#markPluginItem) glows while it lies on the ground as an item entity. */
public final class ItemGlowListener implements Listener {
    private final LimboPlugin plugin;

    public ItemGlowListener(LimboPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onSpawn(ItemSpawnEvent e) {
        if (plugin.items().isPluginItem(e.getEntity().getItemStack())) e.getEntity().setGlowing(true);
    }

    /** Items dropped before this was added (or by an older version) get the glow when their chunk loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en instanceof Item item && plugin.items().isPluginItem(item.getItemStack())) item.setGlowing(true);
        }
    }
}
