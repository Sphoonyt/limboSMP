package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * L - shoot a chain of crimson energy. It lashes onto the first enemy it hits and tethers them to you for 8s.
 * The tethered enemy has Weakness and Wither, your melee hits against them are auto-crits, and they can't get
 * more than 20 blocks away from you (they're launched back). 80s cooldown.
 */
public final class Enchain extends Channel implements Listener {
    private static final String NAME = "Enchain";
    private static final double SPEED = 2.0;        // blocks per tick
    private static final double RANGE = 28.0;
    private static final int TETHER_TICKS = 160;    // 8s
    private static final double LEASH = 20.0;
    private static final double CRIT_MULTIPLIER = 1.3;

    private static final class Tether {
        final UUID caster;
        final UUID target;
        int ticks = 0;
        BukkitTask task;
        Tether(UUID caster, UUID target) { this.caster = caster; this.target = target; }
    }

    /** caster -> their active tether (one at a time) */
    private final Map<UUID, Tether> tethers = new HashMap<>();

    public Enchain() {
        super("enchain", NAME, "Chain that tethers an enemy to you.", ChannelInput.L, 80_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location pos = eye.clone().add(dir.clone().multiply(0.8));

        w.playSound(eye, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.6f);
        w.playSound(eye, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);

        Predicate<Entity> filter = e -> e instanceof LivingEntity le && !e.equals(p)
                && Targeting.canHarm(p, le) && Targeting.isEnemy(le);

        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                RayTraceResult r = w.rayTrace(pos, dir, SPEED, FluidCollisionMode.NEVER, true, 0.35, filter);
                if (r != null) {
                    Location at = r.getHitPosition().toLocation(w);
                    drawLine(p.getLocation().add(0, 1.0, 0), at);
                    if (r.getHitEntity() instanceof LivingEntity target) lash(p, target);
                    else ParticleUtil.blissDust(at, EntropyDarkness.COLOR, 10, 0.2);
                    cancel();
                    return;
                }
                pos.add(dir.clone().multiply(SPEED));
                travelled += SPEED;
                drawLine(p.getLocation().add(0, 1.0, 0), pos); // the chain trails back to your hand
                if (travelled >= RANGE) cancel();
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------ the tether

    private void lash(Player caster, LivingEntity target) {
        end(caster.getUniqueId());
        Tether t = new Tether(caster.getUniqueId(), target.getUniqueId());
        tethers.put(caster.getUniqueId(), t);

        applyEffects(target, TETHER_TICKS);
        Feedback.affected(target, NAME, caster);
        ParticleUtil.burst(target.getLocation().add(0, 1, 0), EntropyDarkness.COLOR, 24);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.6f);
        caster.sendActionBar(Msg.parse("<#961428>Enchained"));
        if (target instanceof Player tp) tp.sendActionBar(Msg.parse("<dark_red>You are chained to " + Msg.esc(caster.getName())));

        t.task = new BukkitRunnable() {
            @Override public void run() {
                Player c = Bukkit.getPlayer(t.caster);
                Entity te = Bukkit.getEntity(t.target);
                t.ticks++;
                if (c == null || !c.isOnline() || c.isDead() || !(te instanceof LivingEntity victim)
                        || !victim.isValid() || victim.isDead() || victim.getWorld() != c.getWorld()
                        || t.ticks > TETHER_TICKS) {
                    end(t.caster);
                    return;
                }
                // 20 block leash: past it they're hauled back toward you
                Vector toCaster = c.getBoundingBox().getCenter().subtract(victim.getBoundingBox().getCenter());
                double dist = toCaster.length();
                if (dist > LEASH) {
                    Vector pull = toCaster.normalize().multiply(Math.min(2.2, 0.9 + (dist - LEASH) * 0.2));
                    pull.setY(pull.getY() + 0.25);
                    victim.setVelocity(pull);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.6f, 0.6f);
                }
                if (t.ticks % 20 == 0) applyEffects(victim, TETHER_TICKS - t.ticks); // keep it on if cleansed
                drawLine(c.getLocation().add(0, 1.0, 0), victim.getBoundingBox().getCenter().toLocation(c.getWorld()));
            }
        }.runTaskTimer(LimboPlugin.get(), 1L, 1L);
    }

    private void applyEffects(LivingEntity target, int ticks) {
        if (ticks <= 0) return;
        PotionEffect weak = target.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weak == null || weak.getDuration() < ticks - 5) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0, false, true, true));
        }
        PotionEffect wither = target.getPotionEffect(PotionEffectType.WITHER);
        if (wither == null || wither.getDuration() < ticks - 5) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, ticks, 0, false, true, true));
        }
    }

    private void end(UUID casterId) {
        Tether t = tethers.remove(casterId);
        if (t == null) return;
        if (t.task != null) t.task.cancel();
        Player c = Bukkit.getPlayer(casterId);
        if (c != null && c.isOnline()) c.sendActionBar(Msg.parse("<gray>The chain breaks."));
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(tethers.keySet())) end(id);
    }

    /** A chain of alternating crimson and black links between two points. */
    private void drawLine(Location from, Location to) {
        World w = from.getWorld();
        Vector v = to.toVector().subtract(from.toVector());
        double len = v.length();
        if (len < 0.5) return;
        Vector step = v.clone().normalize().multiply(0.45);
        Particle.DustOptions crimson = new Particle.DustOptions(EntropyDarkness.COLOR, 1.0f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.1f);
        Location l = from.clone();
        int n = (int) (len / 0.45);
        for (int i = 0; i < n; i++) {
            l.add(step);
            w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, (i % 2 == 0) ? crimson : dark, true);
        }
    }

    // ------------------------------------------------------------ auto-crit

    /** While chained, your melee hits on that enemy always crit (x1.5 unless it already was a vanilla crit). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        Tether t = tethers.get(p.getUniqueId());
        if (t == null || !e.getEntity().getUniqueId().equals(t.target)) return;

        boolean vanillaCrit = p.getFallDistance() > 0f && !p.isOnGround() && !p.isSprinting()
                && !p.isClimbing() && !p.isInWater() && !p.isInsideVehicle();
        if (!vanillaCrit) e.setDamage(e.getDamage() * CRIT_MULTIPLIER);

        Location at = e.getEntity().getLocation().add(0, 1, 0);
        ParticleUtil.blissDust(at, EntropyDarkness.COLOR, 8, 0.3);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.9f);
    }
}
