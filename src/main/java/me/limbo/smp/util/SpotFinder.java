package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Finds random spots anywhere inside the world border: solid ground, open sky, 2 blocks of headroom. */
public final class SpotFinder {
    private SpotFinder() {}

    /** Calls `done` (on the main thread) with up to `count` spots; fewer if it ran out of attempts. */
    public static void find(LimboPlugin plugin, World world, int count, int maxAttempts, Consumer<List<Location>> done) {
        attempt(plugin, world, count, maxAttempts, 0, new ArrayList<>(), done);
    }

    private static void attempt(LimboPlugin plugin, World world, int count, int maxAttempts, int attempts,
                                List<Location> found, Consumer<List<Location>> done) {
        if (found.size() >= count || attempts >= maxAttempts) {
            done.accept(found);
            return;
        }
        WorldBorder border = world.getWorldBorder();
        double half = Math.max(1.0, border.getSize() / 2.0 - 16.0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = (int) Math.floor(border.getCenter().getX() + r.nextDouble(-half, half));
        int z = (int) Math.floor(border.getCenter().getZ() + r.nextDouble(-half, half));

        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err == null) {
                        Location spot = evaluate(world, x, z);
                        if (spot != null) found.add(spot);
                    }
                    attempt(plugin, world, count, maxAttempts, attempts + 1, found, done);
                }));
    }

    private static Location evaluate(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= w.getMinHeight()) return null;
        Block ground = w.getBlockAt(x, y, z);
        Material gt = ground.getType();
        if (!gt.isSolid() || ground.isLiquid() || gt == Material.MAGMA_BLOCK || gt == Material.CACTUS
                || gt == Material.CAMPFIRE || gt == Material.SOUL_CAMPFIRE) return null;
        for (int i = 1; i <= 2; i++) {
            Block b = w.getBlockAt(x, y + i, z);
            if (!b.isPassable() || b.isLiquid()) return null;
        }
        if (w.getBlockAt(x, y + 1, z).getLightFromSky() < 15) return null; // open sky
        Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
        return w.getWorldBorder().isInside(loc) ? loc : null;
    }
}
