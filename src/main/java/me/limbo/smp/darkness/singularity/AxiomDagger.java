package me.limbo.smp.darkness.singularity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.SingularityDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TimeUtil;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * L - throw a fast dagger (a real 3D model shown by an item display, from the resource pack; players without
 * the pack see a flying iron sword). On an enemy: 1.5 hearts true damage, and they can't jump or throw wind
 * charges for 4 seconds. 2 charges, each recharges 40s after it is used (tracked here, not by the normal cooldown).
 */
public final class AxiomDagger extends Channel {
    private static final String NAME = "Axiom Dagger";
    private static final int CHARGES = 2;
    private static final long RECHARGE_MS = 40_000L;
    private static final double SPEED = 2.2;       // blocks per tick
    private static final double RANGE = 40.0;
    private static final double DAMAGE = 3.0;      // 1.5 hearts
    private static final int GROUND_TICKS = 80;    // 4s

    /** Per player: the time each charge becomes available again. */
    private final Map<UUID, long[]> charges = new HashMap<>();
    private final Set<ItemDisplay> flying = new HashSet<>();

    public AxiomDagger() {
        super("axiom_dagger", NAME, "Fast dagger: true damage, can't jump or wind charge.", ChannelInput.L, RECHARGE_MS);
    }

    @Override public boolean usesCustomCooldown() { return true; }

    @Override public void onCooldownReset(UUID id) { charges.remove(id); }

    @Override
    public void onShutdown() {
        for (ItemDisplay d : new ArrayList<>(flying)) d.remove();
        flying.clear();
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        long now = System.currentTimeMillis();
        long[] slots = charges.computeIfAbsent(p.getUniqueId(), k -> new long[CHARGES]);

        int use = -1;
        long soonest = Long.MAX_VALUE;
        for (int i = 0; i < CHARGES; i++) {
            if (slots[i] <= now) { use = i; break; }
            soonest = Math.min(soonest, slots[i]);
        }
        if (use < 0) {
            p.sendActionBar(Msg.parse("<gray>Axiom Dagger <dark_gray>- <red>" + TimeUtil.seconds(soonest - now)));
            return false;
        }
        slots[use] = now + RECHARGE_MS;
        int left = 0;
        for (long s : slots) if (s <= now) left++;
        p.sendActionBar(Msg.parse("<#9646e6>Axiom Dagger <gray>" + left + "/" + CHARGES));

        launch(p);
        return true;
    }

    private void launch(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location pos = eye.clone().add(dir.clone().multiply(0.8));

        w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.8f);
        w.playSound(eye, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);

        ItemDisplay display = spawnDagger(w, pos, dir);
        flying.add(display);

        Predicate<Entity> filter = e -> e instanceof LivingEntity le && !e.equals(p)
                && Targeting.canHarm(p, le) && Targeting.isEnemy(le);
        Particle.DustTransition trail = new Particle.DustTransition(SingularityDarkness.LIGHT, SingularityDarkness.COLOR, 0.9f);

        new BukkitRunnable() {
            double travelled = 0;

            private void stop() {
                flying.remove(display);
                display.remove();
                cancel();
            }

            @Override public void run() {
                if (!p.isOnline() || !display.isValid()) { stop(); return; }
                RayTraceResult r = w.rayTrace(pos, dir, SPEED, FluidCollisionMode.NEVER, true, 0.3, filter);
                if (r != null) {
                    Location at = r.getHitPosition().toLocation(w);
                    if (r.getHitEntity() instanceof LivingEntity target) {
                        hit(p, target, at);
                    } else {
                        ParticleUtil.blissDust(at, SingularityDarkness.COLOR, 4, 0.15);
                        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.4f);
                    }
                    stop();
                    return;
                }
                pos.add(dir.clone().multiply(SPEED));
                travelled += SPEED;
                display.teleport(flat(pos));
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pos, 1, 0, 0, 0, 0, trail, true); // just a thin trail
                if (travelled >= RANGE) stop();
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    /** The display's own yaw/pitch must stay 0: all rotation lives in its transformation. */
    private static Location flat(Location l) {
        Location c = l.clone();
        c.setYaw(0);
        c.setPitch(0);
        return c;
    }

    /**
     * The dagger model points along +Y, so rotate +Y onto the flight direction. The model comes from the
     * resource pack via custom_model_data string "axiom_dagger" on an iron sword.
     */
    private ItemDisplay spawnDagger(World w, Location at, Vector dir) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta m = item.getItemMeta();
        CustomModelDataComponent cmd = m.getCustomModelDataComponent();
        cmd.setStrings(List.of("axiom_dagger"));
        m.setCustomModelDataComponent(cmd);
        item.setItemMeta(m);

        Quaternionf rotation = new Quaternionf().rotateTo(new Vector3f(0, 1, 0),
                new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
        return w.spawn(flat(at), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), rotation, new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setGravity(false);
            d.setInvulnerable(true);
        });
    }

    private void hit(Player p, LivingEntity target, Location at) {
        TrueDamage.apply(p, target, DAMAGE);
        Feedback.affected(target, NAME, p);
        ParticleUtil.blissDust(at, SingularityDarkness.COLOR, 10, 0.25);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.4f);
        if (target.isDead()) return;
        LimboPlugin.get().status().ground(target, GROUND_TICKS);
        if (target instanceof Player victim) {
            victim.sendActionBar(Msg.parse("<#9646e6>Grounded <gray>- no jumping or wind charges (4s)"));
        }
    }
}
