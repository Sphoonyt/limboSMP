package me.limbo.smp.util;

import me.limbo.smp.LimboPlugin;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * ALL ability damage / target selection must go through here so trust + ceasefire are respected.
 * victim.damage(amount, caster) gives the caster kill credit (which triggers siphon on death).
 */
public final class Targeting {
    private Targeting() {}

    /** Reflection's clones (tagged mannequins) are not valid targets for abilities. */
    public static boolean isClone(Entity e) {
        return e.getPersistentDataContainer().has(new NamespacedKey(LimboPlugin.get(), "reflection_clone"), PersistentDataType.STRING);
    }

    public static boolean canHarm(Player caster, Entity victim) {
        if (isClone(victim)) return false;
        if (victim.getUniqueId().equals(caster.getUniqueId())) return false;
        if (victim instanceof ArmorStand) return false;
        if (victim instanceof Player pv) {
            if (pv.getGameMode() == GameMode.SPECTATOR) return false;
            LimboPlugin pl = LimboPlugin.get();
            // caster trusts victim -> caster's abilities never affect victim
            if (pl.trust().isTrusting(caster.getUniqueId(), pv.getUniqueId())) return false;
            // Serenity ceasefire: ceasefired players can't harm players; the caster also can't be harmed
            if (pl.status().isCeasefire(caster.getUniqueId()) || pl.status().isProtected(pv.getUniqueId())) return false;
        }
        return true;
    }

    /** Players and hostile mobs. */
    public static boolean isEnemy(Entity e) {
        return e instanceof Player || e instanceof Enemy;
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
