package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rift teleport: stand still for 10 seconds next to a rift, then one player soul is consumed and you are
 * teleported to the rift farthest away from the one you're at. This never starts the rift relocation timer.
 */
public final class RiftTeleport {
    private final LimboPlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public RiftTeleport(LimboPlugin plugin) { this.plugin = plugin; }

    public void start(Player p, String riftId) {
        UUID id = p.getUniqueId();
        if (active.containsKey(id)) {
            Msg.send(p, "<gray>You're already teleporting.");
            return;
        }
        RiftManager.Rift origin = plugin.rifts().get(riftId);
        if (origin == null) return;
        if (plugin.items().countSouls(p) < 1) {
            Msg.send(p, "<red>You need a player soul to use the rift teleport.");
            return;
        }
        if (farthest(origin.location()) == null) {
            Msg.send(p, "<red>There is no other rift to teleport to.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());

        int totalTicks = (int) (plugin.getConfig().getDouble("souls.teleport-seconds", 10.0) * 20);
        Location start = p.getLocation().clone();
        Msg.send(p, "<#9b59ff>Stand still...");

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancelFor(id, null); return; }
                Location now = p.getLocation();
                if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.01) {
                    cancelFor(id, "<red>You moved. Teleport cancelled.");
                    return;
                }
                ticks += 5;
                int left = (int) Math.ceil((totalTicks - ticks) / 20.0);
                p.sendActionBar(Msg.parse("<#9b59ff>Rift teleport <gray>- stand still <white>" + Math.max(0, left) + "s"));
                ParticleUtil.ring(now.clone().add(0, 0.1, 0), 1.2, 24, ParticleUtil.RIFT_ACCENT);
                ParticleUtil.risingMotes(now, ParticleUtil.RIFT_ACCENT, 3, 0.8);
                if (ticks >= totalTicks) complete(p, riftId, start);
            }
        }.runTaskTimer(plugin, 0L, 5L);
        active.put(id, task);
    }

    private void cancelFor(UUID id, String message) {
        BukkitTask t = active.remove(id);
        if (t != null) t.cancel();
        Player p = Bukkit.getPlayer(id);
        if (p != null && message != null) Msg.send(p, message);
    }

    private void complete(Player p, String riftId, Location start) {
        cancelFor(p.getUniqueId(), null);

        // work out the destination first so a soul is never taken for nothing
        RiftManager.Rift origin = plugin.rifts().get(riftId);
        RiftManager.Rift dest = farthest(origin != null ? origin.location() : start);
        if (dest == null) {
            Msg.send(p, "<red>There is no rift to teleport to.");
            return;
        }
        if (!plugin.items().consumeSoul(p, null)) {
            Msg.send(p, "<red>You no longer have a player soul.");
            return;
        }

        Location from = p.getLocation().add(0, 1, 0);
        ParticleUtil.burst(from, ParticleUtil.RIFT_ACCENT, 40);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        p.teleport(dest.location().clone());
        Location to = p.getLocation().add(0, 1, 0);
        ParticleUtil.burst(to, ParticleUtil.RIFT_ACCENT, 40);
        to.getWorld().playSound(to, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
        Msg.send(p, "<#9b59ff>The rift carries you across the world.");
        // deliberately NOT plugin.rifts().onCraft(...): teleporting never starts the relocation timer
    }

    /** The rift (in the same world) farthest from the given spot, or null if there isn't another one. */
    private RiftManager.Rift farthest(Location from) {
        RiftManager.Rift best = null;
        double bestD = 4.0; // must be more than 2 blocks away
        for (RiftManager.Rift r : plugin.rifts().all()) {
            if (r.location().getWorld() != from.getWorld()) continue;
            double d = r.location().distanceSquared(from);
            if (d > bestD) { bestD = d; best = r; }
        }
        return best;
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) cancelFor(id, null);
    }
}
