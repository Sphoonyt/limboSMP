package me.limbo.smp.darkness.singularity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.darkness.SingularityDarkness;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every 12th melee hit gives the enemy Levitation for 1 second. */
public final class AntiGravityPassive implements Passive, Listener {
    private static final int EVERY = 12;
    private static final int LEVITATION_TICKS = 20;

    private final Map<UUID, Integer> hits = new HashMap<>();

    @Override public String id() { return "anti_gravity"; }
    @Override public String name() { return "Anti Gravity"; }
    @Override public String description() { return "Every 12th hit gives the enemy levitation for 1 second."; }

    @Override public void onDeactivate(Player p) { hits.remove(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity v)) return;
        if (!LimboPlugin.get().passivesActive(p, SingularityDarkness.ID)) return;
        if (!Targeting.canHarm(p, v) || !Targeting.isEnemy(v)) return;

        int n = hits.merge(p.getUniqueId(), 1, Integer::sum);
        if (n >= EVERY) {
            hits.put(p.getUniqueId(), 0);
            v.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, LEVITATION_TICKS, 0, false, true, true));
            ParticleUtil.blissDust(v.getLocation().add(0, 1, 0), SingularityDarkness.COLOR, 16, 0.4);
        }
    }
}
