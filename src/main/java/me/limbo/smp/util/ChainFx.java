package me.limbo.smp.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A chain made of flat link models laid between two points. Call update() every tick with the current end points,
 * clear() when it's over. Links are added and removed as the distance changes.
 */
public final class ChainFx {
    private static final double SPACING = 0.5;
    private int tick;

    private final List<ItemDisplay> links = new ArrayList<>();

    public void update(Location from, Location to) {
        World w = from.getWorld();
        if (w == null || to.getWorld() != w) { clear(); return; }
        Vector v = to.toVector().subtract(from.toVector());
        double len = v.length();
        if (len < 0.5) { clear(); return; }
        Vector dir = v.clone().multiply(1.0 / len);
        int n = Math.max(1, (int) Math.round(len / SPACING));
        while (links.size() < n) links.add(FlatFx.spawn(from, FlatFx.LINK, Display.Billboard.FIXED));
        while (links.size() > n) FlatFx.remove(links.remove(links.size() - 1));

        double step = len / n;
        Quaternionf align = FlatFx.yTo(dir);
        Vector3f scale = new Vector3f(0.42f, (float) (step * 1.15), 0.42f);
        tick++;
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1e-4) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.clone().crossProduct(dir).normalize();
        for (int i = 0; i < n; i++) {
            Location at = from.clone().add(dir.clone().multiply(step * (i + 0.5)));
            Quaternionf roll = new Quaternionf().rotateY(i % 2 == 0 ? 0f : (float) (Math.PI / 4)); // alternate links
            ItemDisplay d = links.get(i);
            FlatFx.move(d, at, 1);
            FlatFx.shape(d, scale, align, roll, 1);
            // sparks wrapping the chain: two motes per link, orbiting it, plus the odd ember drifting off
            double ang = tick * 0.35 + i * 1.3;
            for (int k = 0; k < 2; k++) {
                double a = ang + k * Math.PI;
                Location pl = at.clone().add(side.clone().multiply(Math.cos(a) * 0.28)).add(up.clone().multiply(Math.sin(a) * 0.28));
                w.spawnParticle(org.bukkit.Particle.DUST, pl, 1, 0, 0, 0, 0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(176, 24, 48), 0.7f), true);
            }
            if (tick % 5 == i % 5) w.spawnParticle(org.bukkit.Particle.SMALL_FLAME, at, 1, 0.15, 0.15, 0.15, 0.01, null, true);
        }
    }

    public void clear() {
        for (ItemDisplay d : links) FlatFx.remove(d);
        links.clear();
    }
}
