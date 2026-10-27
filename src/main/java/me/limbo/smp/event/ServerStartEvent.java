package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.SpotFinder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server Start: every online player gets a freshly rolled darkness (siphon cleared) and is sent to a random
 * spot inside the world border. While the event stays active, players who join later and haven't been
 * treated yet get the same on join. Cursed items are rolled here too once their rules are defined.
 */
public final class ServerStartEvent {
    private final LimboPlugin plugin;
    private final EventManager events;
    private boolean active;
    private final Set<UUID> treated = new HashSet<>();

    public ServerStartEvent(LimboPlugin plugin, EventManager events) {
        this.plugin = plugin;
        this.events = events;
    }

    public boolean isActive() { return active; }
    public boolean hasBeenTreated(UUID id) { return treated.contains(id); }

    public void load(YamlConfiguration y) {
        active = y.getBoolean("server-start.active", false);
        treated.clear();
        for (String s : y.getStringList("server-start.treated")) {
            try { treated.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save(YamlConfiguration y) {
        y.set("server-start.active", active);
        y.set("server-start.treated", treated.stream().map(UUID::toString).collect(Collectors.toList()));
    }

    public void start(org.bukkit.command.CommandSender admin) {
        if (active) {
            Msg.send(admin, "<red>The server start event is already running. Use /limbo event stop server_start first.");
            return;
        }
        active = true;
        treated.clear();
        Bukkit.broadcast(Msg.parse(Msg.PREFIX + "<#9b59ff>The server start event has begun."));
        treat(new ArrayList<>(Bukkit.getOnlinePlayers()));
        rollCursedItems();
        events.save();
        Msg.send(admin, "<gray>Server start event started for <white>" + Bukkit.getOnlinePlayers().size() + "<gray> players.");
    }

    public void stop(org.bukkit.command.CommandSender admin) {
        if (!active) {
            Msg.send(admin, "<gray>The server start event isn't running.");
            return;
        }
        active = false;
        events.save();
        Msg.send(admin, "<gray>Server start event stopped. Latecomers are no longer treated.");
    }

    /** Reroll darkness + clear siphon, then spread everyone across the map. */
    public void treat(List<Player> players) {
        if (players.isEmpty()) return;
        for (Player p : players) reroll(p);

        World world = events.world();
        if (world == null) {
            plugin.getLogger().warning("Server start event: no overworld to spread players in.");
            return;
        }
        SpotFinder.find(plugin, world, players.size(), 400, spots -> {
            Iterator<Location> it = spots.iterator();
            for (Player p : players) {
                if (!p.isOnline()) continue;
                if (!it.hasNext()) {
                    Msg.send(p, "<red>Couldn't find you a spot, ask an op.");
                    continue;
                }
                p.setFallDistance(0f);
                p.teleportAsync(it.next());
                treated.add(p.getUniqueId());
            }
            events.save();
        });
    }

    /** Players who join while the event is running and haven't been treated yet. */
    public void treatLatecomer(Player p) {
        if (!active || treated.contains(p.getUniqueId())) return;
        treat(List.of(p));
    }

    private void reroll(Player p) {
        Darkness d = plugin.data().assignRandom(p.getUniqueId());
        plugin.data().clearSiphon(p.getUniqueId());
        plugin.data().saveIfDirty();
        plugin.items().syncTotems(p);
        if (d != null) Msg.send(p, "<#9b59ff>Your darkness has been chosen: <white>" + Msg.esc(d.displayName()));
    }

    /** Cursed items: waiting on the rules for these. */
    private void rollCursedItems() {
        plugin.getLogger().info("Server start event: cursed item roll is not defined yet (placeholder).");
    }

    public List<String> describe() {
        List<String> l = new ArrayList<>();
        l.add("<gray>Server start: " + (active ? "<green>active" : "<red>inactive") + " <dark_gray>(" + treated.size() + " players treated)");
        return l;
    }
}
