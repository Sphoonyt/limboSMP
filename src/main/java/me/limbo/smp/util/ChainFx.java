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
    private static final double SPACING = 0.8;

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
        Vector3f scale = new Vector3f(0.9f, (float) (step * 1.2), 0.9f);
        for (int i = 0; i < n; i++) {
            Location at = from.clone().add(dir.clone().multiply(step * (i + 0.5)));
            Quaternionf roll = new Quaternionf().rotateY(i % 2 == 0 ? 0f : (float) (Math.PI / 4)); // alternate links
            ItemDisplay d = links.get(i);
            FlatFx.move(d, at, 1);
            FlatFx.shape(d, scale, align, roll, 1);
        }
    }

    public void clear() {
        for (ItemDisplay d : links) FlatFx.remove(d);
        links.clear();
    }
}
