package me.limbo.smp.darkness.reflection;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import me.limbo.smp.util.FlatFx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * R - for 7 seconds your darkness shatters the glass of space in a 4 block radius that moves with you.
 * Swinging at AIR connects with every enemy inside the radius for 20% less damage than a normal hit
 * (normal hits are unaffected). These hits can never trigger Reflected Constructs' clone echo.
 * 90s cooldown.
 */
public final class RealityShatter extends Channel implements Listener {
    private static final String NAME = "Reality Shatter";
    private static final int DURATION_TICKS = 140;   // 7s
    private static final double RADIUS = 4.0;
    private static final double DAMAGE_FACTOR = 0.8; // 20% reduced

    /** True while a shatter hit is being dealt, so listeners (the clone echo) can ignore it. */
    private static boolean shatterHit = false;

    public static boolean isShatterHit() { return shatterHit; }

    private final Map<UUID, BukkitTask> active = new HashMap<>();

    private static final int SHARDS = 12;

    /** One energy shard flying around the dome. */
    private static final class Shard {
        final ItemDisplay display;
        final double lon0, latAmp, latSpeed, latPhase, lonSpeed, size, spin;

        Shard(ItemDisplay display, ThreadLocalRandom r) {
            this.display = display;
            this.lon0 = r.nextDouble(Math.PI * 2);
            this.latAmp = r.nextDouble(0.35, 1.2);              // how far up and down it swings (radians)
            this.latSpeed = r.nextDouble(0.02, 0.05) * (r.nextBoolean() ? 1 : -1);
            this.latPhase = r.nextDouble(Math.PI * 2);
            this.lonSpeed = r.nextDouble(0.035, 0.08) * (r.nextBoolean() ? 1 : -1);
            this.size = r.nextDouble(0.55, 1.1);
            this.spin = r.nextDouble(0.2, 0.5);
        }

        /** Point on the dome at tick t (dome centre = origin, unit radius). */
        Vector at(double t) {
            double lat = latAmp * Math.sin(t * latSpeed + latPhase);
            double lon = lon0 + t * lonSpeed;
            return new Vector(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
        }
    }

    private final Map<UUID, List<Shard>> shards = new HashMap<>();

    public RealityShatter() {
        super("reality_shatter", NAME, "Air swings hit every enemy nearby.", ChannelInput.R, 90_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        UUID id = p.getUniqueId();
        end(id);
        List<Shard> mine = new ArrayList<>();
        ThreadLocalRandom rnd0 = ThreadLocalRandom.current();
        for (int i = 0; i < SHARDS; i++) {
            mine.add(new Shard(FlatFx.spawn(p.getLocation().add(0, 1, 0), FlatFx.SHARD, Display.Billboard.FIXED), rnd0));
        }
        shards.put(id, mine);
        BukkitTask t = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 2;
                if (!p.isOnline() || p.isDead() || ticks > DURATION_TICKS) {
                    end(id);
                    return;
                }
                draw(p, ticks);
                flyShards(p, mine, ticks);
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        active.put(id, t);
        me.limbo.smp.util.Sfx.play(p.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f, me.limbo.smp.util.Sfx.Theme.REFLECTION);
        me.limbo.smp.util.Sfx.add(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.5f);
        return true;
    }

    private void end(UUID id) {
        BukkitTask t = active.remove(id);
        if (t != null) t.cancel();
        List<Shard> s = shards.remove(id);
        if (s != null) for (Shard sh : s) FlatFx.remove(sh.display);
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) end(id);
    }

    // ------------------------------------------------------------ the swing

    /** Swinging at air (no entity, no block) connects with every enemy inside the radius. */
    @EventHandler
    public void onSwing(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!active.containsKey(p.getUniqueId())) return;

        double damage = baseDamage(p) * DAMAGE_FACTOR;
        World w = p.getWorld();
        Location center = p.getLocation().add(0, 1.0, 0);
        double r2 = RADIUS * RADIUS;
        for (LivingEntity target : w.getNearbyLivingEntities(center, RADIUS)) {
            if (target.equals(p) || Targeting.isClone(target)) continue;
            if (!Targeting.canHarm(p, target) || !Targeting.isEnemy(target)) continue;
            if (target.getBoundingBox().getCenter().distanceSquared(center.toVector()) > r2) continue;

            shatterHit = true;
            try {
                target.damage(damage, p);
            } finally {
                shatterHit = false;
            }
            Feedback.affected(target, NAME, p);
            Location at = target.getLocation().add(0, 1, 0);
            w.spawnParticle(Particle.BLOCK, at, 6, 0.3, 0.4, 0.3, 0, Material.GLASS.createBlockData(), true);
            ParticleUtil.blissDust(at, ReflectionDarkness.COLOR, 6, 0.3);
            w.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.4f);
        }
    }

    /** What a normal hit with the held item would roughly deal (attribute includes the weapon + Strength). */
    private double baseDamage(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double base = a != null ? a.getValue() : 1.0;
        ItemStack held = p.getInventory().getItemInMainHand();
        int sharp = held.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharp > 0) base += 0.5 * sharp + 0.5;
        return base;
    }

    // ------------------------------------------------------------ visuals

    /** A shell of cracked glass around you: dust points, jagged crack lines and falling shards. */
    private void draw(Player p, int ticks) {
        World w = p.getWorld();
        Location c = p.getLocation().add(0, 1.0, 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Particle.DustTransition shell = new Particle.DustTransition(ReflectionDarkness.WHITE, ReflectionDarkness.COLOR, 0.9f);
        Particle.DustOptions glint = new Particle.DustOptions(ReflectionDarkness.WHITE, 0.6f);
        Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.0f);
        BlockData glass = Material.GLASS.createBlockData();

        int n = 30;
        double golden = Math.PI * (3 - Math.sqrt(5));
        double spin = ticks * 0.03;
        for (int i = 0; i < n; i++) {
            double y = 1 - 2.0 * i / (n - 1);
            double rad = Math.sqrt(1 - y * y);
            double th = golden * i + spin;
            Location l = c.clone().add(Math.cos(th) * rad * RADIUS, y * RADIUS, Math.sin(th) * rad * RADIUS);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, shell, true);
        }
        // jagged cracks running across the shell
        for (int k = 0; k < 4; k++) {
            Vector pos = randomUnit(rnd).multiply(RADIUS);
            for (int s = 0; s < 4; s++) {
                pos.add(new Vector(rnd.nextDouble(-0.5, 0.5), rnd.nextDouble(-0.5, 0.5), rnd.nextDouble(-0.5, 0.5)));
                pos = pos.normalize().multiply(RADIUS); // stay on the shell
                Location l = c.clone().add(pos);
                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, glint, true);
                if (s % 2 == 0) w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dark, true);
            }
        }
        // shards falling off it
        for (int k = 0; k < 2; k++) {
            Location l = c.clone().add(randomUnit(rnd).multiply(RADIUS));
            w.spawnParticle(Particle.BLOCK, l, 1, 0.1, 0.1, 0.1, 0, glass, true);
        }
    }

    /**
     * Energy shards (flat models) orbiting the dome: each one swings around the sphere on its own path, pointed along
     * the way it is travelling and tumbling about that axis.
     */
    private void flyShards(Player p, List<Shard> list, int ticks) {
        Location c = p.getLocation().add(0, 1.0, 0);
        double radius = RADIUS * 0.96;
        for (Shard s : list) {
            Vector now = s.at(ticks);
            Vector next = s.at(ticks + 2);
            Vector dir = next.clone().subtract(now);
            Location at = c.clone().add(now.clone().multiply(radius));
            FlatFx.move(s.display, at, 2);
            FlatFx.shape(s.display, new Vector3f((float) s.size, (float) (s.size * 1.4), (float) s.size),
                    FlatFx.yTo(dir), new Quaternionf().rotateY((float) (ticks * s.spin)), 2);
        }
        if (ticks % 8 == 0) { // a glint where they pass
            Shard s = list.get(ThreadLocalRandom.current().nextInt(list.size()));
            c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(s.at(ticks).multiply(radius)), 1, 0.05, 0.05, 0.05, 0.01, null, true);
        }
    }

    private static Vector randomUnit(ThreadLocalRandom rnd) {
        double z = rnd.nextDouble(-1, 1);
        double a = rnd.nextDouble(Math.PI * 2);
        double rad = Math.sqrt(1 - z * z);
        return new Vector(rad * Math.cos(a), z, rad * Math.sin(a));
    }
}
