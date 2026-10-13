package me.limbo.smp.util;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * "Bliss dust" style visuals: soft drifting dust that fades from void-black into a
 * darkness accent colour, with sparse rising sparkles.
 */
public final class ParticleUtil {
    public static final Color VOID = Color.fromRGB(10, 8, 16);
    public static final Color RIFT_ACCENT = Color.fromRGB(150, 90, 255);

    private ParticleUtil() {}

    /** The core look: black -> accent dust cloud, dark dust, accent dust, sparkles, void wisps. */
    public static void blissDust(Location loc, Color accent, int count, double spread) {
        World w = loc.getWorld();
        if (w == null) return;
        Particle.DustTransition fade = new Particle.DustTransition(VOID, accent, 1.3f);
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, count, spread, spread, spread, 0, fade, true);
        w.spawnParticle(Particle.DUST, loc, Math.max(1, count / 2), spread, spread, spread, 0,
                new Particle.DustOptions(VOID, 1.5f), true);
        w.spawnParticle(Particle.DUST, loc, Math.max(1, count / 3), spread, spread, spread, 0,
                new Particle.DustOptions(accent, 1.0f), true);
        w.spawnParticle(Particle.END_ROD, loc, Math.max(1, count / 6),
                spread * 0.6, spread * 0.6, spread * 0.6, 0.015, null, true);
        w.spawnParticle(Particle.REVERSE_PORTAL, loc, Math.max(1, count / 4),
                spread * 0.5, spread * 0.5, spread * 0.5, 0.02, null, true);
    }

    /** Sparkles that drift upward (count=0 makes offset act as velocity). */
    public static void risingMotes(Location base, Color accent, int motes, double radius) {
        World w = base.getWorld();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Particle.DustTransition fade = new Particle.DustTransition(VOID, accent, 1.0f);
        for (int i = 0; i < motes; i++) {
            Location l = base.clone().add(r.nextDouble(-radius, radius), r.nextDouble(0, 0.4), r.nextDouble(-radius, radius));
            w.spawnParticle(Particle.END_ROD, l, 0, 0, 1, 0, 0.03 + r.nextDouble(0.03), null, true);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, fade, true);
        }
    }

    public static void ring(Location center, double radius, int points, Color accent) {
        World w = center.getWorld();
        if (w == null) return;
        Particle.DustTransition fade = new Particle.DustTransition(VOID, accent, 1.2f);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location l = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, fade, true);
        }
    }

    public static void burst(Location loc, Color accent, int count) {
        blissDust(loc, accent, count, 0.6);
        ring(loc, 1.2, 24, accent);
        risingMotes(loc, accent, Math.max(4, count / 4), 0.8);
    }

    /** Helix that follows a player (call every few ticks). */
    public static void aura(Player p, Color accent) {
        double t = Bukkit.getCurrentTick() * 0.25;
        double y = (Bukkit.getCurrentTick() % 40) / 20.0;
        for (int i = 0; i < 2; i++) {
            double a = t + i * Math.PI;
            Location l = p.getLocation().add(Math.cos(a) * 0.7, y, Math.sin(a) * 0.7);
            blissDust(l, accent, 2, 0.05);
        }
    }

    public static void riftAmbience(Location center) {
        blissDust(center, RIFT_ACCENT, 6, 0.45);
        risingMotes(center, RIFT_ACCENT, 2, 0.5);
    }
}
