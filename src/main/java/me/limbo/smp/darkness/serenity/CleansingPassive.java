package me.limbo.smp.darkness.serenity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.darkness.SerenityDarkness;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Immune to darkness, blindness, slowness, poison, weakness and wither. */
public final class CleansingPassive implements Passive, Listener {
    private static final List<PotionEffectType> BLOCKED = List.of(
            PotionEffectType.DARKNESS, PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS,
            PotionEffectType.POISON, PotionEffectType.WEAKNESS, PotionEffectType.WITHER);

    @Override public String id() { return "cleansing"; }
    @Override public String name() { return "Cleansing"; }
    @Override public String description() { return "Immune to darkness, blindness, slowness, poison, weakness and wither."; }

    @Override public void onActivate(Player p) { purge(p); }
    @Override public void onTick(Player p) { purge(p); }

    private void purge(Player p) {
        for (PotionEffectType t : BLOCKED) if (p.hasPotionEffect(t)) p.removePotionEffect(t);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PotionEffect n = e.getNewEffect();
        if (n == null || !BLOCKED.contains(n.getType())) return;
        if (!LimboPlugin.get().passivesActive(p, SerenityDarkness.ID)) return;
        e.setCancelled(true);
    }
}
