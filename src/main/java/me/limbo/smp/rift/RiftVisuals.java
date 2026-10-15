package me.limbo.smp.rift;

import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws a shadow rift: a tall jagged vertical tear. Void-black core, purple edges fading to black,
 * lilac glints, inward-pulling portal wisps and rising sparks. The shape is deterministic per rift (seed)
 * with a slow shimmer; call every ~4 ticks.
 */
public final class RiftVisuals {
    private static final Color GLINT = Color.fromRGB(235, 215, 255);
    private static final int SEGMENTS = 14;
    private static final double HEIGHT = 3.2;

    private RiftVisuals() {}

    public static void render(Location base, double yaw, int tick, long seed) {
        World w = base.getWorld();
        if (w == null) return;
        Random shape = new Random(seed);
        ThreadLocalRandom live = ThreadLocalRandom.current();
        double rx = Math.cos(yaw), rz = Math.sin(yaw);

        Particle.DustOptions core = new Particle.DustOptions(ParticleUtil.VOID, 1.9f);
        Particle.DustTransition edge = new Particle.DustTransition(ParticleUtil.RIFT_ACCENT, ParticleUtil.VOID, 1.1f);
        Particle.DustOptions glint = new Particle.DustOptions(GLINT, 0.7f);

        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double env = Math.sin(Math.PI * t); // thin at the tips, widest in the middle
            double off = (shape.nextDouble() - 0.5) * 0.5 * (0.3 + env) + 0.06 * Math.sin(tick * 0.08 + i);
            double half = 0.06 + 0.42 * env * (0.55 + 0.45 * shape.nextDouble());
            double y = base.getY() + 0.1 + HEIGHT * t;
            double cx = base.getX() + rx * off;
            double cz = base.getZ() + rz * off;

            // black core
            for (int k = -1; k <= 1; k += 2) {
                double lat = k * half * 0.5;
                w.spawnParticle(Particle.DUST, new Location(w, cx + rx * lat, y, cz + rz * lat), 1, 0, 0, 0, 0, core, true);
            }
            // glowing edges
            Location left = new Location(w, cx - rx * half, y, cz - rz * half);
            Location right = new Location(w, cx + rx * half, y, cz + rz * half);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, left, 1, 0, 0, 0, 0, edge, true);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, right, 1, 0, 0, 0, 0, edge, true);

            if (live.nextInt(3) == 0) {
                w.spawnParticle(Particle.DUST, live.nextBoolean() ? left : right, 1, 0.03, 0.03, 0.03, 0, glint, true);
            }
            if (i % 3 == 0) {
                w.spawnParticle(Particle.REVERSE_PORTAL, live.nextBoolean() ? left : right, 1, 0, 0, 0, 0.05, null, true);
            }
        }

        // sparks drifting up off the tear
        for (int s = 0; s < 2; s++) {
            double t = live.nextDouble();
            double lat = (live.nextDouble() - 0.5) * 0.7;
            Location p = new Location(w, base.getX() + rx * lat, base.getY() + 0.1 + HEIGHT * t, base.getZ() + rz * lat);
            w.spawnParticle(Particle.END_ROD, p, 0, 0, 1, 0, 0.02 + live.nextDouble() * 0.03, null, true);
        }
    }
}
