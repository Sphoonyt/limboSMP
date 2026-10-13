package me.limbo.smp.darkness.ignition;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * L - Overheat: 12s. Every melee hit ignites the victim; every 3rd hit creates an explosion at the
 * victim dealing 1.5 hearts true damage + knockback to all OTHER enemies in it. 90s cooldown.
 */
public final class Overheat extends Channel implements Listener {
    private static final int DURATION_TICKS = 240;
    private static final int FIRE_TICKS = 60;      // 3s
    private static final int EXPLODE_EVERY = 3;
    private static final double RADIUS = 3.5;
    private static final double DAMAGE = 3.0;      // 1.5 hearts
    private static final double KNOCKBACK = 1.3;

    private static final class State {
        int hits;
        BukkitTask task;
    }

    private final Map<UUID, State> active = new HashMap<>();

    public Overheat() {
        super("overheat", "Overheat",
                "12s: your hits ignite; every 3rd hit explodes for 1.5 hearts true damage + knockback.",
                ChannelInput.L, 90_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        UUID id = p.getUniqueId();
        end(id);
        State s = new State();
        active.put(id, s);
        s.task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 2;
                if (!p.isOnline() || p.isDead() || ticks >= DURATION_TICKS) {
                    end(id);
                    if (p.isOnline()) p.sendActionBar(Msg.parse("<gray>Overheat faded."));
                    return;
                }
                Location l = p.getLocation().add(0, 1, 0);
                l.getWorld().spawnParticle(Particle.FLAME, l, 4, 0.35, 0.6, 0.35, 0.01);
                ParticleUtil.blissDust(l, IgnitionDarkness.COLOR, 4, 0.4);
                if (ticks % 6 == 0) ParticleUtil.aura(p, IgnitionDarkness.COLOR);
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        p.sendActionBar(Msg.parse("<#ff6e14>Overheat"));
        ParticleUtil.burst(p.getLocation().add(0, 1, 0), IgnitionDarkness.COLOR, 30);
        return true;
    }

    private void end(UUID id) {
        State s = active.remove(id);
        if (s != null && s.task != null) s.task.cancel();
    }

    @Override
    public void onShutdown() {
        for (UUID id : new java.util.ArrayList<>(active.keySet())) end(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        State s = active.get(p.getUniqueId());
        if (s == null) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!Targeting.canHarm(p, victim)) return;

        victim.setFireTicks(Math.max(victim.getFireTicks(), FIRE_TICKS));
        s.hits++;
        if (s.hits % EXPLODE_EVERY == 0) explode(p, victim);
    }

    private void explode(Player p, LivingEntity hit) {
        Location c = hit.getLocation().add(0, hit.getHeight() / 2, 0);
        c.getWorld().spawnParticle(Particle.EXPLOSION, c, 2, 0.3, 0.3, 0.3, 0);
        c.getWorld().spawnParticle(Particle.FLAME, c, 60, 1.0, 0.6, 1.0, 0.12);
        ParticleUtil.burst(c, IgnitionDarkness.COLOR, 40);
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);

        for (LivingEntity t : Targeting.nearby(p, c, RADIUS)) {
            if (t.equals(hit) || !Targeting.isEnemy(t)) continue;
            TrueDamage.apply(p, t, DAMAGE);
            if (t.isDead()) continue;
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 1e-4) away = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            away.normalize().multiply(KNOCKBACK).setY(0.45);
            t.setVelocity(away);
        }
    }
}
