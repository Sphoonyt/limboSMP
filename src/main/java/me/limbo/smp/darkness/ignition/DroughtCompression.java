package me.limbo.smp.darkness.ignition;

import me.limbo.smp.util.Feedback;
import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * R - ring on the spot where you cast it for 3s (it stays put). Water inside evaporates for good (it sizzles);
 * enemy players lose a little hunger each second (a third of what it used to: 4 points over the 3 seconds). 70s cooldown.
 */
public final class DroughtCompression extends Channel {
    private static final double RADIUS = 7.0;
    private static final int DURATION_TICKS = 60;  // 3s
    private static final int[] HUNGER_LOSS = {1, 1, 2}; // 4 food points in total over the three drains
    private static final int MAX_SIZZLES_PER_PASS = 4;
    private static final Set<Material> WATER_THINGS = EnumSet.of(
            Material.WATER, Material.BUBBLE_COLUMN, Material.KELP, Material.KELP_PLANT,
            Material.SEAGRASS, Material.TALL_SEAGRASS);

    private final Set<Cast> casts = new HashSet<>();

    public DroughtCompression() {
        super("drought_compression", "Drought Compression",
                "Dries water and drains enemy hunger.",
                ChannelInput.R, 70_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        Cast c = new Cast(p);
        casts.add(c);
        c.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        World w = p.getWorld();
        me.limbo.smp.util.Sfx.play(p.getLocation(), org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f, me.limbo.smp.util.Sfx.Theme.IGNITION);
        me.limbo.smp.util.Sfx.add(p.getLocation(), org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        return true;
    }

    @Override
    public void onShutdown() {
        for (Cast c : new ArrayList<>(casts)) c.finish();
    }

    private final class Cast extends BukkitRunnable {
        private final Player p;
        private final Location origin;
        private int ticks = 0;
        private int pass = 0;
        private boolean done = false;

        Cast(Player p) {
            this.p = p;
            this.origin = p.getLocation().clone(); // the ring stays where it was cast
        }

        @Override
        public void run() {
            if (!p.isOnline() || ticks >= DURATION_TICKS) {
                finish();
                return;
            }
            Location c = origin;
            drawRing(c);
            evaporate(c);
            if (ticks % 20 == 0) drain(c, HUNGER_LOSS[Math.min(pass++, HUNGER_LOSS.length - 1)]);
            ticks += 2;
        }

        private void drawRing(Location c) {
            World w = c.getWorld();
            Location base = c.clone().add(0, 0.15, 0);
            ParticleUtil.ring(base, RADIUS, 56, IgnitionDarkness.COLOR);
            ParticleUtil.ring(base.clone().add(0, 1.2, 0), RADIUS, 40, IgnitionDarkness.COLOR);
            for (int i = 0; i < 56; i += 3) {
                double a = 2 * Math.PI * i / 56 + ticks * 0.05;
                Location l = base.clone().add(Math.cos(a) * RADIUS, 0, Math.sin(a) * RADIUS);
                w.spawnParticle(Particle.FLAME, l, 1, 0, 0.1, 0, 0.01);
            }
            ParticleUtil.risingMotes(base, IgnitionDarkness.COLOR, 3, RADIUS * 0.8);
        }

        /** Removes water for good; each pass plays a few sizzles + steam at the evaporated spots. */
        private void evaporate(Location c) {
            World w = c.getWorld();
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
            int r = (int) Math.ceil(RADIUS);
            int puffs = 0, sizzles = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) continue;
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Block b = w.getBlockAt(x, y, z);
                        boolean removed = false;
                        if (WATER_THINGS.contains(b.getType())) {
                            b.setType(Material.AIR, false);
                            removed = true;
                        } else if (b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) {
                            wl.setWaterlogged(false);
                            b.setBlockData(wl, false);
                            removed = true;
                        }
                        if (!removed) continue;
                        Location at = b.getLocation().add(0.5, 0.5, 0.5);
                        if (puffs++ < 14) w.spawnParticle(Particle.SMOKE, at, 1, 0.2, 0.2, 0.2, 0.03);
                        if (sizzles < MAX_SIZZLES_PER_PASS && rnd.nextInt(6) == 0) {
                            sizzles++;
                            w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f + rnd.nextFloat() * 0.6f);
                        }
                    }
                }
            }
        }

        private void drain(Location c, int loss) {
            for (Player t : c.getWorld().getPlayers()) {
                if (t.getLocation().distanceSquared(c) > RADIUS * RADIUS) continue;
                if (!Targeting.canHarm(p, t)) continue;
                t.setSaturation(Math.max(0f, t.getSaturation() - loss));
                t.setFoodLevel(Math.max(0, t.getFoodLevel() - loss));
                Feedback.affected(t, "Drought Compression", p);
                ParticleUtil.blissDust(t.getLocation().add(0, 1, 0), IgnitionDarkness.COLOR, 8, 0.4);
            }
        }

        void finish() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) {}
            casts.remove(this);
        }
    }
}
