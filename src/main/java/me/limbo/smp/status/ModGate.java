package me.limbo.smp.status;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Limbo FX client mod announces itself on the plugin channel "limbo:hello" a moment after joining.
 * /limbo requiremod on: players without the mod are kicked after mod.grace-seconds (ops and anyone with
 * limbo.mod.bypass are exempt). It also makes the server resource pack mandatory. The setting is saved in config.yml.
 */
public final class ModGate implements Listener, PluginMessageListener {
    public static final String CHANNEL = "limbo:hello";

    private final LimboPlugin plugin;
    private final Set<UUID> hasMod = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> versionsSeen = new HashMap<>();

    public ModGate(LimboPlugin plugin) { this.plugin = plugin; }

    public void register() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void shutdown() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
    }

    public boolean required() { return plugin.getConfig().getBoolean("mod.required", false); }

    public boolean hasMod(Player p) { return hasMod.contains(p.getUniqueId()); }

    public void setRequired(boolean on) {
        plugin.getConfig().set("mod.required", on);
        plugin.saveConfig();
        if (on) for (Player p : Bukkit.getOnlinePlayers()) schedule(p);
    }

    private int grace() { return Math.max(3, plugin.getConfig().getInt("mod.grace-seconds", 15)); }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        hasMod.add(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { schedule(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { hasMod.remove(e.getPlayer().getUniqueId()); }

    private void schedule(Player p) {
        if (!required() || p.hasPermission("limbo.mod.bypass") || p.isOp()) return;
        if (hasMod(p)) return;
        int secs = grace();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !required() || hasMod(p)) return;
            String url = plugin.getConfig().getString("mod.download-url", "");
            String extra = url == null || url.isBlank() ? "" : "\n<gray>" + Msg.esc(url);
            p.kick(Msg.parse("<#9b59ff>This server requires the Limbo FX mod.<newline><gray>Install it (Fabric, Minecraft 26.3) and rejoin." + extra));
        }, secs * 20L);
    }
}
