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
import me.limbo.smp.util.Sfx;
import me.limbo.smp.util.TimeUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L - Reflected Construct. You blink 6 blocks in the direction you are moving (read from your movement keys, diagonals
 * included, straight ahead if you stand still) without passing through blocks, except cobwebs, and turn truly
 * invisible for 5 seconds. An identical clone is left where you stood and copies your movements from there.
 * It lasts 5 seconds or until something hits it: it then explodes, blinding the attacker for 6 seconds and dealing
 * 1.5 hearts of true damage. 2 charges, each recharges after 45 seconds.
 *
 * Nothing announces it to other players: no sound, no particles. The only tells are the clone and you vanishing.
 * Invisibility is the potion, re-applied every 2 ticks for the whole duration (so being hit or milk can't end it),
 * plus the player is hidden from every other player's client, so there is no nametag and no gear to leak, and
 * nothing can leave you looking naked when it ends.
 */
public final class ReflectedConstruct extends Channel implements Listener {
    private static final String NAME = "Reflected Construct";
    private static final int CHARGES = 2;
    private static final long RECHARGE_MS = 45_000L;
    private static final double DISTANCE = 6.0;
    private static final int DURATION_TICKS = 100;       // 5s: invisibility and clone
    private static final int BLIND_TICKS = 120;          // 6s
    private static final double EXPLOSION_DAMAGE = 3.0;  // 1.5 hearts
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    private final NamespacedKey cloneKey;
    private final Map<UUID, long[]> charges = new HashMap<>();
    private final Map<UUID, Cast> byClone = new HashMap<>();
    private static final Map<UUID, Invis> INVIS = new HashMap<>();

    public ReflectedConstruct() {
        super("reflected_construct", NAME, "Blink 6 blocks, turn invisible and leave a decoy that explodes when hit.",
                ChannelInput.L, RECHARGE_MS);
        this.cloneKey = new NamespacedKey(LimboPlugin.get(), "reflection_clone");
    }

    /** True while this player is invisible from this ability (the invisibility flux leaves them alone then). */
    public static boolean isInvisible(UUID id) { return INVIS.containsKey(id); }

    // ------------------------------------------------------------ charges (own cooldown tracking, like Axiom Dagger)

    @Override public boolean usesCustomCooldown() { return true; }

    @Override public void onCooldownReset(UUID id) { charges.remove(id); }

    @Override
    public long hudRemaining(UUID id) {
        long[] slots = charges.get(id);
        if (slots == null) return 0L;
        long now = System.currentTimeMillis(), soonest = Long.MAX_VALUE;
        for (long s : slots) if (s > now) soonest = Math.min(soonest, s - now);
        return soonest == Long.MAX_VALUE ? 0L : soonest;
    }

    @Override
    public String hudCharges(UUID id) {
        long[] slots = charges.get(id);
        long now = System.currentTimeMillis();
        int ready = CHARGES;
        if (slots != null) { ready = 0; for (long s : slots) if (s <= now) ready++; }
        return ready + "/" + CHARGES;
    }

    // ------------------------------------------------------------ the cast

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
            p.sendActionBar(Msg.parse("<gray>Reflected Construct <dark_gray>- <red>" + TimeUtil.seconds(soonest - now)));
            return false;
        }

        Location origin = p.getLocation().clone();
        Location dest = destination(p, moveDirection(p));
        if (dest.distanceSquared(origin) < 1.0) {
            p.sendActionBar(Msg.parse("<red>No room to move"));
            return false; // charge not spent
        }

        slots[use] = now + (ctx.cursed() ? RECHARGE_MS * 2 : RECHARGE_MS);
        int left = 0;
        for (long s : slots) if (s <= now) left++;
        p.sendActionBar(Msg.parse("<#a5cdff>Reflected Construct <gray>" + left + "/" + CHARGES));

        // clone first, so it exists the instant you are gone
        Cast cast = new Cast(p, origin.toVector().subtract(dest.toVector()));
        Mannequin m = cast.spawn(origin);
        byClone.put(m.getUniqueId(), cast);

        p.teleport(dest);
        beginInvisibility(p);
        cast.start();
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.15f, 1.7f); // only you hear it
        return true;
    }

    @Override
    public void onShutdown() {
        for (Cast c : new ArrayList<>(byClone.values())) c.end(false);
        for (UUID id : new ArrayList<>(INVIS.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) endInvisibility(p);
        }
    }

    // ------------------------------------------------------------ movement

    /** Horizontal direction you are moving in, from your movement keys (diagonals included). Forward if idle. */
    private Vector moveDirection(Player p) {
        Input in = p.getCurrentInput();
        double fwd = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double side = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        double yaw = Math.toRadians(p.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector left = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Vector v = forward.clone().multiply(fwd).add(left.multiply(side));
        if (v.lengthSquared() < 1e-6) v = forward;
        return v.normalize();
    }

    /** The furthest point up to 6 blocks away along `dir` that you can reach without passing through blocks (webs are fine). */
    private Location destination(Player p, Vector dir) {
        Location start = p.getLocation();
        Location best = start.clone();
        for (double d = 0.25; d <= DISTANCE + 1e-6; d += 0.25) {
            Vector shift = dir.clone().multiply(d);
            if (!fits(p, shift)) break;
            best = start.clone().add(shift);
        }
        return best;
    }

    private boolean fits(Player p, Vector shift) {
        BoundingBox bb = p.getBoundingBox().shift(shift);
        World w = p.getWorld();
        for (int x = (int) Math.floor(bb.getMinX()); x <= (int) Math.floor(bb.getMaxX() - 1e-6); x++) {
            for (int y = (int) Math.floor(bb.getMinY()); y <= (int) Math.floor(bb.getMaxY() - 1e-6); y++) {
                for (int z = (int) Math.floor(bb.getMinZ()); z <= (int) Math.floor(bb.getMaxZ() - 1e-6); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.COBWEB) continue;
                    if (!b.isPassable()) return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------ true invisibility

    private static final class Invis {
        long until;
        BukkitTask task;
        final Set<UUID> hiddenFrom = new HashSet<>();
    }

    private void beginInvisibility(Player p) {
        UUID id = p.getUniqueId();
        long until = System.currentTimeMillis() + DURATION_TICKS * 50L;
        Invis inv = INVIS.get(id);
        if (inv != null) { inv.until = until; return; } // a second charge just extends it
        Invis fresh = new Invis();
        fresh.until = until;
        INVIS.put(id, fresh);
        LimboPlugin pl = LimboPlugin.get();
        fresh.task = new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead() || System.currentTimeMillis() >= fresh.until) { endInvisibility(p); return; }
                // re-applied constantly: hits, milk, totems... can't drop it
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 12, 0, false, false, false));
                if (t % 5 == 0) { // also hidden from every other client: no nametag, no gear
                    for (Player v : Bukkit.getOnlinePlayers()) {
                        if (v.equals(p) || v.getWorld() != p.getWorld()) continue;
                        if (fresh.hiddenFrom.add(v.getUniqueId())) v.hideEntity(pl, p);
                    }
                }
                t++;
            }
        }.runTaskTimer(pl, 0L, 2L);
    }

    private void endInvisibility(Player p) {
        Invis inv = INVIS.remove(p.getUniqueId());
        if (inv == null) return;
        if (inv.task != null) inv.task.cancel();
        LimboPlugin pl = LimboPlugin.get();
        for (UUID u : inv.hiddenFrom) {
            Player v = Bukkit.getPlayer(u);
            if (v != null) v.showEntity(pl, p);
        }
        if (!pl.fluxActive(p, ReflectionDarkness.ID)) p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // belt and braces: make sure nobody is left looking at an empty-handed, armourless player
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            PlayerInventory inv2 = p.getInventory();
            for (Player v : p.getTrackedBy()) for (EquipmentSlot s : SLOTS) v.sendEquipmentChange(p, s, inv2.getItem(s));
        }, 3L);
    }

    // ------------------------------------------------------------ the decoy

    private final class Cast extends BukkitRunnable {
        private final Player p;
        private final World world;
        private final Vector offset; // where the clone is relative to you: origin - destination
        private Mannequin clone;
        private int ticks = 0;
        private boolean done = false;

        Cast(Player p, Vector offset) {
            this.p = p;
            this.world = p.getWorld();
            this.offset = offset;
        }

        Mannequin spawn(Location at) {
            clone = world.spawn(at, Mannequin.class, m -> {
                m.setProfile(profileOf(p));
                m.setDescription(null);
                m.setImmovable(true);
                m.setInvulnerable(false);   // so hits fire damage events we can intercept
                m.setGravity(false);
                m.setSilent(true);
                m.setPersistent(false);
                m.setCollidable(false);
                m.setMainHand(p.getMainHand());
                m.setPose(mapPose(p.getPose()));
                AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    hp.setBaseValue(1024.0);
                    m.setHealth(1024.0);
                }
                copyGear(m);
                m.getPersistentDataContainer().set(cloneKey, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            return clone;
        }

        void start() { runTaskTimer(LimboPlugin.get(), 1L, 1L); }

        @Override
        public void run() {
            ticks++;
            if (!p.isOnline() || p.isDead() || p.getWorld() != world || ticks > DURATION_TICKS || !clone.isValid()) {
                end(false);
                return;
            }
            // mimic: the clone moves exactly as you do, starting from where you were
            Location pl = p.getLocation();
            double x = pl.getX() + offset.getX(), z = pl.getZ() + offset.getZ();
            double pg = groundY(pl.getX(), pl.getY(), pl.getZ(), pl.getY());
            double cg = groundY(x, pl.getY(), z, pg);
            clone.teleport(new Location(world, x, pl.getY() + (cg - pg), z, pl.getYaw(), pl.getPitch()));
            clone.setRotation(pl.getYaw(), pl.getPitch());
            clone.setPose(mapPose(p.getPose()));
            if (ticks % 20 == 0) copyGear(clone);
        }

        void swing() { if (clone.isValid()) clone.swingMainHand(); }

        /** @param exploded true when it went off (the explosion effect is played by the caller) */
        void end(boolean exploded) {
            if (done) return;
            done = true;
            try { cancel(); } catch (IllegalStateException ignored) {}
            if (clone != null) {
                byClone.remove(clone.getUniqueId());
                if (clone.isValid() && !exploded) {
                    // timing out: it just isn't there any more, as quietly as possible
                    clone.getWorld().spawnParticle(Particle.CLOUD, clone.getLocation().add(0, 1, 0), 4, 0.2, 0.4, 0.2, 0.01);
                }
                if (clone.isValid()) clone.remove();
            }
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

        private double groundY(double x, double y, double z, double fallback) {
            RayTraceResult r = world.rayTraceBlocks(new Location(world, x, y + 1.5, z), new Vector(0, -1, 0), 8.0,
                    FluidCollisionMode.NEVER, true);
            return r == null ? fallback : r.getHitPosition().getY();
        }
    }

    private static ItemStack copy(ItemStack s) { return s == null ? null : s.clone(); }

    private static ResolvableProfile profileOf(Player p) {
        try {
            return ResolvableProfile.resolvableProfile()
                    .uuid(p.getUniqueId())
                    .name(p.getName())
                    .addProperties(new ArrayList<ProfileProperty>(p.getPlayerProfile().getProperties()))
                    .build();
        } catch (RuntimeException ex) {
            return ResolvableProfile.resolvableProfile()
                    .uuid(p.getUniqueId())
                    .addProperties(new ArrayList<ProfileProperty>(p.getPlayerProfile().getProperties()))
                    .build();
        }
    }

    private static Pose mapPose(Pose p) {
        return switch (p) {
            case SNEAKING, SWIMMING, FALL_FLYING, SLEEPING -> p;
            default -> Pose.STANDING;
        };
    }

    // ------------------------------------------------------------ the explosion

    private void explode(Cast cast, LivingEntity attacker, Player owner) {
        Location at = cast.clone.getLocation().add(0, 1, 0);
        World w = at.getWorld();
        cast.end(true);

        Sfx.play(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f, Sfx.Theme.REFLECTION);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.5f);
        w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.2f);
        w.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0, null, true);
        w.spawnParticle(Particle.BLOCK, at, 50, 0.5, 0.8, 0.5, 0.2, Material.GLASS.createBlockData(), true);
        w.spawnParticle(Particle.END_ROD, at, 30, 0.3, 0.5, 0.3, 0.4, null, true);
        ParticleUtil.burst(at, ReflectionDarkness.COLOR, 40);
        for (int i = 0; i < 3; i++) {
            double r = 1.0 + i * 0.9;
            for (int k = 0; k < 36; k++) {
                double a = Math.PI * 2 * k / 36;
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(Math.cos(a) * r, -0.8, Math.sin(a) * r), 1, 0, 0, 0, 0,
                        new Particle.DustTransition(ReflectionDarkness.WHITE, ReflectionDarkness.COLOR, 1.5f), true);
            }
        }

        TrueDamage.apply(owner, attacker, EXPLOSION_DAMAGE);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLIND_TICKS, 0, false, true, true));
        Feedback.affected(attacker, NAME, owner);
        if (attacker instanceof Player ap) ap.sendActionBar(Msg.parse("<#a5cdff>It was only a reflection..."));
    }

    // ------------------------------------------------------------ interactions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitClone(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Mannequin m)) return;
        String owner = m.getPersistentDataContainer().get(cloneKey, PersistentDataType.STRING);
        if (owner == null) return;

        e.setCancelled(true); // the clone never takes real damage, it just goes off
        Entity damager = e.getDamager();
        Entity attacker = damager;
        if (damager instanceof Projectile pr) {
            if (pr.getShooter() instanceof Entity shooter) attacker = shooter;
            damager.remove();
        }
        if (attacker.getUniqueId().toString().equals(owner)) return; // your own swings don't pop it
        UUID ownerId = UUID.fromString(owner);
        if (attacker instanceof Player ap && LimboPlugin.get().trust().isTrusting(ownerId, ap.getUniqueId())) return;
        if (!(attacker instanceof LivingEntity le)) return;

        Cast cast = byClone.get(m.getUniqueId());
        Player ownerPlayer = Bukkit.getPlayer(ownerId);
        if (cast == null || ownerPlayer == null) { m.remove(); return; }
        explode(cast, le, ownerPlayer);
    }

    /** Fire, lava, explosions etc. can't kill a clone either. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOtherDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Mannequin m)) return;
        if (m.getPersistentDataContainer().has(cloneKey, PersistentDataType.STRING)) e.setCancelled(true);
    }

    /** The clone swings when you swing. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        for (Cast c : byClone.values()) if (c.p.getUniqueId().equals(id)) c.swing();
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { cleanup(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { cleanup(e.getEntity()); }

    private void cleanup(Player p) {
        for (Cast c : new ArrayList<>(byClone.values())) if (c.p.getUniqueId().equals(p.getUniqueId())) c.end(false);
        endInvisibility(p);
    }
}
