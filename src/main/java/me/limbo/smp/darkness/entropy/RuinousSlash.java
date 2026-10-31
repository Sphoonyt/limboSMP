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
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R - a LARGE horizontal slash in front of you. It costs 2 hearts of your own health. Every enemy it hits takes
 * 2 hearts of true damage; for each one you get 2 hearts back (as absorption if you're already full) and 5 seconds
 * of +1.5 attack damage (stacking per enemy). 80s cooldown.
 */
public final class RuinousSlash extends Channel {
    private static final String NAME = "Ruinous Slash";
    private static final double RANGE = 6.5;
    private static final double HALF_ARC = Math.toRadians(80);   // 160 degrees wide
    private static final double VERTICAL = 2.4;                  // how far above/below you it still reaches
    private static final double SACRIFICE = 4.0;                 // 2 hearts
    private static final double DAMAGE = 4.0;                    // 2 hearts
    private static final double HEAL = 4.0;                      // 2 hearts per enemy
    private static final double BONUS_PER_ENEMY = 1.5;
    private static final int BONUS_TICKS = 100;                  // 5s

    private final NamespacedKey bonusKey;
    private final Map<UUID, BukkitTask> bonusTasks = new HashMap<>();

    public RuinousSlash() {
        super("ruinous_slash", NAME, "Costs health. Large slash that drains enemies.", ChannelInput.R, 80_000L);
        this.bonusKey = new NamespacedKey(LimboPlugin.get(), "ruinous_bonus");
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        if (p.getHealth() <= SACRIFICE + 0.5) { // never kill yourself with it
            p.sendActionBar(Msg.parse("<dark_red>Not enough health"));
            return false;
        }
        World w = p.getWorld();

        // the sacrifice
        p.setHealth(p.getHealth() - SACRIFICE);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9f, 0.7f);

        Vector forward = p.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) forward = new Vector(0, 0, 1);
        forward.normalize();
        Location center = p.getLocation().add(0, 1.0, 0);

        int hits = 0;
        for (LivingEntity e : w.getNearbyLivingEntities(center, RANGE + 2)) {
            if (e.equals(p) || !Targeting.canHarm(p, e) || !Targeting.isEnemy(e)) continue;
            Vector to = e.getBoundingBox().getCenter().subtract(center.toVector());
            if (Math.abs(to.getY()) > VERTICAL) continue;
            Vector flat = to.clone().setY(0);
            double dist = flat.length();
            if (dist > RANGE) continue;
            if (dist > 0.6 && forward.angle(flat) > HALF_ARC) continue; // outside the arc
            if (!TrueDamage.apply(p, e, DAMAGE)) continue;

            hits++;
            Feedback.affected(e, NAME, p);
            ParticleUtil.burst(e.getLocation().add(0, 1, 0), EntropyDarkness.COLOR, 20);
            gainHealth(p, HEAL);
        }
        if (hits > 0) addAttackBonus(p, BONUS_PER_ENEMY * hits);
        p.sendActionBar(Msg.parse("<#961428>Ruinous Slash <gray>(" + hits + ")"));

        sweep(p, center, forward);
        return true;
    }

    /** Healing first; whatever doesn't fit becomes absorption. */
    private void gainHealth(Player p, double amount) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20.0;
        double heal = Math.min(amount, Math.max(0, cap - p.getHealth()));
        p.setHealth(p.getHealth() + heal);
        double overflow = amount - heal;
        if (overflow > 0) p.setAbsorptionAmount(p.getAbsorptionAmount() + overflow);
    }

    /** +attack damage for 5 seconds (one modifier carrying the total for all enemies hit). */
    private void addAttackBonus(Player p, double amount) {
        UUID id = p.getUniqueId();
        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk == null) return;
        removeBonus(p);
        atk.addTransientModifier(new AttributeModifier(bonusKey, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        bonusTasks.put(id, Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () -> {
            bonusTasks.remove(id);
            removeBonus(p);
        }, BONUS_TICKS));
    }

    private void removeBonus(Player p) {
        BukkitTask t = bonusTasks.remove(p.getUniqueId());
        if (t != null) t.cancel();
        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk == null) return;
        for (AttributeModifier m : new ArrayList<>(atk.getModifiers())) {
            if (m.getKey().equals(bonusKey)) atk.removeModifier(m);
        }
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(bonusTasks.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) removeBonus(p);
        }
        bonusTasks.clear();
    }

    /** The slash itself: a big crescent that sweeps across in 4 ticks, on three heights. */
    private void sweep(Player p, Location center, Vector forward) {
        World w = center.getWorld();
        Particle.DustTransition edge = new Particle.DustTransition(EntropyDarkness.COLOR, ParticleUtil.VOID, 1.5f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.8f);
        double[] heights = {-0.7, 0.0, 0.7};

        new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                step++;
                double progress = step / 4.0;
                double swept = -HALF_ARC + 2 * HALF_ARC * progress; // from the right edge to the left
                double prev = -HALF_ARC + 2 * HALF_ARC * (step - 1) / 4.0;
                for (double h : heights) {
                    for (double a = prev; a <= swept; a += Math.toRadians(5)) {
                        Vector dir = rotate(forward, a);
                        for (double r : new double[]{RANGE, RANGE * 0.7}) {
                            Location l = center.clone().add(dir.clone().multiply(r)).add(0, h, 0);
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, edge, true);
                            if (r == RANGE) w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.05, 0.05, 0, dark, true);
                        }
                    }
                }
                if (step >= 4) cancel();
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    private static Vector rotate(Vector v, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vector(v.getX() * cos - v.getZ() * sin, v.getY(), v.getX() * sin + v.getZ() * cos);
    }
}
