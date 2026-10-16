package me.limbo.smp.darkness.reflection;

import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** Permanent invisibility while the orb is held (3s pulses every 2s, no particles). */
public final class InvisibilityFlux implements FluxPassive {
    @Override public String id() { return "invisibility"; }
    @Override public String name() { return "Invisibility"; }
    @Override public String description() { return "Permanent invisibility while holding the orb."; }

    @Override public void onActivate(Player p) { EffectUtil.pulse(p, PotionEffectType.INVISIBILITY, 0, true); }
    @Override public void onTick(Player p) { EffectUtil.pulse(p, PotionEffectType.INVISIBILITY, 0); }
    @Override public void onDeactivate(Player p) { EffectUtil.removePulse(p, PotionEffectType.INVISIBILITY, 0); }
}
