package me.limbo.smp.darkness.singularity;

import me.limbo.smp.util.Feedback;
import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.SingularityDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TimeUtil;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * L - throw a fast dagger. On an enemy: 1.5 hearts true damage, and they can't jump or throw wind charges
 * for 4 seconds. 2 charges, each recharges 40s after it is used (tracked here, not by the normal cooldown).
 */
public final class AxiomDagger extends Channel {
    private static final int CHARGES = 2;
    private static final long RECHARGE_MS = 40_000L;
    private static final double SPEED = 2.2;       // blocks per tick
    private static final double RANGE = 40.0;
    private static final double DAMAGE = 3.0;      // 1.5 hearts
    private static final int GROUND_TICKS = 80;    // 4s

    /** Per player: the time each charge becomes available again. */
    private final Map<UUID, long[]> charges = new HashMap<>();

    public AxiomDagger() {
        super("axiom_dagger", "Axiom Dagger", "Fast dagger: true damage, can't jump or wind charge.",
                ChannelInput.L, RECHARGE_MS);
    }

    @Override public boolean usesCustomCooldown() { return true; }

    @Override public void onCooldownReset(UUID id) { charges.remove(id); }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        long now = System.currentTimeMillis();
        long[] slots = charges.computeIfAbsent(p.getUniqueId(), k -> new long[CHARGES]);

        int use = -1;
        long soonest = Long.MAX_VALUE;
        for (int i = 0; i < CHARGES; i++) {
            if (slots[i] <= now) { use = i; break; }
            soonest = Math.min(soonest, slots[i]);
        }
        if (use < 0) {
            p.sendActionBar(Msg.parse("<gray>Axiom Dagger <dark_gray>- <red>" + TimeUtil.seconds(soonest - now)));
            return false;
        }
        slots[use] = now + RECHARGE_MS;
        int left = 0;
        for (long s : slots) if (s <= now) left++;
        p.sendActionBar(Msg.parse("<#9646e6>Axiom Dagger <gray>" + left + "/" + CHARGES));

        launch(p);
        return true;
    }

    private void launch(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location pos = eye.clone().add(dir.clone().multiply(0.8));
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1e-4) side = new Vector(1, 0, 0);
        side.normalize();
        final Vector guard = side;

        w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.8f);
        w.playSound(eye, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);

        Predicate<Entity> filter = e -> e instanceof LivingEntity le && !e.equals(p)
                && Targeting.canHarm(p, le) && Targeting.isEnemy(le);

        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                RayTraceResult r = w.rayTrace(pos, dir, SPEED, FluidCollisionMode.NEVER, true, 0.3, filter);
                if (r != null) {
                    Location at = r.getHitPosition().toLocation(w);
                    if (r.getHitEntity() instanceof LivingEntity target) {
                        hit(p, target, at);
                    } else {
                        ParticleUtil.blissDust(at, SingularityDarkness.COLOR, 8, 0.2);
                        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.4f);
                    }
                    cancel();
                    return;
                }
                draw(w, pos, dir, guard);
                pos.add(dir.clone().multiply(SPEED));
                travelled += SPEED;
                if (travelled >= RANGE) cancel();
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    /** A small dagger: blade along the flight direction plus a crossguard. */
    private void draw(World w, Location pos, Vector dir, Vector guard) {
        Particle.DustTransition blade = new Particle.DustTransition(SingularityDarkness.LIGHT, SingularityDarkness.COLOR, 1.0f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.3f);
        for (double t = -0.5; t <= 0.7; t += 0.2) {
            Location l = pos.clone().add(dir.clone().multiply(t));
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, blade, true);
            if (t < 0) w.spawnParticle(Particle.DUST, l, 1, 0.03, 0.03, 0.03, 0, dark, true); // handle
        }
        for (int k = -1; k <= 1; k += 2) {
            Location g = pos.clone().add(dir.clone().multiply(-0.2)).add(guard.clone().multiply(0.22 * k));
            w.spawnParticle(Particle.DUST, g, 1, 0, 0, 0, 0, dark, true);
        }
        w.spawnParticle(Particle.REVERSE_PORTAL, pos, 1, 0.05, 0.05, 0.05, 0.02, null, true);
    }

    private void hit(Player p, LivingEntity target, Location at) {
        TrueDamage.apply(p, target, DAMAGE);
        Feedback.affected(target, "Axiom Dagger", p);
        ParticleUtil.burst(at, SingularityDarkness.COLOR, 24);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.4f);
        if (target.isDead()) return;
        LimboPlugin.get().status().ground(target, GROUND_TICKS);
        if (target instanceof Player victim) {
            victim.sendActionBar(Msg.parse("<#9646e6>Grounded <gray>- no jumping or wind charges (4s)"));
        }
    }
}
