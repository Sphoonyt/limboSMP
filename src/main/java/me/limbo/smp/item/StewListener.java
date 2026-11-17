package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Eating a suspicious stew that has Saturation in it also gives Weaving for 3 minutes. */
public final class StewListener implements Listener {
    private final LimboPlugin plugin;

    public StewListener(LimboPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.SUSPICIOUS_STEW) return;
        if (!(e.getItem().getItemMeta() instanceof SuspiciousStewMeta m)) return;
        boolean saturation = false;
        for (PotionEffect fx : m.getCustomEffects()) if (fx.getType().equals(PotionEffectType.SATURATION)) saturation = true;
        if (!saturation) return;
        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.WEAVING, 3 * 60 * 20, 0));
    }
}
