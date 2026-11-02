package me.limbo.smp.darkness.serenity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.SerenityDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * R - a 7 block zone that stays where you cast it for 10s. Every 2 seconds enemies inside take 1 heart of true
 * damage and allies (you and players you trust) heal 1 heart. 90s cooldown.
 */
public final class ImbalanceRing extends Channel {
    private static final String NAME = "Imbalance Ring";
    private static final double RADIUS = 7.0;
    private static final int DURATION_TICKS = 200;  // 10s
    private static final int PULSE_TICKS = 40;      // every 2s
    private static final double DAMAGE = 2.0;       // 1 heart
    private static final double HEAL = 2.0;         // 1 heart

    private final Set<Cast> casts = new HashSet<>();

    public ImbalanceRing() {
        super("imbalance_ring", NAME, "Zone that hurts enemies and heals allies.", ChannelInput.R, 90_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        Cast c = new Cast(p);
        casts.add(c);
        c.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.4f);
        p.sendActionBar(Msg.parse("<#ffafcd>Imbalance Ring"));
        return true;
    }

    @Override
    public void onShutdown() {
        for (Cast c : new ArrayList<>(casts)) c.finish();
    }

    private final class Cast extends BukkitRunnable {
        private final Player caster;
        private final World w;
        private final Location center;
        private int ticks = 0;
        private boolean done = false;

        Cast(Player caster) {
            this.caster = caster;
            this.w = caster.getWorld();
            this.center = caster.getLocation().clone(); // fixed: the zone does not follow you
        }

        @Override
        public void run() {
            if (!caster.isOnline() || ticks >= DURATION_TICKS) {
                finish();
                return;
            }
            ticks += 2;
            draw();
            if (ticks % PULSE_TICKS == 0) pulse();
        }

        /** Pink outer ring (the mending side), a dark inner ring that spins the other way (the harming side). */
        private void draw() {
            Location ground = center.clone().add(0, 0.1, 0);
            ParticleUtil.ring(ground, RADIUS, 64, SerenityDarkness.COLOR);
            Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.4f);
            double spin = -ticks * 0.08;
            for (int i = 0; i < 28; i++) {
                double a = spin + 2 * Math.PI * i / 28;
                Location l = ground.clone().add(Math.cos(a) * RADIUS * 0.55, 0, Math.sin(a) * RADIUS * 0.55);
                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dark, true);
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                double a = rnd.nextDouble(Math.PI * 2);
                double r = Math.sqrt(rnd.nextDouble()) * RADIUS;
                w.spawnParticle(Particle.CHERRY_LEAVES, ground.clone().add(Math.cos(a) * r, 0.8 + rnd.nextDouble(1.5), Math.sin(a) * r),
                        1, 0.1, 0.1, 0.1, 0.01, null, true);
            }
        }

        private void pulse() {
            double r2 = RADIUS * RADIUS;
            for (LivingEntity e : w.getNearbyLivingEntities(center, RADIUS)) {
                if (e.getBoundingBox().getCenter().distanceSquared(center.toVector()) > r2) continue;
                if (isAlly(e)) {
                    heal(e);
                } else if (Targeting.canHarm(caster, e) && Targeting.isEnemy(e)) {
                    if (TrueDamage.apply(caster, e, DAMAGE)) {
                        ParticleUtil.blissDust(e.getLocation().add(0, 1, 0), ParticleUtil.VOID, SerenityDarkness.COLOR, 8, 0.3);
                        Feedback.affected(e, NAME, caster);
                    }
                }
            }
            w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f);
        }

        /** You and the players you trust. */
        private boolean isAlly(LivingEntity e) {
            if (!(e instanceof Player pl)) return false;
            return pl.getUniqueId().equals(caster.getUniqueId())
                    || LimboPlugin.get().trust().isTrusting(caster.getUniqueId(), pl.getUniqueId());
        }

        private void heal(LivingEntity e) {
            AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
            double cap = max != null ? max.getValue() : 20.0;
            if (e.getHealth() >= cap) return;
            e.setHealth(Math.min(cap, e.getHealth() + HEAL));
            w.spawnParticle(Particle.HEART, e.getLocation().add(0, 2.0, 0), 2, 0.3, 0.2, 0.3, 0);
            if (e instanceof Player pl) Feedback.affected(pl, NAME, caster);
        }

        void finish() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) {}
            casts.remove(this);
        }
    }
}
