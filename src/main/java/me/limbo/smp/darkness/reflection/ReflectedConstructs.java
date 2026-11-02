package me.limbo.smp.darkness.reflection;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * L - three rings expand outward from you, 3 clones (mannequins with your skin, armor and held items) appear: one
 * on each side of you and one behind you, and you turn truly invisible (potion + gear hidden) for 70 ticks.
 * The clones last 20s and copy your movement, rotation, pose and swings. Enemies within 5 blocks of a clone glow.
 * Whenever you hit an enemy, a clone teleports in front of them after a short delay, repeats the hit for half
 * the damage, then vanishes (Reality Shatter hits never trigger this). A clone survives 3 hits; every hit on one
 * is cancelled and gives the attacker Darkness for 2s. 100s cooldown. Uses vanilla Mannequin entities (no Citizens needed).
 */
public final class ReflectedConstructs extends Channel implements Listener {
    private static final String NAME = "Reflected Constructs";
    private static final int CLONE_HITS = 3; // hits a clone survives
    private static final double RING_RADIUS = 3.0;
    private static final int LIFETIME_TICKS = 400;   // 20s
    private static final int INVIS_TICKS = 70;
    private static final int DARKNESS_TICKS = 40;    // 2s
    private static final double GLOW_RANGE = 5.0;
    private static final int ECHO_DELAY_TICKS = 10;  // "short delay" before the clone repeats a hit
    private static final double ECHO_FRACTION = 0.5;
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    private static final class Clone {
        final Mannequin entity;
        final Vector offset;
        boolean busy = false; // currently off doing an echo hit, so it must not follow the player
        Clone(Mannequin entity, Vector offset) { this.entity = entity; this.offset = offset; }
    }

    private final NamespacedKey cloneKey;
    private final Map<UUID, Cast> casts = new HashMap<>();
    private final Map<UUID, Integer> hitsLeft = new HashMap<>();

    public ReflectedConstructs() {
        super("reflected_constructs", NAME, "Summons mimicking clones and turns you invisible.",
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
            // one clone on each side of you and one behind you (relative to where you face when you cast)
            double yaw = Math.toRadians(pl.getYaw());
            Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vector[] offsets = {
                    right.clone().multiply(RING_RADIUS),
                    right.clone().multiply(-RING_RADIUS),
                    forward.clone().multiply(-RING_RADIUS)};
            for (Vector offset : offsets) {
                Location at = cloneLocation(pl, offset);
                Mannequin m = spawnClone(at);
                hitsLeft.put(m.getUniqueId(), CLONE_HITS);
                clones.add(new Clone(m, offset));
                ParticleUtil.blissDust(at.clone().add(0, 1, 0), ReflectionDarkness.COLOR, 18, 0.4);
            }
            world.playSound(pl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
            world.playSound(pl, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.8f);
            expandingRings(pl.clone());

            // true invisibility: potion plus gear hidden from everyone watching
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVIS_TICKS, 0, false, false, true));
            hideGear();
            p.sendActionBar(Msg.parse("<#a5cdff>Reflected Constructs"));
            runTaskTimer(LimboPlugin.get(), 1L, 1L);
        }

        /** Three medium-spaced rings that expand outward along the ground, one after another. */
        private void expandingRings(Location center) {
            new BukkitRunnable() {
                int step = 0;
                @Override public void run() {
                    step++;
                    Particle.DustTransition fade = new Particle.DustTransition(ReflectionDarkness.WHITE, ReflectionDarkness.COLOR, 1.1f);
                    Particle.DustOptions dark = new Particle.DustOptions(ParticleUtil.VOID, 1.2f);
                    for (int i = 0; i < 3; i++) {
                        int s = step - i * 4; // each ring starts 4 ticks after the one before
                        if (s <= 0) continue;
                        double r = s * 0.55;
                        if (r > 7.5) continue;
                        int points = (int) Math.max(24, r * 8);
                        for (int k = 0; k < points; k++) {
                            double a = 2 * Math.PI * k / points;
                            Location l = center.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r);
                            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, fade, true);
                            if (k % 3 == 0) world.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dark, true);
                        }
                    }
                    if (step > 26) cancel();
                }
            }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
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
                Mannequin m = c.entity;
                if (!m.isValid() || c.busy) continue;
                double x = pl.getX() + c.offset.getX();
                double z = pl.getZ() + c.offset.getZ();
                double cg = groundY(x, pl.getY(), z, pg);
                Location t = new Location(world, x, pl.getY() + (cg - pg), z, pl.getYaw(), pl.getPitch());
                m.teleport(t);
                m.setRotation(pl.getYaw(), pl.getPitch());
                m.setPose(pose);
                if (ticks % 20 == 0) copyGear(m);
            }
            if (ticks % 10 == 0) glowEnemies();
        }

        /** Enemies within 5 blocks of any clone glow (refreshed twice a second, so it fades right after they leave). */
        private void glowEnemies() {
            for (Clone c : clones) {
                Mannequin m = c.entity;
                if (!m.isValid()) continue;
                Location at = m.getLocation();
                for (Entity e : m.getNearbyEntities(GLOW_RANGE, GLOW_RANGE, GLOW_RANGE)) {
                    if (!(e instanceof LivingEntity le) || le.equals(p) || Targeting.isClone(le)) continue;
                    if (!Targeting.isEnemy(le) || !Targeting.canHarm(p, le)) continue;
                    if (le.getLocation().distanceSquared(at) > GLOW_RANGE * GLOW_RANGE) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 25, 0, false, false, false));
                }
            }
        }

        void swing() {
            for (Clone c : clones) if (c.entity.isValid() && !c.busy) c.entity.swingMainHand();
        }

        /** A random clone that is still around and not already busy. */
        private Clone pickClone() {
            List<Clone> free = new ArrayList<>();
            for (Clone c : clones) if (c.entity.isValid() && !c.busy) free.add(c);
            if (free.isEmpty()) return null;
            return free.get(ThreadLocalRandom.current().nextInt(free.size()));
        }

        /**
         * You just hit `target`: after a short delay a clone appears in front of them, swings, deals half of the
         * original damage and disappears.
         */
        void echoHit(LivingEntity target, double damage) {
            Clone c = pickClone();
            if (c == null) return;
            c.busy = true;
            Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () -> {
                Mannequin m = c.entity;
                if (!m.isValid()) return;
                if (!target.isValid() || target.isDead() || !p.isOnline() || target.getWorld() != world) {
                    poof(m);
                    m.remove();
                    return;
                }
                Location tl = target.getLocation();
                Vector front = tl.getDirection().setY(0);
                if (front.lengthSquared() < 1e-4) front = new Vector(0, 0, 1);
                front.normalize().multiply(1.3);
                Location at = tl.clone().add(front);
                at.setDirection(tl.toVector().subtract(at.toVector())); // face the target
                m.teleport(at);
                m.swingMainHand();
                world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);

                Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () -> {
                    if (target.isValid() && !target.isDead() && p.isOnline() && damage > 0) {
                        TrueDamage.apply(p, target, damage);
                        Feedback.affected(target, NAME, p);
                        ParticleUtil.blissDust(target.getLocation().add(0, 1, 0), ReflectionDarkness.COLOR, 14, 0.35);
                    }
                    poof(m);
                    m.remove();
                }, 4L);
            }, ECHO_DELAY_TICKS);
        }

        void end() {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) {}
            casts.remove(p.getUniqueId(), this);
            if (gearHidden) restoreGear();
            for (Clone c : clones) {
                Mannequin m = c.entity;
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

    /** You hit an enemy while you have clones: one of them repeats the hit for half damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOwnerHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying() || RealityShatter.isShatterHit()) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        Cast c = casts.get(p.getUniqueId());
        if (c == null) return;
        if (!(e.getEntity() instanceof LivingEntity target) || Targeting.isClone(target)) return;
        if (!Targeting.canHarm(p, target) || !Targeting.isEnemy(target)) return;
        c.echoHit(target, e.getFinalDamage() * ECHO_FRACTION);
    }

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
        Player ownerPlayer = Bukkit.getPlayer(ownerId);
        if (ownerPlayer != null) Feedback.affected(attacker, NAME, ownerPlayer);
        int left = hitsLeft.getOrDefault(m.getUniqueId(), CLONE_HITS) - 1;
        if (left <= 0) {
            hitsLeft.remove(m.getUniqueId());
            poof(m);
            m.remove();
        } else {
            hitsLeft.put(m.getUniqueId(), left); // cracked, but still standing
            Location l = m.getLocation().add(0, 1, 0);
            l.getWorld().spawnParticle(Particle.BLOCK, l, 8, 0.25, 0.4, 0.25, 0, Material.GLASS.createBlockData(), true);
            l.getWorld().playSound(l, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.3f);
        }
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
