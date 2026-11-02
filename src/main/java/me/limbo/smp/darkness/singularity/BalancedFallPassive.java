package me.limbo.smp.darkness.singularity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.darkness.SingularityDarkness;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** Fall damage immunity. */
public final class BalancedFallPassive implements Passive, Listener {
    @Override public String id() { return "balanced_fall"; }
    @Override public String name() { return "Balanced Fall"; }
    @Override public String description() { return "Immune to fall damage."; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (e.getEntity() instanceof Player p && LimboPlugin.get().passivesActive(p, SingularityDarkness.ID)) {
            e.setCancelled(true);
        }
    }
}
