package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * ALL ability damage / target selection must go through here so the trust system is respected.
 * victim.damage(amount, caster) gives the caster kill credit (which triggers siphon on death).
 */
public final class Targeting {
    private Targeting() {}

    public static boolean canHarm(Player caster, Entity victim) {
        if (victim.getUniqueId().equals(caster.getUniqueId())) return false;
        if (victim instanceof ArmorStand) return false;
        if (victim instanceof Player pv) {
            if (pv.getGameMode() == GameMode.SPECTATOR) return false;
            // caster trusts victim -> caster's abilities never affect victim
            if (LimboPlugin.get().trust().isTrusting(caster.getUniqueId(), pv.getUniqueId())) return false;
        }
        return true;
    }

    public static boolean damage(Player caster, LivingEntity victim, double amount) {
        if (!canHarm(caster, victim)) return false;
        victim.damage(amount, caster);
        return true;
    }

    public static List<LivingEntity> nearby(Player caster, Location center, double radius) {
        if (center.getWorld() == null) return List.of();
        return center.getWorld().getNearbyLivingEntities(center, radius).stream()
                .filter(e -> canHarm(caster, e))
                .toList();
    }
}
