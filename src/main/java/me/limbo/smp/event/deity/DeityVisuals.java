package me.limbo.smp.event.deity;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/**
 * All the particle drawing for the deity. Pure functions of (position, tick), no state, so the look can be
 * changed freely without touching the logic. Local coordinates: x = right, y = up, z = forward (the way it faces).
 */
final class DeityVisuals {
    static final Color VOID = Color.fromRGB(10, 8, 16);
    static final Color DEEP = Color.fromRGB(45, 12, 80);
    static final Color PURPLE = Color.fromRGB(155, 89, 255);
    static final Color LIGHT = Color.fromRGB(228, 205, 255);

    private DeityVisuals() {}

    /** Maps local coordinates to the world and draws there. */
    static final class Brush {
        final World w;
        final Location base;
        final double cos, sin, scale;
        final double jx, jz;

        Brush(Location base, double yawDegrees, double scale, double jitter) {
            this.w = base.getWorld();
            this.base = base;
            double yaw = Math.toRadians(yawDegrees);
            this.cos = Math.cos(yaw);
            this.sin = Math.sin(yaw);
            this.scale = scale;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            this.jx = jitter == 0 ? 0 : r.nextDouble(-jitter, jitter);
            this.jz = jitter == 0 ? 0 : r.nextDouble(-jitter, jitter);
        }

        Location at(double x, double y, double z) {
            double lx = x * scale, ly = y * scale, lz = z * scale;
            return new Location(w,
                    base.getX() + jx + lx * -cos + lz * -sin,
                    base.getY() + ly,
                    base.getZ() + jz + lx * -sin + lz * cos);
        }

        /** Soft dust that fades between two colours. */
        void dust(double x, double y, double z, Color from, Color to, float size) {
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, at(x, y, z), 1, 0, 0, 0, 0,
                    new Particle.DustTransition(from, to, (float) Math.min(4f, size * scale)), true);
        }

        void dark(double x, double y, double z, float size) {
            w.spawnParticle(Particle.DUST, at(x, y, z), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(VOID, (float) Math.min(4f, size * scale)), true);
        }

        void spark(Particle p, double x, double y, double z) {
            w.spawnParticle(p, at(x, y, z), 1, 0, 0, 0, 0, null, true);
        }

        /** A particle that drifts in a direction (count 0 makes the offset the direction). */
        void drift(Particle p, double x, double y, double z, double dx, double dy, double dz, double speed) {
            w.spawnParticle(p, at(x, y, z), 0, dx, dy, dz, speed, null, true);
        }
    }

    // ------------------------------------------------------------ dormant: a cocoon that breathes

    /**
     * The sleeping deity: a tall black cocoon with faint violet cracks that pulse like a heartbeat.
     * @param crack 0 = barely cracked, 1 = about to break open (used while it is waking up)
     */
    static void cocoon(Brush b, long t, double crack) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double rx = 2.6, ry = 4.8;
        double beat = Math.pow(Math.max(0, Math.sin(t * 0.06)), 8);

        for (int i = 0; i < 38; i++) { // the shell
            double u = r.nextDouble(Math.PI * 2), v = Math.acos(2 * r.nextDouble() - 1);
            double x = rx * Math.sin(v) * Math.cos(u), y = ry + ry * Math.cos(v), z = rx * Math.sin(v) * Math.sin(u);
            if (i % 4 == 0) b.dust(x, y, z, VOID, DEEP, 2.0f);
            else b.dark(x, y, z, 2.2f);
        }

        double reach = 0.35 + 0.65 * crack;
        for (int k = 0; k < 6; k++) { // cracks, longer and brighter as it wakes
            double a = Math.toRadians(k * 60 + 17);
            for (double y = 0.4; y < 9.2 * reach; y += 0.55) {
                double rad = rx * Math.sqrt(Math.max(0, 1 - Math.pow((y - ry) / ry, 2))) + 0.1;
                double wob = Math.sin(y * 2.1 + k * 3.0) * 0.3;
                double cx = Math.cos(a) * rad + wob, cz = Math.sin(a) * rad;
                if (beat > 0.25 || crack > 0) b.dust(cx, y, cz, PURPLE, LIGHT, (float) (0.9 + beat + crack));
                if (crack > 0.5 && r.nextDouble() < 0.25) { // light leaking out
                    b.drift(Particle.END_ROD, cx, y, cz, Math.cos(a), 0.1, Math.sin(a), 0.08 + 0.1 * crack);
                }
            }
        }
        for (int i = 0; i < 4; i++) { // the ground drinks the dark
            double a = r.nextDouble(Math.PI * 2), d = 3.5 + r.nextDouble(2.5);
            b.drift(Particle.REVERSE_PORTAL, Math.cos(a) * d, 0.2, Math.sin(a) * d, -Math.cos(a), 0.3, -Math.sin(a), 0.4);
        }
    }

    // ------------------------------------------------------------ awake: the full depiction

    /**
     * The awake deity: a hooded, crowned figure about 12 blocks tall in a flowing robe, arms lifted, eyes lit,
     * a halo behind its head, shards orbiting it and a rune ring around its feet.
     * @param glow 0 = normal, up to 1 = overcharged (the finale)
     */
    static void figure(Brush b, long t, double glow) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double time = t * 0.1;

        // robe: wavy strands from the hem up to the waist
        for (int k = 0; k < 10; k++) {
            double a = Math.PI * 2 * k / 10 + time * 0.05;
            for (double h = 0.2; h < 6.2; h += 0.75) {
                double rad = 3.4 - h * 0.3 + Math.sin(time + h * 0.9 + k) * 0.25;
                double x = Math.cos(a) * rad, z = Math.sin(a) * rad;
                if (h < 1.5) b.dust(x, h, z, DEEP, PURPLE, 2.2f);
                else b.dark(x, h, z, 2.3f);
            }
        }
        // torso
        for (int i = 0; i < 22; i++) {
            double a = r.nextDouble(Math.PI * 2), h = 5.8 + r.nextDouble(3.4);
            b.dark(Math.cos(a) * 1.15, h, Math.sin(a) * 0.75, 2.1f);
        }
        // shoulders
        for (double x = -2.3; x <= 2.3; x += 0.5) b.dust(x, 9.3 + Math.sin(time + x) * 0.05, 0, DEEP, PURPLE, 1.8f);
        // arms and hands, lifted and reaching
        for (int side = -1; side <= 1; side += 2) {
            for (double f = 0; f <= 1; f += 0.125) {
                double x = side * (2.3 + 2.4 * f), y = 9.3 - 1.4 * Math.sin(f * Math.PI * 0.9) + 1.5 * f * f, z = 1.6 * f;
                b.dark(x, y, z, 1.8f);
            }
            double hx = side * 4.7, hy = 9.4 + Math.sin(time) * 0.15, hz = 1.6;
            for (int fing = 0; fing < 5; fing++) {
                double spread = (fing - 2) * 0.28;
                b.spark(Particle.END_ROD, hx + side * 0.2 + spread, hy + 0.5 + Math.abs(spread) * -0.4, hz + 0.3);
            }
            b.dust(hx, hy, hz, LIGHT, PURPLE, 2.0f);
        }
        // head: hood, face void, eyes
        for (int i = 0; i < 18; i++) {
            double u = r.nextDouble(Math.PI * 2), v = Math.acos(2 * r.nextDouble() - 1);
            b.dark(0.85 * Math.sin(v) * Math.cos(u), 10.4 + 0.95 * Math.cos(v), 0.85 * Math.sin(v) * Math.sin(u) - 0.1, 1.6f);
        }
        for (int side = -1; side <= 1; side += 2) {
            b.spark(Particle.END_ROD, side * 0.32, 10.5, 0.8);
            b.dust(side * 0.32, 10.5, 0.82, LIGHT, PURPLE, 1.2f);
        }
        // crown of spikes + halo
        for (int s = 0; s < 7; s++) {
            double a = Math.PI * 2 * s / 7, len = 1.6 + (s % 2) * 0.9;
            for (int i = 0; i <= 3; i++) {
                double f = i / 3.0;
                b.dust(Math.cos(a) * (0.9 + 0.5 * f), 11.2 + len * f, Math.sin(a) * (0.9 + 0.5 * f), PURPLE, LIGHT, 1.1f);
            }
        }
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36 + time * 0.2;
            double rad = 2.5 + glow * 0.8;
            b.dust(Math.cos(a) * rad, 11.6 + Math.sin(a) * rad, -0.9, i % 3 == 0 ? LIGHT : PURPLE, VOID, 1.3f);
        }
        // shards orbiting
        for (int i = 0; i < 6; i++) {
            double a = time * 0.5 + Math.PI * 2 * i / 6;
            double rad = 7.5 + glow * 2, y = 7 + Math.sin(time + i * 1.7) * 2.2;
            b.spark(Particle.END_ROD, Math.cos(a) * rad, y, Math.sin(a) * rad);
            b.dust(Math.cos(a) * rad, y - 0.2, Math.sin(a) * rad, PURPLE, VOID, 1.8f);
            b.dust(Math.cos(a - 0.12) * rad, y - 0.35, Math.sin(a - 0.12) * rad, PURPLE, VOID, 1.2f);
        }
        // rune ring on the ground, drawn as slowly turning dashes
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64;
            if (Math.sin(a * 12 + time * 0.5) < 0.1) continue;
            b.dust(Math.cos(a) * 6.5, 0.15, Math.sin(a) * 6.5, PURPLE, DEEP, 1.4f);
        }
        // a pillar of dark rising from its crown
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(1.4);
            b.drift(Particle.REVERSE_PORTAL, Math.cos(a) * d, 12 + r.nextDouble(2), Math.sin(a) * d, 0, 1, 0, 0.5);
        }
        if (glow > 0) { // overcharged: light pours off it
            for (int i = 0; i < (int) (14 * glow); i++) {
                double a = r.nextDouble(Math.PI * 2);
                b.drift(Particle.END_ROD, 0, 6 + r.nextDouble(6), 0, Math.cos(a), r.nextDouble(-0.2, 0.4), Math.sin(a), 0.35);
            }
        }
    }

    // ------------------------------------------------------------ effects

    /** A horizontal ring of dust (used for shockwaves). */
    static void ring(World w, Location c, double radius, int points, Color from, Color to, float size) {
        Particle.DustTransition d = new Particle.DustTransition(from, to, size);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, d, true);
        }
    }

    /** Random points on a sphere shell. */
    static void shell(World w, Location c, double radius, int points, Color from, Color to, float size) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Particle.DustTransition d = new Particle.DustTransition(from, to, size);
        for (int i = 0; i < points; i++) {
            double u = r.nextDouble(Math.PI * 2), v = Math.acos(2 * r.nextDouble() - 1);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                    c.clone().add(radius * Math.sin(v) * Math.cos(u), radius * Math.cos(v), radius * Math.sin(v) * Math.sin(u)),
                    1, 0, 0, 0, 0, d, true);
        }
    }

    /** A line of dust between two points. */
    static void line(World w, Location a, Location b, int points, Color from, Color to, float size) {
        Particle.DustTransition d = new Particle.DustTransition(from, to, size);
        for (int i = 0; i <= points; i++) {
            double f = i / (double) points;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                    new Location(w, a.getX() + (b.getX() - a.getX()) * f, a.getY() + (b.getY() - a.getY()) * f,
                            a.getZ() + (b.getZ() - a.getZ()) * f), 1, 0, 0, 0, 0, d, true);
        }
    }
}
