package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * L - for 8s your sword and axe get the mace slam: hit an enemy while falling (more than 1.5 blocks)
 * for the mace's fall-distance bonus damage, no fall damage afterwards, and a small shockwave knockback.
 * Total damage of the hit is capped at 6 hearts. 75s cooldown.
 */
public final class Conversion extends Channel implements Listener {
    private static final int DURATION_TICKS = 160; // 8s
    private static final double DAMAGE_CAP = 12.0; // 6 hearts
    private static final double MIN_FALL = 1.5;
    private static final double SHOCK_RADIUS = 3.5;

    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public Conversion() {
        super("conversion", "Conversion", "Sword and axe gain the mace slam.", ChannelInput.L, 75_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        UUID id = p.getUniqueId();
        end(id);
        BukkitTask t = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 4;
                if (!p.isOnline() || p.isDead() || ticks >= DURATION_TICKS) {
                    end(id);
                    if (p.isOnline()) p.sendActionBar(Msg.parse("<gray>Conversion faded."));
                    return;
                }
                ParticleUtil.blissDust(p.getLocation().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 4, 0.4);
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 4L);
        active.put(id, t);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.5f);
        ParticleUtil.burst(p.getLocation().add(0, 1, 0), InertiaDarkness.LIGHT, 24);
        p.sendActionBar(Msg.parse("<#40e0d0>Conversion"));
        return true;
    }

    private void end(UUID id) {
        BukkitTask t = active.remove(id);
        if (t != null) t.cancel();
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) end(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!active.containsKey(p.getUniqueId())) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!Targeting.canHarm(p, victim)) return;

        Material held = p.getInventory().getItemInMainHand().getType();
        if (!Tag.ITEMS_SWORDS.isTagged(held) && !Tag.ITEMS_AXES.isTagged(held)) return;

        double fall = p.getFallDistance();
        if (fall <= MIN_FALL || p.isOnGround() || p.isGliding() || p.isFlying() || p.isInWater()) return;

        // vanilla mace bonus: 4/block for 3 blocks, 2/block for the next 5, then 1/block
        double bonus;
        if (fall <= 3) bonus = 4.0 * fall;
        else if (fall <= 8) bonus = 12.0 + 2.0 * (fall - 3);
        else bonus = 22.0 + (fall - 8);

        e.setDamage(Math.min(e.getDamage() + bonus, DAMAGE_CAP)); // armor only reduces from here
        p.setFallDistance(0f); // slam negates the fall

        Location c = victim.getLocation();
        c.getWorld().spawnParticle(Particle.EXPLOSION, c, 1);
        ParticleUtil.burst(c.clone().add(0, 0.3, 0), InertiaDarkness.LIGHT, 36);
        ParticleUtil.ring(c.clone().add(0, 0.2, 0), SHOCK_RADIUS, 36, InertiaDarkness.LIGHT);
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
        p.sendActionBar(Msg.parse("<#40e0d0>Slam!"));

        for (LivingEntity t : Targeting.nearby(p, c, SHOCK_RADIUS)) {
            if (t.equals(victim) || !Targeting.isEnemy(t)) continue;
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 1e-4) continue;
            double strength = 0.3 + 0.7 * (1.0 - Math.min(1.0, t.getLocation().distance(c) / SHOCK_RADIUS));
            away.normalize().multiply(strength).setY(0.35);
            t.setVelocity(away);
        }
    }
}
