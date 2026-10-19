package me.limbo.smp.darkness.reflection;

import me.limbo.smp.util.Feedback;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L - summon 5 clones (mannequins wearing your skin, armor and held items) in a circle around you and
 * turn truly invisible (potion + gear hidden from viewers) for 70 ticks. The clones last 20s and copy
 * everything you do: same movement, rotation, pose, sword swings. Hitting a clone cancels the hit, makes it
 * vanish and gives the attacker Darkness for 2s. 100s cooldown.
 * Uses vanilla Mannequin entities (no Citizens needed).
 */
public final class ReflectedConstructs extends Channel implements Listener {
    private static final int CLONES = 5;
    private static final double RING_RADIUS = 3.0;
    private static final int LIFETIME_TICKS = 400;   // 20s
    private static final int INVIS_TICKS = 70;
    private static final int DARKNESS_TICKS = 40;    // 2s
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    private record Clone(Mannequin entity, Vector offset) {}

    private final NamespacedKey cloneKey;
    private final Map<UUID, Cast> casts = new HashMap<>();

    public ReflectedConstructs() {
        super("reflected_constructs", "Reflected Constructs", "Summons mimicking clones and turns you invisible.",
                ChannelInput.L, 100_000L);
        this.cloneKey = new NamespacedKey(LimboPlugin.get(), "reflection_clone");
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        Cast old = casts.remove(p.getUniqueId());
        if (old != null) old.end();

        Cast c = new Cast(p);
        casts.put(p.getUniqueId(), c);
        c.start();
        return true;
    }

    @Override
    public void onShutdown() {
        for (Cast c : new ArrayList<>(casts.values())) c.end();
    }

    // ------------------------------------------------------------ the cast

    private final class Cast extends BukkitRunnable {
        private final Player p;
        private final World world;
        private final List<Clone> clones = new ArrayList<>();
        private int ticks = 0;
        private boolean done = false;
        private boolean gearHidden = false;

        Cast(Player p) {
            this.p = p;
            this.world = p.getWorld();
        }

        void start() {
            Location pl = p.getLocation();
            double phase = Math.random() * Math.PI * 2;
            for (int i = 0; i < CLONES; i++) {
                double a = phase + 2 * Math.PI * i / CLONES;
                Vector offset = new Vector(Math.cos(a) * RING_RADIUS, 0, Math.sin(a) * RING_RADIUS);
                Location at = cloneLocation(pl, offset);
                Mannequin m = spawnClone(at);
                clones.add(new Clone(m, offset));
                ParticleUtil.blissDust(at.clone().add(0, 1, 0), ReflectionDarkness.COLOR, 18, 0.4);
            }
            world.playSound(pl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
            world.playSound(pl, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.8f);

            // true invisibility: potion plus gear hidden from everyone watching
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVIS_TICKS, 0, false, false, true));
            hideGear();
            ParticleUtil.blissDust(pl.clone().add(0, 1, 0), ReflectionDarkness.COLOR, 24, 0.5);
            p.sendActionBar(Msg.parse("<#a5cdff>Reflected Constructs"));
            runTaskTimer(LimboPlugin.get(), 1L, 1L);
        }

        @Override
        public void run() {
            ticks++;
            if (!p.isOnline() || p.isDead() || p.getWorld() != world || ticks > LIFETIME_TICKS) {
                end();
                return;
            }
            if (gearHidden) {
                if (ticks >= INVIS_TICKS) restoreGear();
                else if (ticks % 10 == 0) hideGear(); // the server re-sends real gear now and then
            }
            Location pl = p.getLocation();
            double pg = groundY(pl.getX(), pl.getY(), pl.getZ(), pl.getY());
            Pose pose = mapPose(p.getPose());
            for (Clone c : clones) {
                Mannequin m = c.entity();
                if (!m.isValid()) continue;
                double x = pl.getX() + c.offset().getX();
                double z = pl.getZ() + c.offset().getZ();
                double cg = groundY(x, pl.getY(), z, pg);
                Location t = new Location(world, x, pl.getY() + (cg - pg), z, pl.getYaw(), pl.getPitch());
                m.teleport(t);
                m.setRotation(pl.getYaw(), pl.getPitch());
                m.setPose(pose);
                if (ticks % 20 == 0) copyGear(m);
            }
        }

        void swing() {
            for (Clone c : clones) if (c.entity().isValid()) c.entity().swingMainHand();
        }

        void end() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) {}
            casts.remove(p.getUniqueId(), this);
            if (gearHidden) restoreGear();
            for (Clone c : clones) {
                Mannequin m = c.entity();
                if (m.isValid()) {
                    poof(m);
                    m.remove();
                }
            }
        }

        // ---- gear hiding (per viewer, packets only) ----

        private void hideGear() {
            gearHidden = true;
            ItemStack air = new ItemStack(Material.AIR);
            for (Player v : p.getTrackedBy()) {
                for (EquipmentSlot s : SLOTS) v.sendEquipmentChange(p, s, air);
            }
        }

        private void restoreGear() {
            gearHidden = false;
            PlayerInventory inv = p.getInventory();
            for (Player v : p.getTrackedBy()) {
                for (EquipmentSlot s : SLOTS) v.sendEquipmentChange(p, s, inv.getItem(s));
            }
        }

        // ---- clone construction ----

        private Mannequin spawnClone(Location at) {
            return world.spawn(at, Mannequin.class, m -> {
                m.setProfile(profileOf(p));
                m.setDescription(null);                 // hide the default "NPC" subtitle
                m.setImmovable(true);
                m.setInvulnerable(false);               // so hits fire damage events we can intercept
                m.setGravity(false);
                m.setSilent(true);
                m.setPersistent(false);
                m.setCollidable(false);
                m.setMainHand(p.getMainHand());
                AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(1024.0);
                    m.setHealth(1024.0);
                }
                copyGear(m);
                m.getPersistentDataContainer().set(cloneKey, PersistentDataType.STRING, p.getUniqueId().toString());
            });
        }

        private void copyGear(Mannequin m) {
            EntityEquipment eq = m.getEquipment();
            if (eq == null) return;
            PlayerInventory inv = p.getInventory();
            eq.setHelmet(copy(inv.getHelmet()));
            eq.setChestplate(copy(inv.getChestplate()));
            eq.setLeggings(copy(inv.getLeggings()));
            eq.setBoots(copy(inv.getBoots()));
            eq.setItemInMainHand(copy(inv.getItemInMainHand()));
            eq.setItemInOffHand(copy(inv.getItemInOffHand()));
        }

        private Location cloneLocation(Location pl, Vector offset) {
            double pg = groundY(pl.getX(), pl.getY(), pl.getZ(), pl.getY());
            double x = pl.getX() + offset.getX(), z = pl.getZ() + offset.getZ();
            double cg = groundY(x, pl.getY(), z, pg);
            return new Location(world, x, pl.getY() + (cg - pg), z, pl.getYaw(), pl.getPitch());
        }

        /** Height of the first solid block below (x, y, z); fallback if there's none in range. */
        private double groundY(double x, double y, double z, double fallback) {
            RayTraceResult r = world.rayTraceBlocks(new Location(world, x, y + 1.5, z), new Vector(0, -1, 0), 8.0,
                    FluidCollisionMode.NEVER, true);
            return r == null ? fallback : r.getHitPosition().getY();
        }
    }

    private static ItemStack copy(ItemStack s) {
        return s == null ? null : s.clone();
    }

    private static ResolvableProfile profileOf(Player p) {
        try {
            return ResolvableProfile.resolvableProfile()
                    .uuid(p.getUniqueId())
                    .name(p.getName())
                    .addProperties(new ArrayList<ProfileProperty>(p.getPlayerProfile().getProperties()))
                    .build();
        } catch (RuntimeException ex) { // e.g. a name the profile name pattern rejects
            return ResolvableProfile.resolvableProfile()
                    .uuid(p.getUniqueId())
                    .addProperties(new ArrayList<ProfileProperty>(p.getPlayerProfile().getProperties()))
                    .build();
        }
    }

    private static Pose mapPose(Pose p) {
        return switch (p) {
            case SNEAKING, SWIMMING, FALL_FLYING, SLEEPING -> p; // the poses a mannequin supports
            default -> Pose.STANDING;
        };
    }

    private void poof(Entity e) {
        Location l = e.getLocation().add(0, 1, 0);
        ParticleUtil.blissDust(l, ReflectionDarkness.COLOR, 20, 0.4);
        l.getWorld().playSound(l, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.5f);
    }

    // ------------------------------------------------------------ interactions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitClone(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Mannequin m)) return;
        String owner = m.getPersistentDataContainer().get(cloneKey, PersistentDataType.STRING);
        if (owner == null) return;

        e.setCancelled(true); // clones never actually take damage
        Entity damager = e.getDamager();
        Entity attacker = damager;
        if (damager instanceof Projectile pr) {
            if (pr.getShooter() instanceof Entity shooter) attacker = shooter;
            damager.remove();
        }
        if (attacker.getUniqueId().toString().equals(owner)) return; // your own swings don't pop them

        UUID ownerId = UUID.fromString(owner);
        if (attacker instanceof Player ap && LimboPlugin.get().trust().isTrusting(ownerId, ap.getUniqueId())) return;

        if (attacker instanceof LivingEntity le) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DARKNESS_TICKS, 0, false, true, true));
        }
        if (attacker instanceof Player ap) ap.sendActionBar(Msg.parse("<#a5cdff>It was only a reflection..."));
        Player ownerPlayer = org.bukkit.Bukkit.getPlayer(ownerId);
        if (ownerPlayer != null) Feedback.affected(attacker, "Reflected Constructs", ownerPlayer);
        poof(m);
        m.remove();
    }

    /** Fire, lava, explosions etc. can't kill a clone either. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOtherDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Mannequin m)) return;
        if (m.getPersistentDataContainer().has(cloneKey, PersistentDataType.STRING)) e.setCancelled(true);
    }

    /** Clones swing when you swing. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        Cast c = casts.get(e.getPlayer().getUniqueId());
        if (c != null) c.swing();
    }
}
