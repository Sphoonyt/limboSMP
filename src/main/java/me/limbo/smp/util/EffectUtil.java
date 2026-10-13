package me.limbo.smp.util;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Permanent (infinite) potion effects for passives. */
public final class EffectUtil {
    private EffectUtil() {}

    public static void ensureInfinite(Player p, PotionEffectType type, int amplifier) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null) {
            if (cur.getAmplifier() > amplifier) return; // stronger effect already (e.g. potion)
            if (cur.getAmplifier() == amplifier && cur.getDuration() == PotionEffect.INFINITE_DURATION) return;
        }
        p.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
    }

    public static void removeInfinite(Player p, PotionEffectType type, int amplifier) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.getDuration() == PotionEffect.INFINITE_DURATION && cur.getAmplifier() == amplifier) {
            p.removePotionEffect(type);
        }
    }
}
