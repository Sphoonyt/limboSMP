package me.limbo.smp.darkness.inertia;

import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class SpeedPassive implements Passive {
    @Override public String id() { return "speed"; }
    @Override public String name() { return "Speed"; }
    @Override public String description() { return "Permanent Speed I."; }

    @Override public void onActivate(Player p) { EffectUtil.pulse(p, PotionEffectType.SPEED, 0, true); }
    @Override public void onTick(Player p) { EffectUtil.pulse(p, PotionEffectType.SPEED, 0); }
    @Override public void onDeactivate(Player p) { EffectUtil.removePulse(p, PotionEffectType.SPEED, 0); }
}
