package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Hitting an enemy in the back (orb held) -> after a short delay: half a heart true damage and a 1s stun.
 * 12s internal cooldown. Tip: keep the orb in the offhand and a weapon in the main hand.
 */
public final class SpinecutterFlux implements FluxPassive, Listener {
    private static final String CD_KEY = "entropy:spinecutter";
    private static final long COOLDOWN_MS = 12_000L;
    private static final int DELAY_TICKS = 5;
    private static final double DAMAGE = 1.0;     // half a heart
    private static final int STUN_TICKS = 20;
    private static final double BACK_DOT = -0.5;  // attacker within the rear 120 degree arc

    @Override public String id() { return "spinecutter"; }
    @Override public String name() { return "Spinecutter"; }
    @Override public String description() { return "Hitting an enemy in the back deals half a heart true damage and stuns for 1s after a short delay. 12s internal cooldown."; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity v)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.fluxActive(p, EntropyDarkness.ID)) return;
        if (!Targeting.canHarm(p, v) || !Targeting.isEnemy(v)) return;
        if (!inBack(p, v)) return;

        UUID id = p.getUniqueId();
        if (pl.dispatcher().cooldowns().remaining(id, CD_KEY) > 0) return;
        pl.dispatcher().cooldowns().set(id, CD_KEY, COOLDOWN_MS);

        Location mark = v.getLocation().add(0, v.getHeight() * 0.6, 0);
        ParticleUtil.blissDust(mark, EntropyDarkness.COLOR, 10, 0.3);
        p.getWorld().playSound(mark, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!v.isValid() || v.isDead() || !p.isOnline()) return;
            TrueDamage.apply(p, v, DAMAGE);
            if (v.isDead()) return;
            pl.status().stun(v, STUN_TICKS);
            Location l = v.getLocation().add(0, v.getHeight() * 0.6, 0);
            ParticleUtil.burst(l, EntropyDarkness.COLOR, 24);
            l.getWorld().playSound(l, Sound.ENTITY_WITHER_HURT, 0.6f, 1.6f);
            p.sendActionBar(Msg.parse("<#961428>Spinecutter"));
        }, DELAY_TICKS);
    }

    private boolean inBack(Player attacker, LivingEntity victim) {
        Vector facing = victim.getLocation().getDirection().setY(0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 1e-6 || toAttacker.lengthSquared() < 1e-6) return false;
        return facing.normalize().dot(toAttacker.normalize()) < BACK_DOT;
    }
}
