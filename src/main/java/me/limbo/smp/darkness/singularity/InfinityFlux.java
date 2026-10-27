package me.limbo.smp.darkness.singularity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.darkness.SingularityDarkness;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * While the totem is held, any projectile that would come within 1 block of you stops dead and hangs in the
 * air for 2 seconds, then disappears. Your own projectiles and those of players you trust are ignored.
 * (Darkness abilities aren't entities, so they are never affected.)
 * Runs every tick and predicts each projectile's next step so fast arrows can't skip past the radius.
 */
public final class InfinityFlux implements FluxPassive, Listener {
    private static final double RADIUS = 1.0;
    private static final int HOLD_TICKS = 40;

    private static final class Frozen {
        final Projectile projectile;
        final Location at;
        int age = 0;
        Frozen(Projectile projectile, Location at) { this.projectile = projectile; this.at = at; }
    }

    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private BukkitTask task;

    @Override public String id() { return "infinity"; }
    @Override public String name() { return "Infinity"; }
    @Override public String description() { return "Projectiles that come within 1 block of you freeze mid-air for 2 seconds, then vanish."; }

    @Override
    public void onActivate(Player p) {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(LimboPlugin.get(), this::tick, 1L, 1L);
    }

    @Override
    public void onShutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Frozen f : frozen.values()) if (f.projectile.isValid()) f.projectile.remove();
        frozen.clear();
    }

    private void tick() {
        holdFrozen();
        LimboPlugin pl = LimboPlugin.get();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!pl.fluxActive(p, SingularityDarkness.ID)) continue;
            Vector center = p.getBoundingBox().getCenter();
            for (Entity e : p.getNearbyEntities(6, 6, 6)) {
                if (!(e instanceof Projectile pr) || pr instanceof FishHook) continue;
                if (frozen.containsKey(pr.getUniqueId())) continue;
                if (pr instanceof AbstractArrow aa && aa.isInBlock()) continue;
                ProjectileSource src = pr.getShooter();
                if (src instanceof Player sp
                        && (sp.getUniqueId().equals(p.getUniqueId()) || pl.trust().isTrusting(p.getUniqueId(), sp.getUniqueId()))) {
                    continue;
                }
                Vector pos = pr.getLocation().toVector();
                Vector vel = pr.getVelocity();
                double t = entryTime(pos, vel, center);
                if (t < 0) continue;
                Vector hit = pos.clone().add(vel.clone().multiply(t));
                Location at = new Location(pr.getWorld(), hit.getX(), hit.getY(), hit.getZ(),
                        pr.getLocation().getYaw(), pr.getLocation().getPitch());
                freeze(pr, at);
            }
        }
    }

    /** Smallest t in [0,1] where pos + t*vel is within RADIUS of center, or -1. */
    private static double entryTime(Vector pos, Vector vel, Vector center) {
        Vector d = pos.clone().subtract(center);
        double c = d.dot(d) - RADIUS * RADIUS;
        if (c <= 0) return 0; // already inside
        double a = vel.dot(vel);
        if (a < 1.0E-9) return -1;
        double b = 2 * d.dot(vel);
        double disc = b * b - 4 * a * c;
        if (disc < 0) return -1;
        double t = (-b - Math.sqrt(disc)) / (2 * a);
        return (t >= 0 && t <= 1) ? t : -1;
    }

    private void freeze(Projectile pr, Location at) {
        pr.teleport(at);
        pr.setVelocity(new Vector(0, 0, 0));
        pr.setGravity(false);
        if (pr instanceof AbstractArrow aa) aa.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        frozen.put(pr.getUniqueId(), new Frozen(pr, at.clone()));
        ParticleUtil.blissDust(at, SingularityDarkness.COLOR, 10, 0.25);
        at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
    }

    private void holdFrozen() {
        Iterator<Map.Entry<UUID, Frozen>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Frozen f = it.next().getValue();
            Projectile pr = f.projectile;
            f.age++;
            if (!pr.isValid() || f.age > HOLD_TICKS) {
                if (pr.isValid()) {
                    Location l = pr.getLocation();
                    ParticleUtil.blissDust(l, SingularityDarkness.COLOR, 12, 0.25);
                    l.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
                    pr.remove();
                }
                it.remove();
                continue;
            }
            pr.setVelocity(new Vector(0, 0, 0));
            if (pr.getLocation().distanceSquared(f.at) > 0.01) pr.teleport(f.at);
            if (f.age % 6 == 0) ParticleUtil.blissDust(pr.getLocation(), SingularityDarkness.COLOR, 3, 0.15);
        }
    }

    /** Suspended projectiles can never hit anything. */
    @EventHandler(priority = EventPriority.LOW)
    public void onHit(ProjectileHitEvent e) {
        if (frozen.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (frozen.containsKey(e.getDamager().getUniqueId())) e.setCancelled(true);
    }
}
