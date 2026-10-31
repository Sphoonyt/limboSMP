package me.limbo.smp.util;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

/**
 * Passive "permanent" effects, done as short pulses: every 2 seconds the effect is (re)applied
 * with a 3 second duration, so it never lapses while the passive is active but vanishes
 * within 3 seconds once it stops.
 */
public final class EffectUtil {
    public static final int PULSE_DURATION_TICKS = 60;   // 3s
    public static final long PULSE_INTERVAL_MS = 2000L;  // every 2s

    private static final Map<String, Long> lastPulse = new HashMap<>();

    private EffectUtil() {}

    private static String key(Player p, PotionEffectType type) {
        return p.getUniqueId() + ":" + type.getKey();
    }

    /** Rate-limited; safe to call every passive tick. */
    public static void pulse(Player p, PotionEffectType type, int amplifier) {
        pulse(p, type, amplifier, false);
    }

    public static void pulse(Player p, PotionEffectType type, int amplifier, boolean force) {
        String k = key(p, type);
        long now = System.currentTimeMillis();
        Long last = lastPulse.get(k);
        if (!force && last != null && now - last < PULSE_INTERVAL_MS) return;

        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null) {
            if (cur.getAmplifier() > amplifier) return; // stronger effect already (e.g. potion)
            if (cur.getAmplifier() == amplifier
                    && (cur.getDuration() == PotionEffect.INFINITE_DURATION || cur.getDuration() > PULSE_DURATION_TICKS)) {
                return; // a longer effect already covers it
            }
        }
        p.addPotionEffect(new PotionEffect(type, PULSE_DURATION_TICKS, amplifier, false, false, true));
        lastPulse.put(k, now);
    }

    /** Remove a pulse we applied (short duration, matching amplifier). */
    public static void removePulse(Player p, PotionEffectType type, int amplifier) {
        lastPulse.remove(key(p, type));
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.getAmplifier() == amplifier
                && cur.getDuration() != PotionEffect.INFINITE_DURATION
                && cur.getDuration() <= PULSE_DURATION_TICKS) {
            p.removePotionEffect(type);
        }
    }
}
