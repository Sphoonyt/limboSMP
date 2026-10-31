package me.limbo.smp.util;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Damage that ignores armor/enchants/absorption. Keeps kill credit for the caster by landing a
 * tiny real hit first (credit + hurt animation), then lowering health directly.
 * While applying, isApplying() is true so ability listeners ignore the tiny hit (no hit counting / recursion).
 */
public final class TrueDamage {
    private static boolean applying;

    private TrueDamage() {}

    public static boolean isApplying() { return applying; }

    /** @return true if damage was applied. */
    public static boolean apply(Player caster, LivingEntity victim, double amount) {
        if (victim.isDead() || !victim.isValid()) return false;
        if (!Targeting.canHarm(caster, victim)) return false;
        if (victim.isInvulnerable()) return false;
        if (victim instanceof Player pv && (pv.getGameMode() == GameMode.CREATIVE || pv.getGameMode() == GameMode.SPECTATOR)) return false;

        int noDamageTicks = victim.getNoDamageTicks();
        double lastDamage = victim.getLastDamage();
        Vector velocity = victim.getVelocity();
        double before = victim.getHealth() + victim.getAbsorptionAmount();

        applying = true;
        try {
            victim.setNoDamageTicks(0);
            victim.damage(0.01, caster);
        } finally {
            applying = false;
        }
        if (victim.isDead()) return true;

        double after = victim.getHealth() + victim.getAbsorptionAmount();
        if (after >= before) return false; // damage was cancelled (protection plugin, resistance...)

        victim.setNoDamageTicks(noDamageTicks);
        victim.setLastDamage(lastDamage);
        victim.setVelocity(velocity);
        victim.setHealth(Math.max(0.0, victim.getHealth() - amount));
        return true;
    }
}
