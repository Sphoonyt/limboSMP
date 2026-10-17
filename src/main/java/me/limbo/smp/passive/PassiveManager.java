package me.limbo.smp.passive;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.darkness.Passive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives passive lifecycles.
 *  - Passives: active while a null totem is in the main inventory (or offhand).
 *  - Flux passive: active only while the orb is held in main hand or offhand.
 *  - Siphon does NOT affect either.
 */
public final class PassiveManager {
    private final LimboPlugin plugin;
    private final Map<UUID, Darkness> passiveActive = new HashMap<>();
    private final Map<UUID, Darkness> fluxActive = new HashMap<>();
    private BukkitTask task;

    public PassiveManager(LimboPlugin plugin) { this.plugin = plugin; }

    public void start() {
        long iv = Math.max(1L, plugin.getConfig().getLong("passives.tick-interval-ticks", 10L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) tick(p);
        }, 20L, iv);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) release(p);
    }

    private void tick(Player p) {
        plugin.items().syncTotems(p);
        Darkness d = plugin.data().getDarkness(p);
        boolean alive = !p.isDead();
        boolean hasOrb = d != null && alive && plugin.items().hasOrbInInventory(p);
        Darkness wantPassive = hasOrb ? d : null;
        Darkness wantFlux = (hasOrb && plugin.items().isHoldingOrb(p)) ? d : null;
        UUID id = p.getUniqueId();

        syncPassives(p, id, wantPassive);
        syncFlux(p, id, wantFlux);

        if (wantPassive != null) {
            for (Passive ps : d.passives()) safe(() -> ps.onTick(p), ps.id());
        }
        if (wantFlux != null && d.fluxPassive() != null) {
            FluxPassive f = d.fluxPassive();
            safe(() -> f.onTick(p), f.id());
        }
    }

    private void syncPassives(Player p, UUID id, Darkness want) {
        Darkness cur = passiveActive.get(id);
        if (cur == want) return;
        if (cur != null) {
            for (Passive ps : cur.passives()) safe(() -> ps.onDeactivate(p), ps.id());
            passiveActive.remove(id);
        }
        if (want != null) {
            for (Passive ps : want.passives()) safe(() -> ps.onActivate(p), ps.id());
            passiveActive.put(id, want);
        }
    }

    private void syncFlux(Player p, UUID id, Darkness want) {
        Darkness cur = fluxActive.get(id);
        if (cur == want) return;
        if (cur != null) {
            FluxPassive f = cur.fluxPassive();
            if (f != null) safe(() -> f.onDeactivate(p), f.id());
            fluxActive.remove(id);
        }
        if (want != null) {
            FluxPassive f = want.fluxPassive();
            if (f != null) safe(() -> f.onActivate(p), f.id());
            fluxActive.put(id, want);
        }
    }

    /** Deactivate everything for a player (quit, shutdown). */
    public void release(Player p) {
        syncPassives(p, p.getUniqueId(), null);
        syncFlux(p, p.getUniqueId(), null);
    }

    private void safe(Runnable r, String id) {
        try {
            r.run();
        } catch (Throwable t) {
            plugin.getLogger().severe("Passive " + id + " threw: " + t);
        }
    }
}
