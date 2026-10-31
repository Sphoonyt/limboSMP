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
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * R - a slow black hole flies forward for 4s, growing as it goes. Every second it pulls nearby enemies in,
 * and every tick it drags along anything close enough to it. When the flight ends it expands rapidly and
 * deals every enemy it reaches 2 hearts of true damage and gives them Slowness III + Darkness for 4s. Clears cobwebs. 90s cooldown.
 */
public final class BlackHole extends Channel {
    private static final int FLY_TICKS = 80;          // 4s
    private static final double START_R = 0.8;
    private static final double END_R = 3.5;
    private static final double FINAL_R = 7.0;
    private static final double SPEED = 0.35;         // blocks per tick (slow)
    private static final int EXPAND_TICKS = 10;
    private static final int EFFECT_TICKS = 80;       // 4s
    private static final double PULL_IMPULSE = 0.9;
    private static final double DRAG_EXTRA = 1.2;
    private static final double DETONATION_DAMAGE = 4.0; // 2 hearts when it expands

    private final Set<Cast> casts = new HashSet<>();

    public BlackHole() {
        super("black_hole", "Black Hole", "Slow black hole that drags enemies in.", ChannelInput.R, 90_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Cast c = new Cast(ctx.player());
        casts.add(c);
        c.runTaskTimer(LimboPlugin.get(), 0L, 1L);
        World w = ctx.player().getWorld();
        w.playSound(ctx.player().getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.4f);
        ctx.player().sendActionBar(Msg.parse("<#9646e6>Black Hole"));
        return true;
    }

    @Override
    public void onShutdown() {
        for (Cast c : new ArrayList<>(casts)) c.finish();
    }

    private final class Cast extends BukkitRunnable {
        private final Player p;
        private final World w;
        private final Location pos;
        private final Vector dir;
        private final Set<UUID> afflicted = new HashSet<>();
        private int ticks = 0;
        private int expandTicks = 0;
        private boolean expanding = false;
        private boolean done = false;

        Cast(Player p) {
            this.p = p;
            this.w = p.getWorld();
            Location eye = p.getEyeLocation();
            this.dir = eye.getDirection().normalize();
            this.pos = eye.clone().add(dir.clone().multiply(1.8));
        }

        @Override
        public void run() {
            if (!p.isOnline() || p.isDead() || p.getWorld() != w) {
                finish();
                return;
            }
            if (!expanding) {
                ticks++;
                double r = START_R + (END_R - START_R) * ticks / FLY_TICKS;
                Vector step = dir.clone().multiply(SPEED);
                Location next = pos.clone().add(step);
                if (next.getBlock().isPassable()) pos.add(step); else step = new Vector(0, 0, 0); // stalls at walls
                render(r);
                if (ticks % 2 == 0) clearWebs(r);
                pullAndDrag(r, step);
                if (ticks % 20 == 0) w.playSound(pos, Sound.BLOCK_PORTAL_AMBIENT, 1.2f, 0.5f);
                if (ticks >= FLY_TICKS) {
                    expanding = true;
                    w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
                    w.playSound(pos, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.5f);
                }
            } else {
                expandTicks++;
                double r = END_R + (FINAL_R - END_R) * expandTicks / EXPAND_TICKS;
                renderShell(r);
                clearWebs(r);
                for (LivingEntity e : enemiesWithin(r)) {
                    if (!afflicted.add(e.getUniqueId())) continue;
                    Feedback.affected(e, "Black Hole", p);
                    TrueDamage.apply(p, e, DETONATION_DAMAGE);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_TICKS, 2, false, true, true));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, EFFECT_TICKS, 0, false, true, true));
                }
                if (expandTicks >= EXPAND_TICKS) finish();
            }
        }

        void finish() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) {}
            casts.remove(this);
        }

        // ---- gameplay ----

        private List<LivingEntity> enemiesWithin(double range) {
            List<LivingEntity> out = new ArrayList<>();
            Vector c = pos.toVector();
            for (LivingEntity e : w.getNearbyLivingEntities(pos, range)) {
                if (e.equals(p) || !Targeting.canHarm(p, e) || !Targeting.isEnemy(e)) continue;
                if (e.getBoundingBox().getCenter().distanceSquared(c) > range * range) continue;
                out.add(e);
            }
            return out;
        }

        /** Per-second pull toward the hole; every tick, anything close enough is carried along with it. */
        private void pullAndDrag(double r, Vector step) {
            double pullRange = 5 + r * 2;
            for (LivingEntity e : enemiesWithin(pullRange)) {
                Feedback.affected(e, "Black Hole", p);
                Vector toHole = pos.toVector().subtract(e.getBoundingBox().getCenter());
                double dist = toHole.length();
                if (dist < 0.01) continue;
                if (dist <= r + DRAG_EXTRA) {
                    // drag: match the hole's motion and ease toward its centre
                    e.setVelocity(step.clone().add(toHole.clone().multiply(0.12)));
                } else if (ticks % 20 == 0) {
                    double strength = PULL_IMPULSE * (1.0 - dist / pullRange) + 0.3;
                    Vector pull = toHole.normalize().multiply(strength);
                    pull.setY(pull.getY() * 0.6);
                    e.setVelocity(e.getVelocity().add(pull));
                }
            }
        }

        private void clearWebs(double r) {
            int ri = (int) Math.ceil(r);
            int cx = pos.getBlockX(), cy = pos.getBlockY(), cz = pos.getBlockZ();
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dy = -ri; dy <= ri; dy++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r) continue;
                        int x = cx + dx, z = cz + dz;
                        if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Block b = w.getBlockAt(x, cy + dy, z);
                        if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
                    }
                }
            }
        }

        // ---- visuals ----

        private void render(double r) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Particle.DustOptions core = new Particle.DustOptions(ParticleUtil.VOID, (float) Math.min(4.0, 1.5 + r * 0.6));
            for (int i = 0; i < 18; i++) {
                Vector v = randomUnit(rnd).multiply(r * 0.9 * Math.cbrt(rnd.nextDouble()));
                w.spawnParticle(Particle.DUST, pos.clone().add(v), 1, 0, 0, 0, 0, core, true);
            }
            // spinning accretion ring
            Vector axis = new Vector(0.35, 1, 0.2).normalize();
            Vector u = axis.clone().crossProduct(new Vector(1, 0, 0)).normalize();
            Vector v2 = axis.clone().crossProduct(u).normalize();
            Particle.DustTransition rim = new Particle.DustTransition(SingularityDarkness.LIGHT, ParticleUtil.VOID, 1.2f);
            double spin = ticks * 0.25;
            for (int i = 0; i < 24; i++) {
                double a = spin + 2 * Math.PI * i / 24;
                Vector off = u.clone().multiply(Math.cos(a) * r * 1.5).add(v2.clone().multiply(Math.sin(a) * r * 1.5));
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pos.clone().add(off), 1, 0, 0, 0, 0, rim, true);
            }
            // matter spiralling in
            for (int i = 0; i < 4; i++) {
                Vector off = randomUnit(rnd).multiply(r * 2.2);
                w.spawnParticle(Particle.REVERSE_PORTAL, pos.clone().add(off), 1, 0, 0, 0, 0.2, null, true);
            }
        }

        /** Rapidly expanding shell of dust. */
        private void renderShell(double r) {
            Particle.DustTransition shell = new Particle.DustTransition(SingularityDarkness.COLOR, ParticleUtil.VOID, 1.5f);
            int n = 60;
            double golden = Math.PI * (3 - Math.sqrt(5));
            for (int i = 0; i < n; i++) {
                double y = 1 - 2.0 * i / (n - 1);
                double rad = Math.sqrt(1 - y * y);
                double th = golden * i;
                Vector off = new Vector(Math.cos(th) * rad, y, Math.sin(th) * rad).multiply(r);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pos.clone().add(off), 1, 0, 0, 0, 0, shell, true);
            }
            w.spawnParticle(Particle.DUST, pos, 8, r * 0.3, r * 0.3, r * 0.3, 0,
                    new Particle.DustOptions(ParticleUtil.VOID, 3.0f), true);
        }

        private Vector randomUnit(ThreadLocalRandom rnd) {
            double z = rnd.nextDouble(-1, 1);
            double a = rnd.nextDouble(Math.PI * 2);
            double rad = Math.sqrt(1 - z * z);
            return new Vector(rad * Math.cos(a), z, rad * Math.sin(a));
        }
    }
}
