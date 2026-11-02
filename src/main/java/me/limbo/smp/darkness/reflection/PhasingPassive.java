package me.limbo.smp.darkness.reflection;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.concurrent.ThreadLocalRandom;

/** 3% chance to phase straight through a melee hit (no damage, no knockback). */
public final class PhasingPassive implements Passive, Listener {
    private static final double CHANCE = 0.03;

    @Override public String id() { return "phasing"; }
    @Override public String name() { return "Phasing"; }
    @Override public String description() { return "3% chance to phase through melee hits."; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK && e.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!LimboPlugin.get().passivesActive(p, ReflectionDarkness.ID)) return;
        if (ThreadLocalRandom.current().nextDouble() >= CHANCE) return;

        e.setCancelled(true);
        ParticleUtil.blissDust(p.getLocation().add(0, 1, 0), ReflectionDarkness.COLOR, 18, 0.45);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.8f);
        p.sendActionBar(Msg.parse("<#a5cdff>Phased"));
    }
}
