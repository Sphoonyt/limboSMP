package me.limbo.smp.darkness.serenity;

import me.limbo.smp.util.Feedback;
import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.SerenityDarkness;
import me.limbo.smp.status.StatusManager;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.limbo.smp.util.TrueDamage;

/**
 * H - the enemy hit has their tick rate lowered for 6s (only them). Players get a real client tick-rate
 * drop via packet; everything else falls back to attribute scaling (see StatusManager#slowTime).
 * Tune in config.yml: temporal-repose.time-scale (default 0.6 = 12 tps), duration-seconds, mode.
 * 50s cooldown.
 */
public final class TemporalRepose extends Channel implements Listener {

    private static final double LIFESTEAL = 0.5;

    private static final class Slow {
        final UUID caster;
        final long until;
        Slow(UUID caster, long until) { this.caster = caster; this.until = until; }
    }

    /** enemy slowed by Temporal Repose -> who slowed them and until when */
    private final Map<UUID, Slow> slows = new HashMap<>();

    public TemporalRepose() {
        super("temporal_repose", "Temporal Repose",
                "Slows time for the enemy you hit.",
                ChannelInput.H, 50_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        LivingEntity t = ctx.target();
        if (t == null) return false;
        LimboPlugin pl = LimboPlugin.get();
        final int durationTicks = (int) (pl.getConfig().getDouble("temporal-repose.duration-seconds", 6.0) * 20);
        pl.status().slowTime(t, durationTicks);
        slows.put(t.getUniqueId(), new Slow(ctx.player().getUniqueId(), System.currentTimeMillis() + durationTicks * 50L));
        Feedback.affected(t, "Temporal Repose", ctx.player());
        UUID id = t.getUniqueId();

        t.getWorld().playSound(t.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.5f);
        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 0.6f);
        ParticleUtil.burst(t.getLocation().add(0, 1, 0), SerenityDarkness.COLOR, 24);
        if (t instanceof Player victim) victim.sendActionBar(Msg.parse("<#ffafcd>Your time slows..."));
        ctx.player().sendActionBar(Msg.parse("<#ffafcd>Temporal Repose"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 4;
                if (!t.isValid() || t.isDead() || !pl.status().isTimeSlowed(id) || ticks > durationTicks + 8) {
                    cancel();
                    return;
                }
                // slow clock face above the head: ring + one hand that barely moves
                Location c = t.getLocation().add(0, t.getHeight() + 0.5, 0);
                ParticleUtil.ring(c, 0.45, 16, SerenityDarkness.COLOR);
                double a = ticks * 0.04;
                for (int i = 1; i <= 3; i++) {
                    Location l = c.clone().add(Math.cos(a) * 0.15 * i, 0, Math.sin(a) * 0.15 * i);
                    t.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(SerenityDarkness.COLOR, 0.8f), true);
                }
                t.getWorld().spawnParticle(Particle.CHERRY_LEAVES, t.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0.005);
            }
        }.runTaskTimer(pl, 0L, 4L);
        return true;
    }

    @Override
    public void onShutdown() { slows.clear(); }

    /** Your hits against an enemy slowed by your Temporal Repose heal you for 50% of the damage dealt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (!(e.getDamager() instanceof Player p)) return;
        Slow s = slows.get(e.getEntity().getUniqueId());
        if (s == null) return;
        if (s.until < System.currentTimeMillis()) {
            slows.remove(e.getEntity().getUniqueId());
            return;
        }
        if (!s.caster.equals(p.getUniqueId())) return;

        double heal = e.getFinalDamage() * LIFESTEAL;
        if (heal <= 0) return;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20.0;
        if (p.getHealth() >= cap) return;
        p.setHealth(Math.min(cap, p.getHealth() + heal));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.0, 0), 1, 0.3, 0.2, 0.3, 0);
    }
}
