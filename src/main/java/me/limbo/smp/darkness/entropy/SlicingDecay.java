package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * R - a huge diagonal slash flies forward 20 blocks, through blocks and players. Players it hits are
 * marked; after 1s they take 1 heart true damage and are suppressed for 4s (absorption + regeneration
 * repeatedly cleared). 50s cooldown.
 */
public final class SlicingDecay extends Channel {
    private static final double HALF_LENGTH = 4.0;   // slash is 8 blocks long
    private static final double THICKNESS = 1.2;
    private static final double SPEED = 1.25;        // blocks per tick
    private static final double MAX_DISTANCE = 20.0;
    private static final int DELAY_TICKS = 20;       // 1s
    private static final double DAMAGE = 2.0;        // 1 heart
    private static final int SUPPRESS_TICKS = 80;    // 4s

    private final Map<UUID, BukkitTask> suppressions = new HashMap<>();

    public SlicingDecay() {
        super("slicing_decay", "Slicing Decay",
                "Fire a huge diagonal slash 20 blocks through blocks and players. Hit players take 1 heart true damage after 1s and are suppressed (no absorption/regen) for 4s.",
                ChannelInput.R, 50_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        LimboPlugin pl = LimboPlugin.get();
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();

        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();
        final Vector axis = right.clone().add(up).normalize(); // 45 degree diagonal

        w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        w.playSound(eye, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.6f);
        p.sendActionBar(Msg.parse("<#961428>Slicing Decay"));

        final Location pos = eye.clone().add(fwd.clone().multiply(1.5));
        final Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                if (!p.isOnline() || travelled > MAX_DISTANCE) { cancel(); return; }
                pos.add(fwd.clone().multiply(SPEED));
                travelled += SPEED;
                draw(w, pos, fwd, axis);
                checkHits(p, w, pos, fwd, axis, hit);
            }
        }.runTaskTimer(pl, 0L, 1L);
        return true;
    }

    private void draw(World w, Location pos, Vector fwd, Vector axis) {
        Particle.DustTransition fade = new Particle.DustTransition(ParticleUtil.VOID, EntropyDarkness.COLOR, 1.8f);
        for (int layer = 0; layer < 2; layer++) {
            Location center = pos.clone().subtract(fwd.clone().multiply(layer * 0.6));
            int idx = 0;
            for (double t = -HALF_LENGTH; t <= HALF_LENGTH; t += 0.35) {
                Location l = center.clone().add(axis.clone().multiply(t));
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0.05, 0.05, 0.05, 0, fade, true);
                if (layer == 0 && idx % 3 == 0) {
                    w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(ParticleUtil.VOID, 2.0f), true);
                }
                if (layer == 0 && idx % 7 == 0) {
                    w.spawnParticle(Particle.CRIT, l, 1, 0.1, 0.1, 0.1, 0.05, null, true);
                }
                idx++;
            }
        }
    }

    private void checkHits(Player caster, World w, Location pos, Vector fwd, Vector axis, Set<UUID> hit) {
        for (Player t : w.getNearbyPlayers(pos, HALF_LENGTH + 3)) {
            if (t.equals(caster) || hit.contains(t.getUniqueId())) continue;
            Vector d = t.getBoundingBox().getCenter().subtract(pos.toVector());
            double f = d.dot(fwd);
            if (Math.abs(f) > SPEED) continue;
            double tt = d.dot(axis);
            if (Math.abs(tt) > HALF_LENGTH + 0.4) continue;
            Vector perp = d.clone().subtract(fwd.clone().multiply(f)).subtract(axis.clone().multiply(tt));
            if (perp.length() > THICKNESS) continue;
            if (!Targeting.canHarm(caster, t)) continue;
            hit.add(t.getUniqueId());
            mark(caster, t);
        }
    }

    private void mark(Player caster, Player victim) {
        LimboPlugin pl = LimboPlugin.get();
        victim.sendActionBar(Msg.parse("<dark_red>You have been cut. Decay takes hold..."));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 0.7f);
        ParticleUtil.burst(victim.getLocation().add(0, 1, 0), EntropyDarkness.COLOR, 20);

        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!victim.isOnline() || victim.isDead() || !caster.isOnline()) return;
            TrueDamage.apply(caster, victim, DAMAGE);
            if (victim.isDead()) return;
            ParticleUtil.burst(victim.getLocation().add(0, 1, 0), EntropyDarkness.COLOR, 30);
            suppress(victim);
        }, DELAY_TICKS);
    }

    private void suppress(Player victim) {
        LimboPlugin pl = LimboPlugin.get();
        UUID id = victim.getUniqueId();
        BukkitTask old = suppressions.remove(id);
        if (old != null) old.cancel();
        victim.sendActionBar(Msg.parse("<dark_red>Suppressed"));
        BukkitTask t = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 2;
                if (!victim.isOnline() || victim.isDead() || ticks > SUPPRESS_TICKS) {
                    BukkitTask me = suppressions.remove(id);
                    if (me != null) me.cancel();
                    return;
                }
                victim.removePotionEffect(PotionEffectType.REGENERATION);
                victim.removePotionEffect(PotionEffectType.ABSORPTION);
                victim.setAbsorptionAmount(0);
                if (ticks % 10 == 0) ParticleUtil.blissDust(victim.getLocation().add(0, 1, 0), EntropyDarkness.COLOR, 5, 0.4);
            }
        }.runTaskTimer(pl, 0L, 2L);
        suppressions.put(id, t);
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(suppressions.keySet())) {
            BukkitTask t = suppressions.remove(id);
            if (t != null) t.cancel();
        }
    }
}
