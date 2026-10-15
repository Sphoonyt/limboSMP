package me.limbo.smp.darkness.ignition;

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
 * R - ring around you for 5s (moves with you). Water inside evaporates for good (it sizzles);
 * enemy players lose 2 hunger bars every second. 70s cooldown.
 */
public final class DroughtCompression extends Channel {
    private static final double RADIUS = 7.0;
    private static final int DURATION_TICKS = 100;
    private static final int HUNGER_LOSS = 4; // 2 bars = 4 food points
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
        w.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        p.sendActionBar(Msg.parse("<#ff6e14>Drought Compression"));
        return true;
    }

    @Override
    public void onShutdown() {
        for (Cast c : new ArrayList<>(casts)) c.finish();
    }

    private final class Cast extends BukkitRunnable {
        private final Player p;
        private int ticks = 0;
        private boolean done = false;

        Cast(Player p) { this.p = p; }

        @Override
        public void run() {
            if (!p.isOnline() || p.isDead() || ticks >= DURATION_TICKS) {
                finish();
                return;
            }
            Location c = p.getLocation();
            drawRing(c);
            evaporate(c);
            if (ticks % 20 == 0) drain(c);
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
                        if (puffs++ < 14) w.spawnParticle(Particle.CLOUD, at, 1, 0.2, 0.2, 0.2, 0.03);
                        if (sizzles < MAX_SIZZLES_PER_PASS && rnd.nextInt(6) == 0) {
                            sizzles++;
                            w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f + rnd.nextFloat() * 0.6f);
                        }
                    }
                }
            }
        }

        private void drain(Location c) {
            for (Player t : c.getWorld().getPlayers()) {
                if (t.getLocation().distanceSquared(c) > RADIUS * RADIUS) continue;
                if (!Targeting.canHarm(p, t)) continue;
                t.setFoodLevel(Math.max(0, t.getFoodLevel() - HUNGER_LOSS));
                t.setSaturation(0f);
                ParticleUtil.blissDust(t.getLocation().add(0, 1, 0), IgnitionDarkness.COLOR, 8, 0.4);
                t.sendActionBar(Msg.parse("<#ff6e14>Parched..."));
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
