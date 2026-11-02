package me.limbo.smp.darkness.serenity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.darkness.SerenityDarkness;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Golden apples give Regeneration II for double the normal duration. */
public final class SustainedHeal implements Passive, Listener {
    @Override public String id() { return "sustained_heal"; }
    @Override public String name() { return "Sustained Heal"; }
    @Override public String description() { return "Golden apples give Regeneration II for double the normal duration."; }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        if (m != Material.GOLDEN_APPLE && m != Material.ENCHANTED_GOLDEN_APPLE) return;
        Player p = e.getPlayer();
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.passivesActive(p, SerenityDarkness.ID)) return;

        int normal = (m == Material.GOLDEN_APPLE) ? 100 : 400; // vanilla Regen II: 5s / 20s
        int doubled = normal * 2;
        // run after vanilla applies its own effects
        Bukkit.getScheduler().runTask(pl, () -> {
            PotionEffect cur = p.getPotionEffect(PotionEffectType.REGENERATION);
            if (cur != null) {
                if (cur.getAmplifier() > 1) return;
                if (cur.getAmplifier() == 1 && (cur.getDuration() == PotionEffect.INFINITE_DURATION || cur.getDuration() >= doubled)) return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, doubled, 1));
        });
    }
}
