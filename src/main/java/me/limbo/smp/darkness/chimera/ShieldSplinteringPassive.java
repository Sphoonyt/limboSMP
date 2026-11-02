package me.limbo.smp.darkness.chimera;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.ChimeraDarkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.Sfx;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hit a raised shield without disabling it and the shield is "cracked". Your next hit on a shield at least
 * 0.5 seconds later disables it for 5 seconds, exactly like an axe would, with any weapon.
 */
public final class ShieldSplinteringPassive implements Passive, Listener {
    private static final long DELAY_MS = 500L;
    private static final int DISABLE_TICKS = 100; // an axe disables a shield for 5 seconds

    private static final class State {
        boolean cracked;
        long crackedAt;
    }

    private final Map<UUID, State> states = new HashMap<>();

    @Override public String id() { return "shield_splintering"; }
    @Override public String name() { return "Shield Splintering"; }
    @Override public String description() {
        return "Hit a shield without disabling it, and your next hit on a shield 0.5s later disables it like an axe, with any weapon.";
    }

    @Override public void onDeactivate(Player p) { states.remove(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof Player v)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.passivesActive(p, ChimeraDarkness.ID)) return;
        if (!Targeting.canHarm(p, v)) return;
        if (!v.isBlocking()) return;
        // an axe already disables the shield by itself
        if (p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) return;

        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        long now = System.currentTimeMillis();
        Location at = v.getLocation().add(0, 1.2, 0);

        if (!s.cracked) { // first hit: crack it
            s.cracked = true;
            s.crackedAt = now;
            v.getWorld().spawnParticle(Particle.CRIT, at, 8, 0.25, 0.25, 0.25, 0.1);
            v.getWorld().playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.6f);
            p.sendActionBar(Msg.parse("<#46be5a>Shield cracked"));
            return;
        }
        if (now - s.crackedAt < DELAY_MS) return; // too soon, it stays cracked

        s.cracked = false;
        v.setCooldown(Material.SHIELD, DISABLE_TICKS);
        v.clearActiveItem(); // lowers the shield right now
        v.getWorld().spawnParticle(Particle.ITEM, at, 24, 0.3, 0.3, 0.3, 0.12,
                new org.bukkit.inventory.ItemStack(Material.OAK_PLANKS));
        Sfx.play(at, Sound.ITEM_SHIELD_BREAK, 1f, 0.9f, Sfx.Theme.CHIMERA);
        p.sendActionBar(Msg.parse("<#46be5a>Shield splintered"));
        v.sendActionBar(Msg.parse("<#46be5a>Your shield was splintered"));
    }
}
