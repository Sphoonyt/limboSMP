package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L - a short windup, then a cone beam of negative energy. Enemies in the cone are stunned for 1s, take 2 hearts of true damage
 * and get Slowness V for 8s. Every enemy affected gives you 4 seconds of Speed III (stacking). 65s cooldown.
 */
public final class KineticThief extends Channel {
    private static final String NAME = "Kinetic Thief";
    private static final double RANGE = 16.0;
    private static final double CONE_SLOPE = 0.42;  // radius per block of distance (about a 23 degree half angle)
    private static final double SWEEP_PER_TICK = 3.5; // the beam front travels this far each tick
    private static final int WINDUP_TICKS = 6;      // 0.3s
    private static final int STUN_TICKS = 20;      // 1s
    private static final double DAMAGE = 4.0;      // 2 hearts
    private static final int SLOW_TICKS = 160;     // 8s
    private static final int SLOW_AMPLIFIER = 4;   // Slowness V
    private static final int SPEED_TICKS_PER_ENEMY = 80; // 4s
    private static final int SPEED_AMPLIFIER = 2;  // Speed III

    public KineticThief() {
        super("kinetic_thief", NAME, "Wave that stuns, hurts and steals speed.", ChannelInput.L, 65_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        World w = p.getWorld();
        // a rising charge-up through the windup, then the boom on release
        me.limbo.smp.util.Sfx.sweep(p.getEyeLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.7f, 0.9f, 1.9f, WINDUP_TICKS);
        me.limbo.smp.util.Sfx.sweep(p.getEyeLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.6f, 1.6f, WINDUP_TICKS);

        // short windup: light and dark teal motes are pulled into a point in front of you
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Location focus = eye.clone().add(dir.clone().multiply(1.6));
                double pull = 1.0 - (t / (double) WINDUP_TICKS);
                for (int i = 0; i < 4; i++) {
                    Vector o = new Vector(ThreadLocalRandom.current().nextDouble(-1, 1), ThreadLocalRandom.current().nextDouble(-1, 1), ThreadLocalRandom.current().nextDouble(-1, 1));
                    if (o.lengthSquared() < 1e-4) continue;
                    o.normalize().multiply(0.4 + 1.1 * pull);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, focus.clone().add(o), 1, 0, 0, 0, 0,
                            new Particle.DustTransition(InertiaDarkness.DARK, InertiaDarkness.LIGHT, 1.0f), true);
                }
                if (++t >= WINDUP_TICKS) { cancel(); fire(p); }
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
        return true;
    }

    /** The cone beam: sweeps out from your eyes in a few ticks, stopping at the first wall. */
    private void fire(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        final Vector side = right.normalize();
        final Vector up = side.clone().crossProduct(dir).normalize();
        var wall = w.rayTraceBlocks(eye, dir, RANGE, org.bukkit.FluidCollisionMode.NEVER, true);
        final double reach = wall == null ? RANGE : Math.max(1.0, eye.distance(wall.getHitPosition().toLocation(w)) - 0.2);
        Set<UUID> hit = new HashSet<>();

        me.limbo.smp.util.Sfx.play(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.5f, me.limbo.smp.util.Sfx.Theme.INERTIA);
        me.limbo.smp.util.Sfx.add(eye, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);

        new BukkitRunnable() {
            double front = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                double from = front;
                front = Math.min(reach, front + SWEEP_PER_TICK);
                // draw the part of the cone just swept: rings of dust whose radius grows with distance
                for (double d = Math.max(0.8, from); d <= front; d += 0.7) {
                    double r = d * CONE_SLOPE;
                    Location c = eye.clone().add(dir.clone().multiply(d));
                    int pts = Math.max(8, (int) (r * 7));
                    for (int k = 0; k < pts; k++) {
                        double a = Math.PI * 2 * k / pts;
                        Location l = c.clone().add(side.clone().multiply(Math.cos(a) * r)).add(up.clone().multiply(Math.sin(a) * r));
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0,
                                new Particle.DustTransition(InertiaDarkness.DARK, InertiaDarkness.LIGHT, 1.1f), true);
                    }
                    w.spawnParticle(Particle.DUST, c, 2, r * 0.3, r * 0.3, r * 0.3, 0, new Particle.DustOptions(ParticleUtil.VOID, 1.2f), true);
                }
                for (LivingEntity e : w.getNearbyLivingEntities(eye, reach + 3)) {
                    if (e.equals(p) || hit.contains(e.getUniqueId())) continue;
                    if (!Targeting.canHarm(p, e) || !Targeting.isEnemy(e)) continue;
                    Vector d = e.getBoundingBox().getCenter().subtract(eye.toVector());
                    double along = d.dot(dir);
                    if (along < 0.5 || along > front + 0.5 || along > reach + e.getWidth()) continue;
                    double off = d.clone().subtract(dir.clone().multiply(along)).length();
                    if (off > along * CONE_SLOPE + e.getWidth() * 0.6 + 0.4) continue;
                    hit.add(e.getUniqueId());
                    affect(p, e);
                }
                if (front >= reach) cancel();
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    private void affect(Player p, LivingEntity e) {
        TrueDamage.apply(p, e, DAMAGE);
        if (e.isDead()) return;
        LimboPlugin.get().status().stun(e, STUN_TICKS);
        // one look for stun and slow; the slow covers less of the screen and takes over when the stun ends
        LimboPlugin.get().screenFx().show(e, me.limbo.smp.status.ScreenFx.Fx.KINETIC_STUN, STUN_TICKS);
        LimboPlugin.get().screenFx().show(e, me.limbo.smp.status.ScreenFx.Fx.KINETIC_SLOW, SLOW_TICKS);
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, true, true));
        Feedback.affected(e, NAME, p);
        ParticleUtil.burst(e.getLocation().add(0, 1, 0), InertiaDarkness.LIGHT, 18);
        me.limbo.smp.util.Sfx.play(e.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f, me.limbo.smp.util.Sfx.Theme.INERTIA);
        grantSpeed(p);
    }

    /** 4 more seconds of Speed III for every enemy hit. */
    private void grantSpeed(Player p) {
        int duration = SPEED_TICKS_PER_ENEMY;
        PotionEffect cur = p.getPotionEffect(PotionEffectType.SPEED);
        if (cur != null && cur.getAmplifier() >= SPEED_AMPLIFIER && cur.getDuration() > 0) duration += cur.getDuration();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, SPEED_AMPLIFIER, false, true, true));
    }
}
