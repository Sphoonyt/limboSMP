package me.limbo.smp.darkness.chimera;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.ChimeraDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.Sfx;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L - pick a point under your crosshair (up to 30 blocks). A 4 block radius circle marks the ground and after
 * 1 second spikes burst out of it. Enemies caught are thrown upwards, take 2 hearts of true damage and have their
 * darkness (passives, flux and channels) disabled for 8 seconds. 45s cooldown.
 */
public final class SilencingMaw extends Channel {
    private static final String NAME = "Silencing Maw";
    private static final double RANGE = 30.0;
    private static final double RADIUS = 4.0;
    private static final int DELAY_TICKS = 20;
    private static final double DAMAGE = 4.0;        // 2 hearts
    private static final int SILENCE_TICKS = 160;    // 8s

    private static final Color BRIGHT = Color.fromRGB(150, 255, 160);
    private static final Color DARK = Color.fromRGB(8, 30, 12);

    public SilencingMaw() {
        super("silencing_maw", NAME, "Spikes erupt at your crosshair, launching enemies, hurting them and silencing their darkness for 8s.",
                ChannelInput.L, 45_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        Location target = findTarget(p);
        if (target == null) {
            return false;
        }
        World w = target.getWorld();
        Sfx.play(target, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.7f, Sfx.Theme.CHIMERA);

        // the spike layout is chosen now so the telegraph can hint at it
        List<Location> spikes = layout(target);

        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                if (t >= DELAY_TICKS) { cancel(); erupt(p, target, spikes); return; }
                telegraph(w, target, spikes, t);
                if (t % 6 == 0) w.playSound(target, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.5f + t / 40f);
                t += 2;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        return true;
    }

    // ------------------------------------------------------------ targeting

    private Location findTarget(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = w.rayTrace(eye, dir, RANGE, FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity && !e.getUniqueId().equals(p.getUniqueId()));
        Location spot;
        if (hit != null) {
            spot = hit.getHitEntity() != null ? hit.getHitEntity().getLocation() : hit.getHitPosition().toLocation(w);
        } else {
            spot = eye.clone().add(dir.clone().multiply(RANGE));
        }
        // snap to the ground below that point
        RayTraceResult down = w.rayTraceBlocks(spot.clone().add(0, 1.0, 0), new Vector(0, -1, 0), 40, FluidCollisionMode.NEVER, true);
        if (down == null) return null;
        return down.getHitPosition().toLocation(w).add(0, 0.05, 0);
    }

    private List<Location> layout(Location c) {
        World w = c.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Location> out = new ArrayList<>();
        out.add(c.clone()); // always one in the middle
        for (int i = 0; i < 30; i++) {
            double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * (RADIUS - 0.3);
            Location l = c.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            RayTraceResult g = w.rayTraceBlocks(l.clone().add(0, 2, 0), new Vector(0, -1, 0), 6, FluidCollisionMode.NEVER, true);
            if (g != null) l.setY(g.getHitPosition().getY() + 0.05);
            boolean tooClose = false;
            for (Location o : out) if (o.distanceSquared(l) < 0.55) { tooClose = true; break; }
            if (!tooClose) out.add(l);
        }
        return out;
    }

    // ------------------------------------------------------------ visuals

    private void telegraph(World w, Location c, List<Location> spikes, int t) {
        double progress = t / (double) DELAY_TICKS;
        Particle.DustTransition ring = new Particle.DustTransition(BRIGHT, ChimeraDarkness.COLOR, 1.4f);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * RADIUS, 0.1, Math.sin(a) * RADIUS), 1, 0, 0, 0, 0, ring, true);
        }
        double spin = t * 0.2;
        for (int i = 0; i < 12; i++) { // jaw teeth turning inside the ring
            double a = Math.PI * 2 * i / 12 + spin;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * RADIUS * 0.7, 0.12, Math.sin(a) * RADIUS * 0.7), 1, 0, 0, 0, 0,
                    new Particle.DustTransition(ChimeraDarkness.COLOR, DARK, 1.2f), true);
        }
        for (Location s : spikes) { // little tips pushing up through the ground, growing
            if (ThreadLocalRandom.current().nextDouble() > 0.35) continue;
            double h = 0.1 + progress * 0.7;
            for (double y = 0; y <= h; y += 0.25) {
                w.spawnParticle(Particle.DUST, s.clone().add(0, y, 0), 1, 0.02, 0, 0.02, 0, new Particle.DustOptions(ChimeraDarkness.COLOR, 1.0f), true);
            }
        }
        w.spawnParticle(Particle.BLOCK, c.clone().add(0, 0.1, 0), 3, RADIUS * 0.5, 0, RADIUS * 0.5, 0.02,
                c.clone().subtract(0, 0.5, 0).getBlock().getBlockData(), true);
    }

    private void drawSpike(World w, Location base, double height) {
        for (double y = 0; y <= height; y += 0.2) {
            double taper = 1.0 - y / height;
            float size = (float) (0.8 + taper * 1.2);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(0, y, 0), 1, 0.03 * taper, 0, 0.03 * taper, 0,
                    new Particle.DustTransition(y > height * 0.7 ? BRIGHT : ChimeraDarkness.COLOR, DARK, size), true);
        }
        w.spawnParticle(Particle.END_ROD, base.clone().add(0, height, 0), 1, 0, 0, 0, 0, null, true);
    }

    // ------------------------------------------------------------ the eruption

    private void erupt(Player p, Location c, List<Location> spikes) {
        World w = c.getWorld();
        Sfx.play(c, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.3f, 0.7f, Sfx.Theme.CHIMERA);
        w.playSound(c, Sound.BLOCK_BIG_DRIPLEAF_TILT_UP, 1f, 0.5f);
        w.playSound(c, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 0.6f);

        // spikes rise over 4 ticks, then hold a moment
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double[] heights = new double[spikes.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = 2.0 + r.nextDouble(2.2);
        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (t > 8) { cancel(); return; }
                double grow = t < 4 ? (t + 1) / 4.0 : 1.0;
                for (int i = 0; i < spikes.size(); i++) drawSpike(w, spikes.get(i), heights[i] * grow);
                if (t == 0) {
                    w.spawnParticle(Particle.BLOCK, c.clone().add(0, 0.3, 0), 60, RADIUS * 0.6, 0.2, RADIUS * 0.6, 0.1,
                            c.clone().subtract(0, 0.5, 0).getBlock().getBlockData(), true);
                    for (int i = 0; i < 3; i++) {
                        double rad = RADIUS * (0.5 + 0.25 * i);
                        for (int k = 0; k < 40; k++) {
                            double a = Math.PI * 2 * k / 40;
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * rad, 0.2, Math.sin(a) * rad), 1, 0, 0, 0, 0,
                                    new Particle.DustTransition(BRIGHT, ChimeraDarkness.COLOR, 1.6f), true);
                        }
                    }
                }
                t++;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);

        // everyone inside the circle
        LimboPlugin pl = LimboPlugin.get();
        for (LivingEntity e : Targeting.nearby(p, c, RADIUS + 1.5)) {
            double dx = e.getLocation().getX() - c.getX(), dz = e.getLocation().getZ() - c.getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            if (e.getLocation().getY() < c.getY() - 1.5 || e.getLocation().getY() > c.getY() + 3.5) continue;
            if (!Targeting.isEnemy(e)) continue;

            boolean hurt = TrueDamage.apply(p, e, DAMAGE);
            if (!hurt || e.isDead()) continue;
            e.setVelocity(new Vector(0, 1.05, 0));
            Feedback.affected(e, NAME, p);
            if (e instanceof Player v) {
                pl.status().silence(v, SILENCE_TICKS);
                pl.screenFx().show(v, me.limbo.smp.status.ScreenFx.Fx.SILENCE, SILENCE_TICKS);
            }
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, e.getLocation().add(0, 1, 0), 24, 0.4, 0.7, 0.4, 0,
                    new Particle.DustTransition(BRIGHT, ChimeraDarkness.COLOR, 1.5f), true);
        }
    }
}
