package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Flat model effects. Every effect that is "a model" is an ItemDisplay showing one of the flat models from the
 * resource pack (assets/limbo/items/effects/*.json, models/effects/*.json, zero-thickness planes). Always
 * full-bright, never saved, and driven with interpolation so they move smoothly between ticks.
 *
 * Models (all flat):
 *   RING   - the black hole's accretion ring (one plane, spin it with the right rotation)
 *   LINK   - an Enchain chain link (two crossing planes so it reads from every angle; long axis = local Y)
 *   IMPACT - the starburst when Propel dashes through someone (one plane, used billboarded)
 *   HALO   - Temporal Repose's halo (one flat ellipse, used billboarded around the vertical axis)
 *   SHARD  - a Reality Shatter energy shard (two crossing planes; long axis = local Y)
 */
public final class FlatFx {
    private FlatFx() {}

    public static final NamespacedKey RING = key("limbo:effects/black_hole_ring");
    public static final NamespacedKey LINK = key("limbo:effects/chain_link");
    public static final NamespacedKey IMPACT = key("limbo:effects/impact");
    public static final NamespacedKey HALO = key("limbo:effects/halo");
    public static final NamespacedKey SHARD = key("limbo:effects/shard");
    public static final NamespacedKey FURNACE_BOLT = key("limbo:effects/furnace_bolt");

    private static NamespacedKey key(String s) {
        return Objects.requireNonNull(NamespacedKey.fromString(s), s);
    }

    /** Spawns a display of the given model at `at`. Scale 1 = the model's natural size (16 pixels = 1 block). */
    /**
     * A copy of `l` with yaw and pitch zeroed. A display entity turns its whole model with the entity's own rotation,
     * and the locations passed in here are often a player's (their look direction). That is what made Enchain's chain
     * (and the other flat effects) twist differently depending on where you were looking: all orientation must come
     * from the Transformation only, so the entity itself always faces yaw 0 / pitch 0.
     */
    private static Location unrotated(Location l) {
        Location c = l.clone();
        c.setYaw(0f);
        c.setPitch(0f);
        return c;
    }

    public static ItemDisplay spawn(Location at, NamespacedKey model, Display.Billboard billboard) {
        return at.getWorld().spawn(unrotated(at), ItemDisplay.class, d -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(model);
            it.setItemMeta(m);
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(billboard);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setPersistent(false);
            d.setShadowRadius(0f);
            d.setTeleportDuration(1);
        });
    }

    /** Sets size and orientation. `left` is applied last (world orientation), `right` first (spin about the model's own axes). */
    public static void shape(ItemDisplay d, Vector3f scale, Quaternionf left, Quaternionf right, int interpolationTicks) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, interpolationTicks));
        d.setTransformation(new Transformation(new Vector3f(), left, scale, right));
    }

    /** Moves the display smoothly to `to` over `ticks` ticks. */
    public static void move(ItemDisplay d, Location to, int ticks) {
        if (d == null || !d.isValid()) return;
        d.setTeleportDuration(Math.max(1, Math.min(59, ticks)));
        d.teleport(unrotated(to));
    }

    public static void remove(Entity d) {
        if (d != null && d.isValid()) d.remove();
    }

    /** Rotation that turns a model's local +Y axis (the long axis of LINK / SHARD) into `dir`. */
    public static Quaternionf yTo(Vector dir) {
        Vector3f to = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
        if (to.lengthSquared() < 1e-8f) return new Quaternionf();
        return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), to.normalize());
    }

    /** Rotation that turns a plane's normal (local +Z) into `normal`, i.e. lays RING flat against that surface. */
    public static Quaternionf zTo(Vector normal) {
        Vector3f to = new Vector3f((float) normal.getX(), (float) normal.getY(), (float) normal.getZ());
        if (to.lengthSquared() < 1e-8f) return new Quaternionf();
        return new Quaternionf().rotationTo(new Vector3f(0, 0, 1), to.normalize());
    }

    /** A burst: the model pops from `from` to `to` size over `ticks` ticks and is then removed. */
    public static void pop(Location at, NamespacedKey model, Display.Billboard billboard, float from, float to, int ticks, float rollRadians) {
        ItemDisplay d = spawn(at, model, billboard);
        Quaternionf roll = new Quaternionf().rotateZ(rollRadians);
        shape(d, new Vector3f(from, from, from), new Quaternionf(), roll, 0);
        Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () -> shape(d, new Vector3f(to, to, to), new Quaternionf(), roll, ticks), 1L);
        Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () -> remove(d), ticks + 3L);
    }
}
