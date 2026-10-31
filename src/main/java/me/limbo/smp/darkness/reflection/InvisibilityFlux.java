package me.limbo.smp.darkness.reflection;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Permanent invisibility while the totem is held (3s pulses every 2s). The body is invisible, but their nametag
 * (and gear) still show for anyone with a clear line of sight. Viewers whose view is blocked by blocks don't
 * see them at all, so the nametag never shows through walls.
 */
public final class InvisibilityFlux implements FluxPassive {
    private static final double RANGE_SQ = 64.0 * 64.0;

    /** target -> viewers the target is currently hidden from (line of sight blocked) */
    private final Map<UUID, Set<UUID>> hiddenFrom = new HashMap<>();
    private BukkitTask task;

    @Override public String id() { return "invisibility"; }
    @Override public String name() { return "Invisibility"; }
    @Override public String description() { return "Permanent invisibility while holding the totem. Your nametag still shows when seen."; }

    @Override
    public void onActivate(Player p) {
        EffectUtil.pulse(p, PotionEffectType.INVISIBILITY, 0, true);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(LimboPlugin.get(), this::updateVisibility, 4L, 4L);
    }

    @Override public void onTick(Player p) { EffectUtil.pulse(p, PotionEffectType.INVISIBILITY, 0); }

    @Override
    public void onDeactivate(Player p) {
        EffectUtil.removePulse(p, PotionEffectType.INVISIBILITY, 0);
        reveal(p);
    }

    @Override
    public void onShutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Player p : Bukkit.getOnlinePlayers()) reveal(p);
        hiddenFrom.clear();
    }

    private void updateVisibility() {
        LimboPlugin pl = LimboPlugin.get();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!pl.fluxActive(target, ReflectionDarkness.ID)) {
                reveal(target);
                continue;
            }
            Set<UUID> hidden = hiddenFrom.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>());
            hidden.removeIf(u -> Bukkit.getPlayer(u) == null); // logged out: their state was reset

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target) || viewer.getWorld() != target.getWorld()) continue;
                if (viewer.getLocation().distanceSquared(target.getLocation()) > RANGE_SQ) continue;
                if (hasLineOfSight(viewer, target)) {
                    if (hidden.remove(viewer.getUniqueId())) viewer.showEntity(pl, target);
                } else {
                    if (hidden.add(viewer.getUniqueId())) viewer.hideEntity(pl, target);
                }
            }
        }
    }

    /** Un-hides the target from everyone it was hidden from. */
    private void reveal(Player target) {
        Set<UUID> hidden = hiddenFrom.remove(target.getUniqueId());
        if (hidden == null) return;
        LimboPlugin pl = LimboPlugin.get();
        for (UUID u : hidden) {
            Player v = Bukkit.getPlayer(u);
            if (v != null) v.showEntity(pl, target);
        }
    }

    /** Clear if a ray from the viewer's eyes reaches either the target's head or their middle without hitting a block. */
    private boolean hasLineOfSight(Player viewer, Player target) {
        Location eye = viewer.getEyeLocation();
        for (double h : new double[]{target.getEyeHeight(), target.getHeight() * 0.5}) {
            Location to = target.getLocation().add(0, h, 0);
            Vector dir = to.toVector().subtract(eye.toVector());
            double dist = dir.length();
            if (dist < 0.5) return true;
            RayTraceResult r = viewer.getWorld().rayTraceBlocks(eye, dir.normalize(), dist, FluidCollisionMode.NEVER, true);
            if (r == null) return true;
        }
        return false;
    }
}
