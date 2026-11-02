package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.progress.Unlocks;
import me.limbo.smp.util.EffectUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;

/**
 * Permanent Strength I once rifts are unlocked (/limbo unlock rifts). Until then it's only +1.5 attack damage,
 * half of what Strength I gives, so people can't get 3-tapped in the first weeks.
 */
public final class StrengthPassive implements Passive {
    private static final double EARLY_BONUS = 1.5;

    private final NamespacedKey earlyKey;

    public StrengthPassive() {
        this.earlyKey = new NamespacedKey(LimboPlugin.get(), "entropy_early_strength");
    }

    @Override public String id() { return "strength"; }
    @Override public String name() { return "Strength"; }
    @Override public String description() { return "Permanent Strength I."; }

    private boolean full() {
        return LimboPlugin.get().unlocks().isUnlocked(Unlocks.Feature.RIFTS);
    }

    private void apply(Player p, boolean force) {
        if (full()) {
            removeEarly(p);
            EffectUtil.pulse(p, PotionEffectType.STRENGTH, 0, force);
        } else {
            EffectUtil.removePulse(p, PotionEffectType.STRENGTH, 0);
            addEarly(p);
        }
    }

    @Override public void onActivate(Player p) { apply(p, true); }
    @Override public void onTick(Player p) { apply(p, false); }

    @Override
    public void onDeactivate(Player p) {
        removeEarly(p);
        EffectUtil.removePulse(p, PotionEffectType.STRENGTH, 0);
    }

    private void addEarly(Player p) {
        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk == null) return;
        for (AttributeModifier m : atk.getModifiers()) if (m.getKey().equals(earlyKey)) return;
        atk.addTransientModifier(new AttributeModifier(earlyKey, EARLY_BONUS,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void removeEarly(Player p) {
        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk == null) return;
        for (AttributeModifier m : new ArrayList<>(atk.getModifiers())) {
            if (m.getKey().equals(earlyKey)) atk.removeModifier(m);
        }
    }
}
