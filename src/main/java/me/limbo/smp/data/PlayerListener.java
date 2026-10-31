package me.limbo.smp.data;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final LimboPlugin plugin;

    public PlayerListener(LimboPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        if (url != null && !url.isBlank()) {
            if (sha1 == null || sha1.isBlank()) {
                plugin.getLogger().warning("resource-pack.url is set but resource-pack.sha1 is empty, not sending the pack.");
            } else {
                e.getPlayer().setResourcePack(url, sha1.trim().toLowerCase(), plugin.getConfig().getBoolean("resource-pack.required", false));
            }
        }
        if (plugin.data().assignIfMissing(e.getPlayer())) {
            Darkness d = plugin.data().getDarkness(e.getPlayer());
            if (d != null) {
                Msg.send(e.getPlayer(), "<#9b59ff>The void has chosen you: <white>" + d.displayName()
                        + "<gray>. Use <white>/limbo darknessinfo<gray> to learn more.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.passives().release(e.getPlayer());
        plugin.dispatcher().forget(e.getPlayer().getUniqueId());
        plugin.data().saveIfDirty();
    }
}
