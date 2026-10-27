package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;

/**
 * Server-wide events, run by ops: /limbo event <start|stop|end|status> <server_start|progression|dispersal>.
 * State lives in events.yml and survives restarts.
 */
public final class EventManager implements Listener {
    private final LimboPlugin plugin;
    private final File file;
    private final ServerStartEvent serverStart;
    private final ProgressionEvent progression;

    public EventManager(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
        this.serverStart = new ServerStartEvent(plugin, this);
        this.progression = new ProgressionEvent(plugin, this);
    }

    public ServerStartEvent serverStart() { return serverStart; }
    public ProgressionEvent progression() { return progression; }

    /** The world events happen in: events.world, or the default overworld. */
    public World world() {
        String name = plugin.getConfig().getString("events.world", "");
        if (name != null && !name.isBlank()) {
            World w = Bukkit.getWorld(name);
            if (w != null) return w;
            plugin.getLogger().warning("events.world '" + name + "' not found, using the default overworld.");
        }
        for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == World.Environment.NORMAL) return w;
        return null;
    }

    public void load() {
        YamlConfiguration y = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        serverStart.load(y);
        progression.load(y);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        serverStart.save(y);
        progression.save(y);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save events.yml: " + ex.getMessage());
        }
    }

    public void start() { progression.startTasks(); }

    public void shutdown() {
        progression.shutdown();
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!serverStart.isActive() || serverStart.hasBeenTreated(e.getPlayer().getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) serverStart.treatLatecomer(e.getPlayer());
        }, 40L);
    }

    // ------------------------------------------------------------ /limbo event ...

    public void command(CommandSender s, String[] a) {
        if (a.length == 0) { usage(s); return; }
        String action = a[0].toLowerCase();

        if (action.equals("status")) {
            serverStart.describe().forEach(l -> Msg.send(s, l));
            progression.describe().forEach(l -> Msg.send(s, l));
            Msg.send(s, "<gray>Rift Dispersal: <dark_gray>not built yet");
            return;
        }
        if (a.length < 2) { usage(s); return; }
        String event = a[1].toLowerCase().replace("-", "_");

        switch (event) {
            case "server_start", "serverstart" -> {
                switch (action) {
                    case "start" -> serverStart.start(s);
                    case "stop", "end" -> serverStart.stop(s);
                    default -> usage(s);
                }
            }
            case "progression" -> {
                switch (action) {
                    case "start" -> progression.start(s);
                    case "end" -> progression.end(s);
                    case "stop" -> progression.stop(s);
                    default -> usage(s);
                }
            }
            case "dispersal", "rift_dispersal" -> Msg.send(s, "<gray>Rift Dispersal isn't built yet, it's waiting on its details.");
            default -> usage(s);
        }
    }

    private void usage(CommandSender s) {
        Msg.send(s, "<gray>/limbo event <start|stop|end|status> <server_start|progression|dispersal>");
        Msg.send(s, "<dark_gray>progression: <gray>end = complete it (unlocks + reward), stop = abort without unlocking");
    }
}
