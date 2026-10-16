package me.limbo.smp.darkness.ignition;

import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class FireResistancePassive implements Passive {
    @Override public String id() { return "fire_resistance"; }
    @Override public String name() { return "Fire Resistance"; }
    @Override public String description() { return "Permanent fire resistance."; }

    @Override public void onActivate(Player p) { EffectUtil.pulse(p, PotionEffectType.FIRE_RESISTANCE, 0, true); }
    @Override public void onTick(Player p) { EffectUtil.pulse(p, PotionEffectType.FIRE_RESISTANCE, 0); }
    @Override public void onDeactivate(Player p) { EffectUtil.removePulse(p, PotionEffectType.FIRE_RESISTANCE, 0); }
}
