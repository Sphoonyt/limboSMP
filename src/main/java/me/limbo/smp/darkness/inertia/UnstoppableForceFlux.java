package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * After sprinting 2s straight (orb held), the next hit against you while you're still sprinting
 * deals no knockback. 6s internal cooldown. Knockback is removed by briefly granting full knockback
 * resistance inside the damage event (damage still applies).
 */
public final class UnstoppableForceFlux implements FluxPassive, Listener {
    private static final String CD_KEY = "inertia:unstoppable";
    private static final long COOLDOWN_MS = 6_000L;
    private static final long SPRINT_MS = 2_000L;

    private final Map<UUID, Long> sprintStart = new HashMap<>();

    @Override public String id() { return "unstoppable_force"; }
    @Override public String name() { return "Unstoppable Force"; }
    @Override public String description() { return "After sprinting 2s straight, the next hit against you has no knockback. 6s internal cooldown."; }

    @Override
    public void onTick(Player p) {
        // catches players who were already sprinting when the flux became active
        if (p.isSprinting()) sprintStart.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
        else sprintStart.remove(p.getUniqueId());
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (e.isSprinting()) sprintStart.put(id, System.currentTimeMillis());
        else sprintStart.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { sprintStart.remove(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.fluxActive(p, InertiaDarkness.ID)) return;
        if (!p.isSprinting()) return;
        Long st = sprintStart.get(p.getUniqueId());
        if (st == null || System.currentTimeMillis() - st < SPRINT_MS) return;
        UUID id = p.getUniqueId();
        if (pl.dispatcher().cooldowns().remaining(id, CD_KEY) > 0) return;
        pl.dispatcher().cooldowns().set(id, CD_KEY, COOLDOWN_MS);

        // knockback is applied right after this event; resistance 1.0 for 2 ticks cancels it
        pl.status().knockbackImmunity(p, 2);

        ParticleUtil.burst(p.getLocation().add(0, 1, 0), InertiaDarkness.LIGHT, 20);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.7f);
        p.sendActionBar(Msg.parse("<#40e0d0>Unstoppable"));
    }
}
