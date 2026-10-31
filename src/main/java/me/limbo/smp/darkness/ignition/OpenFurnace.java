package me.limbo.smp.darkness.ignition;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * L - charge for 1 second, building up fire (Slowness III, no jumping). Then shoot an extremely fast arrow of
 * flames that pierces everyone in its path: each enemy burns, takes 2 hearts of true damage and gets a scorch mark.
 * When you hit a scorch-marked enemy, right after the hit they are struck by 2 diagonal fire slashes (1 heart of
 * true damage each) and their shield and cobwebs are disabled for 3 seconds. 90s cooldown.
 */
public final class OpenFurnace extends Channel implements Listener {
    private static final String NAME = "Open Furnace";
    private static final int CHARGE_TICKS = 20;
    private static final double SPEED = 4.0;          // blocks per tick
    private static final double RANGE = 60.0;
    private static final double ARROW_DAMAGE = 4.0;   // 2 hearts
    private static final int FIRE_TICKS = 80;
    private static final long MARK_MS = 10_000L;      // how long a scorch mark waits to be hit
    private static final double SLASH_DAMAGE = 2.0;   // 1 heart per slash
    private static final int DISABLE_TICKS = 60;      // shield + cobwebs off for 3s

    private final Set<UUID> charging = new HashSet<>();
    private final Map<UUID, Long> marks = new HashMap<>();       // marked enemy -> expiry
    private final Map<UUID, UUID> markOwner = new HashMap<>();   // marked enemy -> who marked them
    private BukkitTask markTask;

    public OpenFurnace() {
        super("open_furnace", NAME, "Charged fire arrow that scorches enemies.", ChannelInput.L, 90_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        UUID id = p.getUniqueId();
        if (!charging.add(id)) return false;
        LimboPlugin pl = LimboPlugin.get();

        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CHARGE_TICKS + 4, 2, false, true, true));
        pl.status().ground(p, CHARGE_TICKS + 2); // can't jump while charging
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.2f, 0.6f);
        p.sendActionBar(Msg.parse("<#ff6e14>Charging..."));
        ensureMarkTask();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) {
                    charging.remove(id);
                    cancel();
                    return;
                }
                tick++;
                buildUp(p, tick);
                if (tick >= CHARGE_TICKS) {
                    charging.remove(id);
                    cancel();
                    release(p);
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
        return true;
    }

    /** Fire spiralling in toward the caster, tighter and hotter as the charge fills. */
    private void buildUp(Player p, int tick) {
        World w = p.getWorld();
        Location base = p.getLocation();
        double progress = tick / (double) CHARGE_TICKS;
        double radius = 2.2 * (1 - progress) + 0.35;
        for (int i = 0; i < 4; i++) {
            double a = tick * 0.55 + i * Math.PI / 2;
            Location l = base.clone().add(Math.cos(a) * radius, 0.2 + progress * 1.4, Math.sin(a) * radius);
            w.spawnParticle(Particle.FLAME, l, 1, 0, 0, 0, 0.01, null, true);
        }
        ParticleUtil.blissDust(base.clone().add(0, 1, 0), IgnitionDarkness.COLOR, 3, 0.35);
    }

    private void release(Player p) {
        // the slowness was only for the charge
        PotionEffect slow = p.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slow != null && slow.getAmplifier() == 2 && slow.getDuration() <= CHARGE_TICKS + 6) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location pos = eye.clone().add(dir.clone().multiply(1.0));
        w.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        w.playSound(eye, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        p.sendActionBar(Msg.parse("<#ff6e14>Open Furnace"));

        Set<UUID> hit = new HashSet<>();
        Predicate<Entity> filter = e -> e instanceof LivingEntity le && !e.equals(p) && !hit.contains(e.getUniqueId())
                && Targeting.canHarm(p, le) && Targeting.isEnemy(le);

        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                Location from = pos.clone();
                double remaining = SPEED;
                while (remaining > 0.01) {
                    RayTraceResult r = w.rayTrace(from, dir, remaining, FluidCollisionMode.NEVER, true, 0.4, filter);
                    if (r == null) break;
                    Vector hp = r.getHitPosition();
                    if (r.getHitEntity() instanceof LivingEntity target) {
                        hit.add(target.getUniqueId());
                        strike(p, target);
                        double used = hp.distance(from.toVector()) + 0.01;
                        from.add(dir.clone().multiply(used)); // keep going: the arrow pierces
                        remaining -= used;
                        continue;
                    }
                    // hit a block: the arrow is spent
                    Location at = hp.toLocation(w);
                    w.spawnParticle(Particle.FLAME, at, 12, 0.2, 0.2, 0.2, 0.05, null, true);
                    w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f);
                    cancel();
                    return;
                }
                drawArrow(w, pos, dir);
                pos.add(dir.clone().multiply(SPEED));
                travelled += SPEED;
                if (travelled >= RANGE) cancel();
            }
        }.runTaskTimer(pl(), 0L, 1L);
    }

    private static LimboPlugin pl() { return LimboPlugin.get(); }

    private void drawArrow(World w, Location pos, Vector dir) {
        Particle.DustTransition fire = new Particle.DustTransition(IgnitionDarkness.COLOR, ParticleUtil.VOID, 1.3f);
        for (double t = 0; t < SPEED; t += 0.5) {
            Location l = pos.clone().add(dir.clone().multiply(t));
            w.spawnParticle(Particle.FLAME, l, 1, 0.05, 0.05, 0.05, 0.01, null, true);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, fire, true);
        }
    }

    /** The arrow hits an enemy: burn, 2 hearts true damage, scorch mark. */
    private void strike(Player p, LivingEntity target) {
        target.setFireTicks(Math.max(target.getFireTicks(), FIRE_TICKS));
        TrueDamage.apply(p, target, ARROW_DAMAGE);
        marks.put(target.getUniqueId(), System.currentTimeMillis() + MARK_MS);
        markOwner.put(target.getUniqueId(), p.getUniqueId());
        Feedback.affected(target, NAME, p);
        ParticleUtil.burst(target.getLocation().add(0, 1, 0), IgnitionDarkness.COLOR, 20);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 0.8f);
        if (target instanceof Player tp) tp.sendActionBar(Msg.parse("<#ff6e14>Scorched"));
    }

    // ------------------------------------------------------------ scorch mark

    private void ensureMarkTask() {
        if (markTask != null) return;
        markTask = Bukkit.getScheduler().runTaskTimer(pl(), () -> {
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<UUID, Long>> it = marks.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> en = it.next();
                Entity e = Bukkit.getEntity(en.getKey());
                if (en.getValue() < now || !(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                    markOwner.remove(en.getKey());
                    it.remove();
                    continue;
                }
                Location l = le.getLocation().add(0, le.getHeight() + 0.4, 0);
                le.getWorld().spawnParticle(Particle.FLAME, l, 2, 0.15, 0.1, 0.15, 0.01, null, true);
            }
        }, 10L, 10L);
    }

    /** Hitting a scorch-marked enemy: shield/cobwebs off, then two quick diagonal fire slashes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity v)) return;
        Long expiry = marks.get(v.getUniqueId());
        if (expiry == null || expiry < System.currentTimeMillis()) return;
        if (!p.getUniqueId().equals(markOwner.get(v.getUniqueId()))) return;

        marks.remove(v.getUniqueId());
        markOwner.remove(v.getUniqueId());
        if (v instanceof Player vp) {
            vp.setCooldown(Material.SHIELD, DISABLE_TICKS);   // can't block
            vp.setCooldown(Material.COBWEB, DISABLE_TICKS);   // can't place webs
        }
        Bukkit.getScheduler().runTaskLater(pl(), () -> slash(p, v, 0), 3L);
        Bukkit.getScheduler().runTaskLater(pl(), () -> slash(p, v, 1), 6L);
    }

    /** One diagonal slash of fire across the target (the two slashes form an X). */
    private void slash(Player p, LivingEntity v, int index) {
        if (!v.isValid() || v.isDead() || !p.isOnline()) return;
        TrueDamage.apply(p, v, SLASH_DAMAGE);
        Feedback.affected(v, NAME, p);

        World w = v.getWorld();
        Location c = v.getBoundingBox().getCenter().toLocation(w);
        Vector toTarget = v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (toTarget.lengthSquared() < 1e-4) toTarget = new Vector(0, 0, 1);
        Vector side = toTarget.normalize().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector diag = side.clone().multiply(index == 0 ? 1 : -1).add(new Vector(0, 1, 0)).normalize();
        for (double t = -1.7; t <= 1.7; t += 0.2) {
            Location l = c.clone().add(diag.clone().multiply(t));
            w.spawnParticle(Particle.FLAME, l, 1, 0.03, 0.03, 0.03, 0.01, null, true);
            if (((int) Math.round(t * 5)) % 2 == 0) {
                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(ParticleUtil.VOID, 1.4f), true);
            }
        }
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);
    }

    @Override
    public void onShutdown() {
        if (markTask != null) markTask.cancel();
        markTask = null;
        marks.clear();
        markOwner.clear();
        charging.clear();
    }
}
