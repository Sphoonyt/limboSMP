package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.InertiaDarkness;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * The server-wide wind charge cooldown: after a player throws a wind charge they can't throw another for
 * wind-charge.cooldown-seconds (config.yml, 0 = vanilla). Inertia's Quick Charges passive halves it, using whole
 * seconds rounded down (5s -> 2s).
 */
public final class WindChargeCooldown implements Listener {
    private final LimboPlugin plugin;

    public WindChargeCooldown(LimboPlugin plugin) { this.plugin = plugin; }

    /** Cooldown in whole seconds for this player. */
    public int seconds(Player p) {
        int base = Math.max(0, plugin.getConfig().getInt("wind-charge.cooldown-seconds", 5));
        return plugin.passivesActive(p, InertiaDarkness.ID) ? base / 2 : base;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof WindCharge) || !(e.getEntity().getShooter() instanceof Player p)) return;
        int ticks = seconds(p) * 20;
        if (ticks <= 0) return;
        // vanilla applies its own short cooldown right after the throw, so set ours a tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.setCooldown(Material.WIND_CHARGE, ticks);
        });
    }
}
