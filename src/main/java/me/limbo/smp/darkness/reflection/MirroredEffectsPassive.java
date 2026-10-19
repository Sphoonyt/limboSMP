package me.limbo.smp.darkness.reflection;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.Targeting;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Set;

/**
 * When an enemy within 5 blocks gains a positive effect, you also get it for half the duration
 * (rounded down, in ticks). Regeneration and absorption are excluded. Effects applied by plugins
 * (including this plugin's own passive pulses) are ignored so passives don't get copied constantly.
 */
public final class MirroredEffectsPassive implements Passive, Listener {
    private static final double RANGE = 5.0;
    private static final Set<String> POSITIVE = Set.of(
            "speed", "haste", "strength", "jump_boost", "resistance", "fire_resistance", "water_breathing",
            "invisibility", "night_vision", "health_boost", "saturation", "luck", "slow_falling",
            "conduit_power", "dolphins_grace", "hero_of_the_village");

    /** Stops mirrored effects from being mirrored again between two Reflection users. */
    private static boolean mirroring = false;

    @Override public String id() { return "mirrored_effects"; }
    @Override public String name() { return "Mirrored Effects"; }
    @Override public String description() { return "Enemies within 5 blocks gaining a positive effect also grants it to you for half the duration."; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (mirroring) return;
        PotionEffect n = e.getNewEffect();
        if (n == null) return;
        if (e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (!POSITIVE.contains(n.getType().getKey().getKey())) return;
        if (n.getDuration() == PotionEffect.INFINITE_DURATION) return;
        PotionEffect old = e.getOldEffect();
        if (old != null && old.getAmplifier() >= n.getAmplifier() && old.getDuration() >= n.getDuration()) return; // just a refresh
        int half = n.getDuration() / 2;
        if (half <= 0) return;
        if (!(e.getEntity() instanceof LivingEntity src) || !Targeting.isEnemy(src)) return;

        LimboPlugin pl = LimboPlugin.get();
        for (Entity near : src.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (!(near instanceof Player r) || r.equals(src)) continue;
            if (r.getLocation().distanceSquared(src.getLocation()) > RANGE * RANGE) continue;
            if (!pl.passivesActive(r, ReflectionDarkness.ID)) continue;
            if (src instanceof Player sp && pl.trust().isTrusting(r.getUniqueId(), sp.getUniqueId())) continue; // trusted aren't enemies
            mirroring = true;
            try {
                r.addPotionEffect(new PotionEffect(n.getType(), half, n.getAmplifier(), false, true, true));
            } finally {
                mirroring = false;
            }
        }
    }
}
