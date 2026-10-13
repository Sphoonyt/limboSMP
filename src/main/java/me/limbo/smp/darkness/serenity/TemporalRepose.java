package me.limbo.smp.darkness.serenity;

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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * H - the enemy hit has their tick rate lowered for 6s (only them). Players get a real client tick-rate
 * drop via packet; everything else falls back to attribute scaling (see StatusManager#slowTime).
 * Tune in config.yml: temporal-repose.time-scale (default 0.6 = 12 tps), duration-seconds, mode.
 * 50s cooldown.
 */
public final class TemporalRepose extends Channel {

    public TemporalRepose() {
        super("temporal_repose", "Temporal Repose",
                "The enemy you hit has their tick rate lowered for 6s: they and only they move, jump, fall and attack in slow motion.",
                ChannelInput.H, 50_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        LivingEntity t = ctx.target();
        if (t == null) return false;
        LimboPlugin pl = LimboPlugin.get();
        final int durationTicks = (int) (pl.getConfig().getDouble("temporal-repose.duration-seconds", 6.0) * 20);
        pl.status().slowTime(t, durationTicks);
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
                    t.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
                }
                t.getWorld().spawnParticle(Particle.CHERRY_LEAVES, t.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0.005);
            }
        }.runTaskTimer(pl, 0L, 4L);
        return true;
    }
}
