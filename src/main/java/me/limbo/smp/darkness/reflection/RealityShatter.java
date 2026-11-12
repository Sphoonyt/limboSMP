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
import org.bukkit.Bukkit;
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
    private static final double RADIUS = 7.0;      // was 4: three blocks bigger, so 7
    private static final double DAMAGE_FACTOR = 0.8; // 20% reduced

    /** True while a shatter hit is being dealt, so listeners (the clone echo) can ignore it. */
    private static boolean shatterHit = false;

    public static boolean isShatterHit() { return shatterHit; }

    private final Map<UUID, BukkitTask> active = new HashMap<>();

    private static final int SHARDS = 28;

    /** One small glass shard standing at the base of the dome, drifting slowly around the ring at your feet. */
    private static final class Shard {
        final ItemDisplay display;
        final double angle0, speed, size, lean, bobPhase, spin;

        Shard(ItemDisplay display, int index, int total, ThreadLocalRandom r) {
            this.display = display;
            this.angle0 = Math.PI * 2 * index / total + r.nextDouble(-0.08, 0.08);
            this.speed = 0.012 * (index % 2 == 0 ? 1 : -1);
            this.size = r.nextDouble(0.28, 0.5);                  // small
            this.lean = r.nextDouble(0.1, 0.45);                  // how far it leans outward from upright
            this.bobPhase = r.nextDouble(Math.PI * 2);
            this.spin = r.nextDouble(0.04, 0.12);
        }

        /** Unit direction from the centre to this shard at tick t. */
        Vector at(double t) {
            double a = angle0 + t * speed;
            return new Vector(Math.cos(a), 0, Math.sin(a));
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
            mine.add(new Shard(FlatFx.spawn(p.getLocation(), FlatFx.SHARD, Display.Billboard.FIXED), i, SHARDS, rnd0));
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
        Player owner = Bukkit.getPlayer(id);
        if (owner != null) LimboPlugin.get().vault().closeFor(owner);
        List<Shard> s = shards.remove(id);
        if (s != null) for (Shard sh : s) FlatFx.remove(sh.display);
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) end(id);
    }

    // ------------------------------------------------------------ the swing

    /** Swinging at air (no entity, no block) connects with every enemy inside the radius. */
    /** While Reality Shatter is up: crouch + right click your null totem to open the 9 slot vault. */
    @EventHandler
    public void onVault(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !active.containsKey(p.getUniqueId())) return;
        if (!LimboPlugin.get().items().isOrb(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        LimboPlugin.get().vault().open(p);
    }

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
                final double dmg = damage;
                me.limbo.smp.item.NullTotemGuard.abilityDamage(() -> target.damage(dmg, p));
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

        int n = 80;     // more points: the dome is now much bigger
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
        for (int k = 0; k < 8; k++) {
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
        for (int k = 0; k < 3; k++) {
            Location l = c.clone().add(randomUnit(rnd).multiply(RADIUS));
            w.spawnParticle(Particle.BLOCK, l, 1, 0.1, 0.1, 0.1, 0, glass, true);
        }
    }

    /**
     * Small glass shards standing in a ring at the base of the dome, around your feet.
     */
    private void flyShards(Player p, List<Shard> list, int ticks) {
        Location feet = p.getLocation();
        double ring = RADIUS * 0.97;
        for (Shard s : list) {
            Vector out = s.at(ticks);
            double bob = 0.04 * Math.sin(ticks * 0.15 + s.bobPhase);
            Location at = feet.clone().add(out.clone().multiply(ring)).add(0, s.size * 0.55 + 0.05 + bob, 0);
            // upright shard leaning away from you, slowly turning on its own axis
            Vector axis = new Vector(0, 1, 0).add(out.clone().multiply(s.lean)).normalize();
            FlatFx.move(s.display, at, 2);
            FlatFx.shape(s.display, new Vector3f((float) s.size, (float) (s.size * 1.6), (float) s.size),
                    FlatFx.yTo(axis), new Quaternionf().rotateY((float) (ticks * s.spin)), 2);
        }
        if (ticks % 6 == 0) { // a glint on the ring
            Shard s = list.get(ThreadLocalRandom.current().nextInt(list.size()));
            feet.getWorld().spawnParticle(Particle.END_ROD, feet.clone().add(s.at(ticks).multiply(ring)).add(0, 0.3, 0), 1, 0.05, 0.05, 0.05, 0.01, null, true);
        }
    }

    private static Vector randomUnit(ThreadLocalRandom rnd) {
        double z = rnd.nextDouble(-1, 1);
        double a = rnd.nextDouble(Math.PI * 2);
        double rad = Math.sqrt(1 - z * z);
        return new Vector(rad * Math.cos(a), z, rad * Math.sin(a));
    }
}
