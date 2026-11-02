package me.limbo.smp.soul;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Soul tracking: a particle line (only the tracker sees it) points at the target, at most 20 blocks long,
 * redrawn every tick. The target is told when tracking begins. It ends after 4 minutes, or when either
 * player hits the other, or on logout/death.
 */
public final class SoulTracker implements Listener {
    private static final double LINE_MAX = 20.0;
    private static final double STEP = 0.5;

    private static final class Track {
        final UUID target;
        BukkitTask task;
        Track(UUID target) { this.target = target; }
    }

    private final LimboPlugin plugin;
    private final Map<UUID, Track> tracks = new HashMap<>();

    public SoulTracker(LimboPlugin plugin) { this.plugin = plugin; }

    public void start(Player tracker, Player target, Color color) {
        stop(tracker.getUniqueId(), null);
        Track tr = new Track(target.getUniqueId());
        int totalTicks = (int) (plugin.getConfig().getDouble("souls.track-minutes", 4.0) * 60 * 20);
        UUID trackerId = tracker.getUniqueId();

        tr.task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                Player t = Bukkit.getPlayer(tr.target);
                if (!tracker.isOnline() || t == null || tracker.isDead() || t.isDead()
                        || t.getWorld() != tracker.getWorld() || ticks >= totalTicks) {
                    stop(trackerId, ticks >= totalTicks ? "Tracking ended (time ran out)." : "Tracking ended.");
                    return;
                }
                ticks++;
                drawLine(tracker, t, color);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        tracks.put(trackerId, tr);

        Msg.send(tracker, "<gray>Now tracking <white>" + Msg.esc(target.getName()) + "<gray>.");
        Msg.send(target, "<white>" + Msg.esc(tracker.getName()) + "<gray> is tracking you.");
        tracker.playSound(tracker.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
    }

    public void stop(UUID trackerId, String message) {
        Track tr = tracks.remove(trackerId);
        if (tr == null) return;
        if (tr.task != null) tr.task.cancel();
        Player p = Bukkit.getPlayer(trackerId);
        if (message != null && p != null) Msg.send(p, "<gray>" + message);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(tracks.keySet())) stop(id, null);
    }

    private void drawLine(Player tracker, Player target, Color color) {
        Location from = tracker.getLocation().add(0, 1.0, 0);
        Vector toTarget = target.getBoundingBox().getCenter().subtract(from.toVector());
        double dist = toTarget.length();
        if (dist < 1.5) return;
        Vector dir = toTarget.clone().normalize();
        double len = Math.min(LINE_MAX, dist);
        Particle.DustOptions main = new Particle.DustOptions(color, 1.0f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.0f);
        int i = 0;
        for (double s = 1.2; s <= len; s += STEP, i++) {
            Location l = from.clone().add(dir.clone().multiply(s));
            tracker.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, main);
            if (i % 2 == 0) tracker.spawnParticle(Particle.DUST, l, 1, 0.03, 0.03, 0.03, 0, dark);
        }
    }

    /** Tracking ends when the tracker hits the target or the target hits the tracker. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;

        for (Map.Entry<UUID, Track> en : new ArrayList<>(tracks.entrySet())) {
            UUID tracker = en.getKey();
            UUID target = en.getValue().target;
            boolean trackerHitTarget = tracker.equals(attacker.getUniqueId()) && target.equals(victim.getUniqueId());
            boolean targetHitTracker = target.equals(attacker.getUniqueId()) && tracker.equals(victim.getUniqueId());
            if (trackerHitTarget || targetHitTracker) {
                stop(tracker, "Tracking ended (you were in a fight).");
                Player t = Bukkit.getPlayer(target);
                if (t != null && targetHitTracker) Msg.send(t, "<gray>The soul tracking you has faded.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        stop(id, null);
        for (UUID tracker : new ArrayList<>(tracks.keySet())) {
            Track tr = tracks.get(tracker);
            if (tr != null && tr.target.equals(id)) stop(tracker, "Tracking ended (they logged out).");
        }
    }
}
