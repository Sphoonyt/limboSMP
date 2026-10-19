package me.limbo.smp.darkness.entropy;

import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class StrengthPassive implements Passive {
    @Override public String id() { return "strength"; }
    @Override public String name() { return "Strength"; }
    @Override public String description() { return "Permanent Strength I."; }

    @Override public void onActivate(Player p) { EffectUtil.pulse(p, PotionEffectType.STRENGTH, 0, true); }
    @Override public void onTick(Player p) { EffectUtil.pulse(p, PotionEffectType.STRENGTH, 0); }
    @Override public void onDeactivate(Player p) { EffectUtil.removePulse(p, PotionEffectType.STRENGTH, 0); }
}
