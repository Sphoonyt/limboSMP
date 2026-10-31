package me.limbo.smp.rift;

import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws a shadow rift: a tall jagged vertical tear with unstable tendrils lashing out of it.
 * Void-black core, purple edges fading to black, lilac glints, inward-pulling portal wisps.
 * The tear's shape and each tendril's character are deterministic per rift (seed); the tendrils
 * grow, retract, writhe and flicker with the tick, and the edge occasionally crackles.
 * Call every ~4 ticks.
 */
public final class RiftVisuals {
    private static final Color GLINT = Color.fromRGB(190, 140, 255);
    private static final int SEGMENTS = 14;
    private static final double HEIGHT = 3.2;
    private static final int TENDRILS = 6;
    private static final int TENDRIL_STEPS = 9;

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

        // spine geometry, kept so tendrils can root on the tear's edge
        double[] sx = new double[SEGMENTS + 1], sy = new double[SEGMENTS + 1], sz = new double[SEGMENTS + 1], sh = new double[SEGMENTS + 1];

        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double env = Math.sin(Math.PI * t); // thin at the tips, widest in the middle
            double off = (shape.nextDouble() - 0.5) * 0.5 * (0.3 + env) + 0.06 * Math.sin(tick * 0.08 + i);
            double half = 0.06 + 0.42 * env * (0.55 + 0.45 * shape.nextDouble());
            double y = base.getY() + 0.1 + HEIGHT * t;
            double cx = base.getX() + rx * off;
            double cz = base.getZ() + rz * off;
            sx[i] = cx; sy[i] = y; sz[i] = cz; sh[i] = half;

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

        drawTendrils(w, seed, tick, rx, rz, sx, sy, sz, sh, live, glint);
        crackle(w, rx, rz, sx, sy, sz, sh, live, glint);
    }

    /** Writhing tendrils that reach out of the tear, pulse in length and sometimes flicker out for a frame. */
    private static void drawTendrils(World w, long seed, int tick, double rx, double rz,
                                     double[] sx, double[] sy, double[] sz, double[] sh,
                                     ThreadLocalRandom live, Particle.DustOptions glint) {
        for (int k = 0; k < TENDRILS; k++) {
            Random tr = new Random(seed * 31L + k * 7919L); // fixed character per tendril
            int seg = 2 + tr.nextInt(SEGMENTS - 3);
            double side = tr.nextBoolean() ? 1 : -1;
            double phi = tr.nextDouble() * Math.PI * 2;
            double theta = -0.6 + tr.nextDouble() * 1.5;
            double len = 1.0 + tr.nextDouble() * 1.7;
            double phase = tr.nextDouble() * Math.PI * 2;

            if (live.nextInt(8) == 0) continue; // unstable: drop a frame now and then

            double pulse = 0.5 + 0.5 * Math.sin(tick * 0.09 + phase); // grow / retract
            double g = 0.35 + 0.65 * pulse;

            Vector dir = new Vector(Math.cos(theta) * Math.cos(phi), Math.sin(theta), Math.cos(theta) * Math.sin(phi));
            Vector p1 = dir.clone().crossProduct(new Vector(0, 1, 0));
            if (p1.lengthSquared() < 1e-4) p1 = new Vector(1, 0, 0);
            p1.normalize();
            Vector p2 = dir.clone().crossProduct(p1).normalize();

            // root on the edge of the tear
            Vector root = new Vector(sx[seg] + rx * sh[seg] * side, sy[seg], sz[seg] + rz * sh[seg] * side);

            for (int step = 1; step <= TENDRIL_STEPS; step++) {
                double s = step / (double) TENDRIL_STEPS;
                double reach = len * g * s;
                double amp = 0.28 * s;
                double wob = tick * 0.22 + phase + s * 8.0;
                Vector pos = root.clone()
                        .add(dir.clone().multiply(reach))
                        .add(p1.clone().multiply(amp * Math.sin(wob)))
                        .add(p2.clone().multiply(amp * Math.cos(wob * 0.8)));
                Location l = new Location(w, pos.getX(), pos.getY(), pos.getZ());
                float size = (float) (1.15 - 0.7 * s); // tapers toward the tip

                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0,
                        new Particle.DustTransition(ParticleUtil.RIFT_ACCENT, ParticleUtil.VOID, size), true);
                if (step % 2 == 0) {
                    w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(ParticleUtil.VOID, size + 0.3f), true);
                }
                if (step == TENDRIL_STEPS) { // tip flares
                    if (live.nextBoolean()) w.spawnParticle(Particle.DUST, l, 1, 0.03, 0.03, 0.03, 0, glint, true);
                    w.spawnParticle(Particle.REVERSE_PORTAL, l, 1, 0, 0, 0, 0.05, null, true);
                }
            }
        }
    }

    /** Short jagged arcs that jump off the edge now and then. */
    private static void crackle(World w, double rx, double rz, double[] sx, double[] sy, double[] sz, double[] sh,
                                ThreadLocalRandom live, Particle.DustOptions glint) {
        if (live.nextInt(100) >= 35) return;
        int i = 2 + live.nextInt(SEGMENTS - 3);
        double side = live.nextBoolean() ? 1 : -1;
        double x = sx[i] + rx * sh[i] * side;
        double y = sy[i];
        double z = sz[i] + rz * sh[i] * side;
        for (int step = 0; step < 5; step++) {
            x += live.nextDouble(-0.25, 0.25) + rx * side * 0.1;
            y += live.nextDouble(-0.25, 0.25);
            z += live.nextDouble(-0.25, 0.25) + rz * side * 0.1;
            w.spawnParticle(Particle.DUST, new Location(w, x, y, z), 1, 0, 0, 0, 0, glint, true);
        }
    }
}
