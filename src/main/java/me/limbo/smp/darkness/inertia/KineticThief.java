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

/**
 * L - launch a wave of negative energy. Enemies it passes through are stunned for 1s, take 2 hearts of true damage
 * and get Slowness V for 8s. Every enemy affected gives you 4 seconds of Speed III (stacking). 65s cooldown.
 */
public final class KineticThief extends Channel {
    private static final String NAME = "Kinetic Thief";
    private static final double SPEED = 1.6;       // blocks per tick
    private static final double RANGE = 22.0;
    private static final double HALF_WIDTH = 2.8;
    private static final double HALF_HEIGHT = 1.9;
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
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        final Vector side = right;
        final Vector up = side.clone().crossProduct(dir).normalize();
        Location pos = eye.clone().add(dir.clone().multiply(1.2));
        Set<UUID> hit = new HashSet<>();

        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.6f);
        w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);
        p.sendActionBar(Msg.parse("<#40e0d0>Kinetic Thief"));

        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (!pos.clone().add(dir.clone().multiply(SPEED)).getBlock().isPassable()) { // the wave breaks on walls
                    ParticleUtil.blissDust(pos, InertiaDarkness.DARK, InertiaDarkness.LIGHT, 12, 0.4);
                    cancel();
                    return;
                }
                draw(w, pos, dir, side);
                for (LivingEntity e : w.getNearbyLivingEntities(pos, HALF_WIDTH + 2)) {
                    if (e.equals(p) || hit.contains(e.getUniqueId())) continue;
                    if (!Targeting.canHarm(p, e) || !Targeting.isEnemy(e)) continue;
                    Vector d = e.getBoundingBox().getCenter().subtract(pos.toVector());
                    if (Math.abs(d.dot(dir)) > SPEED + 0.7) continue;
                    if (Math.abs(d.dot(side)) > HALF_WIDTH || Math.abs(d.dot(up)) > HALF_HEIGHT) continue;
                    hit.add(e.getUniqueId());
                    affect(p, e);
                }
                pos.add(dir.clone().multiply(SPEED));
                travelled += SPEED;
                if (travelled >= RANGE) cancel();
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
        return true;
    }

    private void affect(Player p, LivingEntity e) {
        TrueDamage.apply(p, e, DAMAGE);
        if (e.isDead()) return;
        LimboPlugin.get().status().stun(e, STUN_TICKS);
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, true, true));
        Feedback.affected(e, NAME, p);
        ParticleUtil.burst(e.getLocation().add(0, 1, 0), InertiaDarkness.LIGHT, 18);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        grantSpeed(p);
    }

    /** 4 more seconds of Speed III for every enemy hit. */
    private void grantSpeed(Player p) {
        int duration = SPEED_TICKS_PER_ENEMY;
        PotionEffect cur = p.getPotionEffect(PotionEffectType.SPEED);
        if (cur != null && cur.getAmplifier() >= SPEED_AMPLIFIER && cur.getDuration() > 0) duration += cur.getDuration();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, SPEED_AMPLIFIER, false, true, true));
        p.sendActionBar(Msg.parse("<#40e0d0>Speed III <gray>" + (duration / 20) + "s"));
    }

    /** A curved wave front, tips trailing back, in light and dark teal. */
    private void draw(World w, Location pos, Vector dir, Vector side) {
        Particle.DustTransition front = new Particle.DustTransition(InertiaDarkness.DARK, InertiaDarkness.LIGHT, 1.3f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.5f);
        int i = 0;
        for (double s = -HALF_WIDTH; s <= HALF_WIDTH; s += 0.35, i++) {
            double back = s * s * 0.1; // the edges lag behind the centre
            Location l = pos.clone().add(side.clone().multiply(s)).subtract(dir.clone().multiply(back));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0.3, 0, 0, front, true);
            if (i % 2 == 0) w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.4, 0.05, 0, dark, true);
        }
    }
}
