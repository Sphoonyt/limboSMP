package me.limbo.smp.darkness.ignition;

import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class FireResistancePassive implements Passive {
    @Override public String id() { return "fire_resistance"; }
    @Override public String name() { return "Fire Resistance"; }
    @Override public String description() { return "Permanent fire resistance."; }

    @Override public void onActivate(Player p) { EffectUtil.ensureInfinite(p, PotionEffectType.FIRE_RESISTANCE, 0); }
    @Override public void onTick(Player p) { EffectUtil.ensureInfinite(p, PotionEffectType.FIRE_RESISTANCE, 0); }
    @Override public void onDeactivate(Player p) { EffectUtil.removeInfinite(p, PotionEffectType.FIRE_RESISTANCE, 0); }
}
