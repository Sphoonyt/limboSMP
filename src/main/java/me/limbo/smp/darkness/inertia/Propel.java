package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * R - 12 block, extremely fast dash. Direction is purely horizontal (where you face, pitch is ignored).
 * Momentum is cut dead at the end, then you're immune to all knockback for 5s. 35s cooldown.
 */
public final class Propel extends Channel {
    private static final double DISTANCE = 12.0;
    private static final double SPEED = 3.8;       // blocks per tick (~76 blocks/s), just under the 3.9 velocity packet cap
    private static final int MAX_TICKS = 8;
    private static final int IMMUNITY_TICKS = 100; // 5s

    public Propel() {
        super("propel", "Propel", "Extreme dash, then knockback immunity.", ChannelInput.R, 35_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        LimboPlugin pl = LimboPlugin.get();

        // horizontal heading from yaw only, so looking up/down never changes the dash
        double yaw = Math.toRadians(p.getLocation().getYaw());
        final Vector direction = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        final Location start = p.getLocation().clone();

        p.getWorld().playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.6f);
        p.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        ParticleUtil.blissDust(start.clone().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 14, 0.5);

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
                    stalled = (now.distance(last) < SPEED * 0.15) ? stalled + 1 : 0; // hit a wall
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
        ParticleUtil.blissDust(at.clone().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 10, 0.35);
    }

    private void finish(Player p) {
        p.setVelocity(new Vector(0, 0, 0)); // cut momentum
        LimboPlugin.get().status().knockbackImmunity(p, IMMUNITY_TICKS);
        ParticleUtil.blissDust(p.getLocation().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 14, 0.5);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.5f);
        p.sendActionBar(Msg.parse("<#40e0d0>Knockback immunity (5s)"));
    }
}
