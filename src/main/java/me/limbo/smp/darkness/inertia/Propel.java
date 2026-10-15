package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * R - 12 block, extremely fast dash in the direction you look. Momentum is cut dead at the end,
 * then you're immune to all knockback for 5s. 35s cooldown.
 */
public final class Propel extends Channel {
    private static final double DISTANCE = 12.0;
    private static final double SPEED = 2.8;       // blocks per tick (~56 blocks/s)
    private static final int MAX_TICKS = 14;
    private static final int IMMUNITY_TICKS = 100; // 5s

    public Propel() {
        super("propel", "Propel", "Extreme dash, then knockback immunity.", ChannelInput.R, 35_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        LimboPlugin pl = LimboPlugin.get();

        Vector dir = p.getLocation().getDirection().normalize();
        if (p.isOnGround() && dir.getY() < 0) { // don't drive into the floor
            dir.setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
            dir.normalize();
        }
        final Vector direction = dir;
        final Location start = p.getLocation().clone();

        p.getWorld().playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.4f);
        p.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        ParticleUtil.burst(start.clone().add(0, 1, 0), InertiaDarkness.LIGHT, 20);

        new BukkitRunnable() {
            Location last = start.clone();
            int ticks = 0;
            int stalled = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                Location now = p.getLocation();
                if (now.getWorld() != start.getWorld()) { finish(p); cancel(); return; }

                double travelled = now.distance(start);
                if (ticks > 0) {
                    stalled = (now.distance(last) < SPEED * 0.3) ? stalled + 1 : 0; // hit a wall
                }
                trail(now);

                if (travelled >= DISTANCE - 0.3 || stalled >= 2 || ticks >= MAX_TICKS) {
                    finish(p);
                    cancel();
                    return;
                }
                double speed = Math.min(SPEED, DISTANCE - travelled);
                p.setVelocity(direction.clone().multiply(speed));
                last = now.clone();
                ticks++;
            }
        }.runTaskTimer(pl, 0L, 1L);
        return true;
    }

    private void trail(Location at) {
        Location mid = at.clone().add(0, 1, 0);
        ParticleUtil.blissDust(mid, InertiaDarkness.DARK, InertiaDarkness.LIGHT, 10, 0.35);
        at.getWorld().spawnParticle(Particle.CLOUD, mid, 3, 0.25, 0.4, 0.25, 0.01);
    }

    private void finish(Player p) {
        p.setVelocity(new Vector(0, 0, 0)); // cut momentum
        LimboPlugin.get().status().knockbackImmunity(p, IMMUNITY_TICKS);
        Location l = p.getLocation().add(0, 1, 0);
        ParticleUtil.burst(l, InertiaDarkness.LIGHT, 30);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.5f);
        p.sendActionBar(Msg.parse("<#40e0d0>Knockback immunity (5s)"));
    }
}
