package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/** Your hits have a 1% chance to swap 2 random hotbar slots of the enemy. */
public final class DisruptionPassive implements Passive, Listener {
    @Override public String id() { return "disruption"; }
    @Override public String name() { return "Disruption"; }
    @Override public String description() { return "Your hits have a 1% chance to scramble 2 of the enemy's hotbar slots."; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof Player v)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.passivesActive(p, EntropyDarkness.ID)) return;
        if (!Targeting.canHarm(p, v)) return;

        double chance = pl.getConfig().getDouble("entropy.disruption-chance", 0.01);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= chance) return;

        int a = r.nextInt(9);
        int b = r.nextInt(8);
        if (b >= a) b++; // distinct slot
        PlayerInventory inv = v.getInventory();
        ItemStack ia = inv.getItem(a);
        ItemStack ib = inv.getItem(b);
        inv.setItem(a, ib);
        inv.setItem(b, ia);

        ParticleUtil.burst(v.getLocation().add(0, 1, 0), EntropyDarkness.COLOR, 20);
        v.playSound(v.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        v.sendActionBar(Msg.parse("<dark_red>Your hotbar was scrambled!"));
    }
}
