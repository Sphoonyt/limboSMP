package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A short sigil that flares at the caster's feet whenever they use an ability: a ring that expands outward,
 * turning rune dashes, and sparks that lift off it. Coloured by the player's darkness, so every ability reads
 * as "cast" at a glance (and other players can tell something just happened). About half a second long.
 */
public final class CastFx {
    private CastFx() {}

    public static void sigil(Player p, Color color) {
        World w = p.getWorld();
        Location c = p.getLocation().clone();
        Particle.DustTransition main = new Particle.DustTransition(color, Color.fromRGB(10, 8, 16), 1.3f);
        Particle.DustTransition bright = new Particle.DustTransition(Color.WHITE, color, 1.0f);

        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || t > 8) { cancel(); return; }
                double radius = 0.6 + t * 0.22;
                double spin = t * 0.5;
                int points = 24;
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius),
                            1, 0, 0, 0, 0, main, true);
                }
                for (int i = 0; i < 12; i++) { // runes turning against the ring
                    double a = Math.PI * 2 * i / 12 - spin;
                    if (i % 2 == 0) continue;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * radius * 0.6, 0.12, Math.sin(a) * radius * 0.6),
                            1, 0, 0, 0, 0, bright, true);
                }
                if (t % 2 == 0) {
                    w.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.2, 0), 2, radius * 0.5, 0.05, radius * 0.5, 0.06, null, true);
                }
                t++;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }
}
