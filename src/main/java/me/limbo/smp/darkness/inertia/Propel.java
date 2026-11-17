package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * R - a regular, very fast ~12 block dash in the direction you're looking (up and down included). Anything
 * you dash through takes 2 hearts of true damage (once each). It eases out into a short glide instead of
 * stopping dead, and you're immune to all knockback for 5s once it ends. 35s cooldown.
 */
public final class Propel extends Channel {
    private static final String NAME = "Propel";
    private static final double DISTANCE = 12.0;
    private static final double SPEED = 3.8;       // blocks per tick (~76 blocks/s), just under the 3.9 velocity packet cap
    private static final int MAX_TICKS = 8;
    private static final int IMMUNITY_TICKS = 100; // 5s
    private static final double THROUGH_DAMAGE = 4.0; // 2 hearts
    private static final double HIT_RADIUS = 2.4; // generous on purpose: at ~76 blocks/s a tight hitbox misses people you clearly passed

    public Propel() {
        super("propel", NAME, "Fast dash where you look. Hurts what you pass through.", ChannelInput.R, 35_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        LimboPlugin pl = LimboPlugin.get();

        // a normal dash: exactly where you're looking
        final Vector direction = p.getLocation().getDirection().normalize();
        final Location start = p.getLocation().clone();

        me.limbo.smp.util.Sfx.play(start, org.bukkit.Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.6f, me.limbo.smp.util.Sfx.Theme.INERTIA);
        me.limbo.smp.util.Sfx.add(start, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        ParticleUtil.blissDust(start.clone().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 14, 0.5);

        new BukkitRunnable() {
            Location last = start.clone();
            int ticks = 0;
            int stalled = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                Location now = p.getLocation();
                if (now.getWorld() != start.getWorld()) { finish(p, direction); cancel(); return; }

                double travelled = now.distance(start);
                if (ticks > 0) stalled = (now.distance(last) < SPEED * 0.15) ? stalled + 1 : 0; // hit a wall
                damageAlong(p, ticks == 0 ? start : last, now, hit); // every tick, including the first
                trail(now);

                if (travelled >= DISTANCE - 0.3 || stalled >= 2 || ticks >= MAX_TICKS) {
                    damageAlong(p, now, now.clone().add(direction.clone().multiply(1.5)), hit); // the last stretch of the glide
                    finish(p, direction);
                    cancel();
                    return;
                }
                double speed = Math.min(SPEED, DISTANCE - travelled);
                p.setVelocity(direction.clone().multiply(speed));
                last = now.clone();
                ticks++;
            }
        }.runTaskTimer(pl, 0L, 1L);
        return true;
    }

    /** Everything within 1.5 blocks of the path travelled this tick takes 2 hearts of true damage (once per dash). */
    private void damageAlong(Player p, Location from, Location to, Set<UUID> hit) {
        World w = from.getWorld();
        Vector a = from.clone().toVector();
        Vector b = to.clone().toVector();
        Vector ab = b.clone().subtract(a);
        double len = ab.length();
        BoundingBox area = BoundingBox.of(a.clone().add(new Vector(0, 1, 0)), b.clone().add(new Vector(0, 1, 0))).expand(HIT_RADIUS + 2.0);
        for (Entity en : w.getNearbyEntities(area)) {
            if (!(en instanceof LivingEntity le) || le.equals(p) || hit.contains(le.getUniqueId())) continue;
            if (!Targeting.canHarm(p, le) || !Targeting.isEnemy(le)) continue;
            // sweep a body-sized probe along the path and test it against the target's hitbox grown by HIT_RADIUS
            BoundingBox target = le.getBoundingBox().expand(HIT_RADIUS * 0.7);
            boolean touched = false;
            int steps = Math.max(1, (int) Math.ceil(len / 0.5));
            for (int i = 0; i <= steps && !touched; i++) {
                Vector at = a.clone().add(ab.clone().multiply(i / (double) steps));
                for (double h : new double[]{0.4, 1.0, 1.6}) if (target.contains(at.getX(), at.getY() + h, at.getZ())) { touched = true; break; }
            }
            if (!touched) continue;
            hit.add(le.getUniqueId());
            if (TrueDamage.apply(p, le, THROUGH_DAMAGE)) {
                Feedback.affected(le, NAME, p);
                ParticleUtil.blissDust(le.getLocation().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 14, 0.4);
                // the impact is a flat starburst model that pops open on them, spun a random amount each time
                me.limbo.smp.util.FlatFx.pop(le.getBoundingBox().getCenter().toLocation(w), me.limbo.smp.util.FlatFx.IMPACT,
                        org.bukkit.entity.Display.Billboard.CENTER, 0.6f, 3.2f, 5,
                        (float) (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * Math.PI));
                me.limbo.smp.util.Sfx.play(le.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.8f, me.limbo.smp.util.Sfx.Theme.INERTIA);
            }
        }
    }

    private static double distanceToSegment(Vector a, Vector b, Vector p) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / len2));
        return a.clone().add(ab.multiply(t)).distance(p);
    }

    private void trail(Location at) {
        ParticleUtil.blissDust(at.clone().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 10, 0.35);
    }

    private void finish(Player p, Vector direction) {
        p.setVelocity(direction.clone().multiply(0.9)); // ease out into a short glide (no hard stop)
        LimboPlugin.get().status().knockbackImmunity(p, IMMUNITY_TICKS);
        ParticleUtil.blissDust(p.getLocation().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 14, 0.5);
        me.limbo.smp.util.Sfx.play(p.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BLOCK, 1f, 0.5f, me.limbo.smp.util.Sfx.Theme.INERTIA);
    }
}
