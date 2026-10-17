package me.limbo.smp.darkness.reflection;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R - ready a 0.7s parry. Visual: 3 expanding circles in front of you (small near, medium middle, large far).
 * A hit during the window is negated and the attacker takes the same damage back (true damage, credit to you).
 * Success: the 3 circles burst outward at the attacker, webs within 3 blocks are cleared, Speed IV for 30 ticks,
 * cooldown drops from 35s to 15s. A miss keeps the 35s cooldown.
 */
public final class Parry extends Channel implements Listener {
    private static final String CD_KEY = ReflectionDarkness.ID + ":parry";
    private static final int WINDOW_TICKS = 14;        // 0.7s
    private static final long SUCCESS_COOLDOWN_MS = 15_000L;
    private static final int SPEED_TICKS = 30;
    private static final int SPEED_AMPLIFIER = 3;      // Speed IV
    private static final double WEB_RADIUS = 3.0;

    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public Parry() {
        super("parry", "Parry", "Brief window that reflects the next hit.", ChannelInput.R, 35_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        UUID id = p.getUniqueId();
        end(id);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.6f);

        BukkitTask t = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { end(id); return; }
                if (ticks >= WINDOW_TICKS) {
                    end(id);
                    p.sendActionBar(Msg.parse("<gray>Parry missed."));
                    return;
                }
                drawReady(p, ticks / (double) WINDOW_TICKS);
                ticks += 2;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        active.put(id, t);
        p.sendActionBar(Msg.parse("<#a5cdff>Parry"));
        return true; // starts the 35s failure cooldown; success shortens it
    }

    private void end(UUID id) {
        BukkitTask t = active.remove(id);
        if (t != null) t.cancel();
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) end(id);
    }

    // ------------------------------------------------------------ the parry itself

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (!(e.getEntity() instanceof Player p) || !active.containsKey(p.getUniqueId())) return;
        DamageCause c = e.getCause();
        if (c != DamageCause.ENTITY_ATTACK && c != DamageCause.ENTITY_SWEEP_ATTACK && c != DamageCause.PROJECTILE) return;

        Entity damager = e.getDamager();
        LivingEntity attacker = null;
        if (damager instanceof LivingEntity le) attacker = le;
        else if (damager instanceof Projectile pr && pr.getShooter() instanceof LivingEntity shooter) attacker = shooter;
        if (attacker == null || attacker.equals(p)) return;

        double dmg = e.getFinalDamage();
        e.setCancelled(true);
        if (damager instanceof Projectile) damager.remove();
        success(p, attacker, dmg);
    }

    private void success(Player p, LivingEntity attacker, double damage) {
        LimboPlugin pl = LimboPlugin.get();
        UUID id = p.getUniqueId();
        end(id);
        pl.dispatcher().cooldowns().set(id, CD_KEY, SUCCESS_COOLDOWN_MS);

        // hit it right back (same damage, true damage so armor doesn't change the number)
        if (damage > 0) {
            Bukkit.getScheduler().runTask(pl, () -> {
                if (attacker.isValid() && !attacker.isDead()) TrueDamage.apply(p, attacker, damage);
            });
        }
        clearWebs(p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_TICKS, SPEED_AMPLIFIER, false, true, true));

        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.4f);
        burst(p, attacker);
        p.sendActionBar(Msg.parse("<#a5cdff>Parried!"));
        if (attacker instanceof Player ap) ap.sendActionBar(Msg.parse("<#a5cdff>Your hit was reflected!"));
    }

    private void clearWebs(Player p) {
        Location c = p.getLocation();
        World w = c.getWorld();
        int r = (int) Math.ceil(WEB_RADIUS);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > WEB_RADIUS * WEB_RADIUS) continue;
                    Block b = w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz);
                    if (b.getType() != Material.COBWEB) continue;
                    b.setType(Material.AIR, false);
                    w.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(ReflectionDarkness.WHITE, 1.0f), true);
                }
            }
        }
    }

    // ------------------------------------------------------------ visuals

    /** Three circles facing away from you that grow while the window is open: near = small, far = large. */
    private void drawReady(Player p, double t) {
        Location origin = p.getEyeLocation().subtract(0, 0.35, 0);
        Vector dir = origin.getDirection().normalize();
        Vector[] basis = basis(dir);
        for (int i = 0; i < 3; i++) {
            double dist = 1.2 + 0.85 * i + 0.35 * t;
            double radius = (0.40 + 0.38 * i) * (0.75 + 0.45 * t);
            Location center = origin.clone().add(dir.clone().multiply(dist));
            ring(center, basis[0], basis[1], radius, 28);
        }
    }

    /** On a successful parry the same three circles shoot outward at the attacker, widening as they go. */
    private void burst(Player p, LivingEntity target) {
        Location origin = p.getEyeLocation().subtract(0, 0.35, 0);
        Location aim = target.getLocation().add(0, target.getHeight() / 2, 0);
        Vector toTarget = aim.toVector().subtract(origin.toVector());
        double targetDist = toTarget.length();
        if (targetDist < 0.5) return;
        Vector dir = toTarget.normalize();
        Vector[] basis = basis(dir);

        new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                step++;
                boolean any = false;
                for (int i = 0; i < 3; i++) {
                    double dist = 1.2 + 0.85 * i + step * (0.9 + 0.25 * i);
                    if (dist > targetDist + 0.8) continue;
                    any = true;
                    double radius = (0.40 + 0.38 * i) + step * 0.22;
                    ring(origin.clone().add(dir.clone().multiply(dist)), basis[0], basis[1], radius, 34);
                }
                if (!any || step >= 10) {
                    ParticleUtil.blissDust(aim, ReflectionDarkness.COLOR, 26, 0.5);
                    cancel();
                }
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    private static Vector[] basis(Vector dir) {
        Vector u = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (u.lengthSquared() < 1e-4) u = new Vector(1, 0, 0);
        u.normalize();
        Vector v = dir.clone().crossProduct(u).normalize();
        return new Vector[]{u, v};
    }

    private static void ring(Location center, Vector u, Vector v, double radius, int points) {
        World w = center.getWorld();
        Particle.DustTransition fade = new Particle.DustTransition(ReflectionDarkness.WHITE, ReflectionDarkness.COLOR, 1.0f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.2f);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            double cos = Math.cos(a) * radius, sin = Math.sin(a) * radius;
            Location l = new Location(w,
                    center.getX() + u.getX() * cos + v.getX() * sin,
                    center.getY() + u.getY() * cos + v.getY() * sin,
                    center.getZ() + u.getZ() * cos + v.getZ() * sin);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, fade, true);
            if (i % 3 == 0) w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dark, true); // darkness mixed in
        }
    }
}
