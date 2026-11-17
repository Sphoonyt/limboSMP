package me.limbo.smp.util;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Damage that ignores armor and enchants (absorption hearts are used up first; apply(..., true) also honours Resistance). Keeps kill credit for the caster by landing a
 * tiny real hit first (credit + hurt animation), then lowering health directly.
 * While applying, isApplying() is true so ability listeners ignore the tiny hit (no hit counting / recursion).
 */
public final class TrueDamage {
    private static boolean applying;

    private TrueDamage() {}

    public static boolean isApplying() { return applying; }

    /** @return true if damage was applied. */
    public static boolean apply(Player caster, LivingEntity victim, double amount) { return apply(caster, victim, amount, false); }

    /** @param resistance true = the victim's Resistance effect reduces it (20% per level, level 5 = immune), as a normal hit would. */
    public static boolean apply(Player caster, LivingEntity victim, double amount, boolean resistance) {
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

        amount = me.limbo.smp.darkness.chimera.GlobalAdaptationFlux.filterTrue(caster, victim, amount); // Chimera flux
        if (resistance) {
            org.bukkit.potion.PotionEffect res = victim.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
            if (res != null) amount *= Math.max(0.0, 1.0 - 0.2 * (res.getAmplifier() + 1));
        }
        // absorption hearts take the hit first
        double absorb = victim.getAbsorptionAmount();
        if (absorb > 0 && amount > 0) {
            double taken = Math.min(absorb, amount);
            victim.setAbsorptionAmount(absorb - taken);
            amount -= taken;
        }
        victim.setNoDamageTicks(noDamageTicks);
        victim.setLastDamage(lastDamage);
        victim.setVelocity(velocity);
        if (amount <= 0.0) return true;
        double left = victim.getHealth() - amount;
        if (left <= 0.0 && victim instanceof Player pv && me.limbo.smp.LimboPlugin.get().totemGuard().tryUndying(pv)) return true;
        victim.setHealth(Math.max(0.0, left));
        return true;
    }
}
