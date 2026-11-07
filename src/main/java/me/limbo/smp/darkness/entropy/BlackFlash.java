package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.util.Feedback;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.Sfx;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * H - Black Flash. The hit you land is not registered at first (the swing deals nothing); 0.6 seconds later it lands
 * for 2.3 hearts of true damage and extra knockback, with black lightning tearing out of the target towards you and
 * all around. Landing it gives you +2 attack damage and Resistance II for 8 seconds. Land 8 more hits inside those
 * 8 seconds and Black Flash comes off cooldown. 90s cooldown.
 */
public final class BlackFlash extends Channel implements Listener {
    private static final String NAME = "Black Flash";
    private static final int DELAY_TICKS = 12;         // 0.6s
    private static final double DAMAGE = 4.6;          // 2.3 hearts
    private static final double KNOCKBACK = 1.9;       // extra horizontal push
    private static final double MAX_REACH = 7.0;       // it still lands if they back off a little
    private static final int BUFF_TICKS = 160;         // 8s
    private static final double BONUS_ATTACK = 2.0;
    private static final int HITS_TO_RESET = 8;

    private static final Color CRIMSON = Color.fromRGB(205, 12, 32);
    private static final Color BLACK = Color.fromRGB(4, 2, 6);

    private static final class Window {
        int hits;
        BukkitTask end;
    }

    private final NamespacedKey bonusKey;
    /** casters whose current swing is the Black Flash wind-up: that hit must be cancelled */
    private final Set<UUID> cancelNext = new HashSet<>();
    private final Map<UUID, Window> windows = new HashMap<>();

    public BlackFlash() {
        super("black_flash", NAME, "Delayed true damage hit with black lightning. Landing it buffs you.", ChannelInput.H, 90_000L);
        this.bonusKey = new NamespacedKey(LimboPlugin.get(), "black_flash_bonus");
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        if (!(ctx.target() instanceof LivingEntity target)) return false;
        cancelNext.add(p.getUniqueId()); // consumed by onSwingHit this same event
        Bukkit.getScheduler().runTask(LimboPlugin.get(), () -> cancelNext.remove(p.getUniqueId()));

        windUp(p, target);
        return true;
    }

    /** The swing that triggered it doesn't register. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwingHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (cancelNext.remove(p.getUniqueId())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ the 0.6 seconds

    private void windUp(Player p, LivingEntity target) {
        World w = target.getWorld();
        Sfx.play(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.5f, Sfx.Theme.ENTROPY);
        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead() || !target.isValid() || target.isDead() || target.getWorld() != w) { cancel(); return; }
                Location c = target.getBoundingBox().getCenter().toLocation(w);
                double pull = 1.0 - t / (double) DELAY_TICKS;
                // black sparks being sucked into the target, red crackle on the fist
                for (int i = 0; i < 1; i++) { // a single dark mote drawn in from around them, never on top of them
                    Vector d = randomUnit().multiply(1.4 + 1.2 * pull);
                    w.spawnParticle(Particle.DUST, c.clone().add(d), 1, 0, 0, 0, 0, new Particle.DustOptions(BLACK, 1.0f), true);
                }
                if (t % 4 == 0) {
                    Location hand = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.9)).subtract(0, 0.5, 0);
                    bolt(w, hand, randomUnit().multiply(0.9), 3, 0.1, false);
                }
                if (t == DELAY_TICKS - 4) w.playSound(c, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.8f);
                t++;
                if (t >= DELAY_TICKS) { cancel(); land(p, target); }
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    // ------------------------------------------------------------ the hit

    private void land(Player p, LivingEntity target) {
        if (!p.isOnline() || p.isDead() || !target.isValid() || target.isDead() || target.getWorld() != p.getWorld()) return;
        if (p.getLocation().distanceSquared(target.getLocation()) > MAX_REACH * MAX_REACH) return;
        if (!Targeting.canHarm(p, target)) return;

        Vector away = target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = p.getLocation().getDirection().setY(0);
        away.normalize();

        if (!TrueDamage.apply(p, target, DAMAGE)) return;
        Feedback.affected(target, NAME, p);
        if (!target.isDead()) target.setVelocity(away.multiply(KNOCKBACK).setY(0.5));

        flash(p, target);
        buff(p);
    }

    private void buff(Player p) {
        UUID id = p.getUniqueId();
        Window old = windows.remove(id);
        if (old != null && old.end != null) old.end.cancel();

        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null) {
            removeBonus(atk);
            atk.addTransientModifier(new AttributeModifier(bonusKey, BONUS_ATTACK, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, BUFF_TICKS, 1, false, true, true));
        LimboPlugin.get().screenFx().show(p, me.limbo.smp.status.ScreenFx.Fx.BLACK_FLASH, BUFF_TICKS);

        Window w = new Window();
        windows.put(id, w);
        w.end = Bukkit.getScheduler().runTaskLater(LimboPlugin.get(), () -> endWindow(id), BUFF_TICKS);
    }

    private void removeBonus(AttributeInstance atk) {
        for (AttributeModifier m : new ArrayList<>(atk.getModifiers())) if (m.getKey().equals(bonusKey)) atk.removeModifier(m);
    }

    private void endWindow(UUID id) {
        Window w = windows.remove(id);
        if (w != null && w.end != null) w.end.cancel();
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        LimboPlugin.get().screenFx().clear(p, me.limbo.smp.status.ScreenFx.Fx.BLACK_FLASH);
        AttributeInstance atk = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null) removeBonus(atk);
    }

    /** 8 hits inside the window brings Black Flash off cooldown. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying() || e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        Window w = windows.get(p.getUniqueId());
        if (w == null || !(e.getEntity() instanceof LivingEntity) || Targeting.isClone(e.getEntity())) return;
        w.hits++;
        if (w.hits >= HITS_TO_RESET) {
            w.hits = Integer.MIN_VALUE / 2; // only once per window
            LimboPlugin.get().dispatcher().cooldowns().clear(p.getUniqueId(), EntropyDarkness.ID + ":black_flash");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.8f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.5f);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { endWindow(e.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { endWindow(e.getEntity().getUniqueId()); }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(windows.keySet())) endWindow(id);
    }

    // ------------------------------------------------------------ the lightning

    private static Vector randomUnit() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double u = r.nextDouble(Math.PI * 2), v = Math.acos(2 * r.nextDouble() - 1);
        return new Vector(Math.sin(v) * Math.cos(u), Math.cos(v), Math.sin(v) * Math.sin(u));
    }

    /**
     * A jagged bolt that starts at `from` and runs along `along` (its length is the vector's length). A black core
     * with a thin crimson edge; every frame the jitter is rolled again so it flickers like real lightning.
     */
    private void bolt(World w, Location from, Vector along, int segments, double jitter, boolean branches) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector step = along.clone().multiply(1.0 / segments);
        Location cur = from.clone();
        Particle.DustOptions core = new Particle.DustOptions(BLACK, 1.0f);
        Particle.DustOptions edge = new Particle.DustOptions(CRIMSON, 0.6f);
        for (int i = 0; i < segments; i++) {
            Vector j = new Vector(r.nextDouble(-jitter, jitter), r.nextDouble(-jitter, jitter), r.nextDouble(-jitter, jitter)).multiply(1 + i * 0.15);
            Location next = cur.clone().add(step).add(j);
            for (double f = 0; f < 1; f += 0.5) {
                Location l = cur.clone().add(next.clone().subtract(cur).toVector().multiply(f));
                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, core, true);
                if (r.nextBoolean()) w.spawnParticle(Particle.DUST, l.clone().add(r.nextDouble(-0.07, 0.07), r.nextDouble(-0.07, 0.07), r.nextDouble(-0.07, 0.07)),
                        1, 0, 0, 0, 0, edge, true);
            }
            if (branches && i > 0 && r.nextInt(6) == 0) bolt(w, next, randomUnit().multiply(1.2), 2, jitter, false);
            cur = next;
        }
    }

    /**
     * The flash itself: a white-hot burst that is immediately swallowed by black, then black-and-red lightning
     * crawling from the target to you and out in every direction, flickering for a few ticks.
     */
    private void flash(Player p, LivingEntity target) {
        World w = target.getWorld();
        Location c = target.getBoundingBox().getCenter().toLocation(w);
        Location me = p.getBoundingBox().getCenter().toLocation(w);

        Sfx.play(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 0.6f, Sfx.Theme.ENTROPY);
        w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.1f);
        w.playSound(c, Sound.ITEM_TRIDENT_THUNDER, 1.4f, 0.8f);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.7f);
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.9, 0), 2, 0.1, 0.1, 0.1, 0.2, null, true); // a small spark above the head, nothing on the body
        // one thin ring at the feet, so the body stays visible
        for (int k = 0; k < 16; k++) {
            double a = Math.PI * 2 * k / 16;
            w.spawnParticle(Particle.DUST, target.getLocation().add(Math.cos(a) * 1.1, 0.15, Math.sin(a) * 1.1), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(CRIMSON, 1.0f), true);
        }

        Vector towardsMe = me.toVector().subtract(c.toVector());
        double toMe = Math.min(12.0, towardsMe.length());
        Vector dirMe = towardsMe.lengthSquared() > 1e-4 ? towardsMe.normalize() : new Vector(0, 0, 1);

        // Everything stays OFF the line between your eyes and them, so you can always see who you hit: one bolt
        // arcs past them to the side, two more fly off away from you.
        Vector sideways = dirMe.clone().crossProduct(new Vector(0, 1, 0));
        if (sideways.lengthSquared() < 1e-4) sideways = new Vector(1, 0, 0);
        final Vector side = sideways.normalize().multiply(ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        new BukkitRunnable() {
            int frame = 0;

            @Override public void run() {
                if (frame >= 3) { cancel(); return; }
                Vector along = dirMe.clone().multiply(Math.max(0.5, Math.min(toMe - 1.5, 3.5))).add(randomUnit().multiply(0.3));
                bolt(w, c.clone().add(side.clone().multiply(1.0)).add(dirMe.clone().multiply(0.4)), along, 6, 0.18, false);
                for (int i = 0; i < 2; i++) {
                    Vector out = randomUnit();
                    int tries = 0;
                    while (out.dot(dirMe) > 0.2 && tries++ < 8) out = randomUnit(); // never toward your camera
                    bolt(w, c.clone().add(out.clone().multiply(1.0)), out.multiply(1.8 + ThreadLocalRandom.current().nextDouble(2.0)), 6, 0.2, false);
                }
                frame++;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }
}
