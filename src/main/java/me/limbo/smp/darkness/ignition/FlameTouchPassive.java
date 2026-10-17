package me.limbo.smp.darkness.ignition;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every 5th melee hit sets the victim on fire for 3 seconds. */
public final class FlameTouchPassive implements Passive, Listener {
    private static final int EVERY = 5;
    private static final int FIRE_TICKS = 60;

    private final Map<UUID, Integer> hits = new HashMap<>();

    @Override public String id() { return "flame_touch"; }
    @Override public String name() { return "Flame Touch"; }
    @Override public String description() { return "Every 5th hit sets your victim on fire for 3 seconds."; }

    @Override public void onDeactivate(Player p) { hits.remove(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity v)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.passivesActive(p, IgnitionDarkness.ID)) return;
        if (!Targeting.canHarm(p, v)) return;

        int n = hits.merge(p.getUniqueId(), 1, Integer::sum);
        if (n >= EVERY) {
            hits.put(p.getUniqueId(), 0);
            v.setFireTicks(Math.max(v.getFireTicks(), FIRE_TICKS));
            ParticleUtil.burst(v.getLocation().add(0, 1, 0), IgnitionDarkness.COLOR, 14);
        }
    }
}
