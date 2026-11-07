package me.limbo.smp.darkness.chimera;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.ChimeraDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.Sfx;
import net.kyori.adventure.bossbar.BossBar;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R - evolve for 30 seconds: +0.3 entity reach, knockback resistance like full netherite (0.4), Haste II, and every
 * other player's passives and channels treat you as trusted (Targeting.canHarm refuses you). 180s cooldown.
 */
public final class Evolution extends Channel implements Listener {
    private static final String NAME = "Evolution";
    private static final int DURATION_TICKS = 600;
    private static final double REACH = 0.3;
    private static final double KNOCKBACK_RESISTANCE = 0.4; // 4 netherite pieces x 0.1

    private static final Map<UUID, Evolved> ACTIVE = new HashMap<>();

    private static final class Evolved {
        BukkitTask task;
    }

    private final NamespacedKey reachKey;
    private final NamespacedKey kbKey;

    public Evolution() {
        super("evolution", NAME, "Evolve for 30s: more reach, haste, knockback resistance, and enemy abilities treat you as trusted.",
                ChannelInput.R, 180_000L);
        this.reachKey = new NamespacedKey(LimboPlugin.get(), "chimera_reach");
        this.kbKey = new NamespacedKey(LimboPlugin.get(), "chimera_knockback");
    }

    /** While true, other players' abilities can't affect this player (see Targeting.canHarm). */
    public static boolean isEvolved(UUID id) { return ACTIVE.containsKey(id); }

    @Override
    public boolean execute(ChannelContext ctx) {
        Player p = ctx.player();
        if (ACTIVE.containsKey(p.getUniqueId())) return false;

        addModifier(p, Attribute.ENTITY_INTERACTION_RANGE, reachKey, REACH);
        addModifier(p, Attribute.KNOCKBACK_RESISTANCE, kbKey, KNOCKBACK_RESISTANCE);
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, DURATION_TICKS + 10, 1, false, false, true));

        Evolved ev = new Evolved();
        LimboPlugin.get().screenFx().show(p, me.limbo.smp.status.ScreenFx.Fx.EVOLUTION, DURATION_TICKS);
        ACTIVE.put(p.getUniqueId(), ev);

        transformBurst(p);
        ev.task = new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t >= DURATION_TICKS) { end(p, t >= DURATION_TICKS); return; }
                aura(p, t);
                t += 2;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 2L);
        return true;
    }

    private void addModifier(Player p, Attribute attr, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        removeModifier(p, attr, key);
        inst.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    private void removeModifier(Player p, Attribute attr, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) if (m.getKey().equals(key)) inst.removeModifier(m);
    }

    private void end(Player p, boolean natural) {
        Evolved ev = ACTIVE.remove(p.getUniqueId());
        if (ev == null) return;
        if (ev.task != null) ev.task.cancel();
        LimboPlugin.get().screenFx().clear(p, me.limbo.smp.status.ScreenFx.Fx.EVOLUTION);
        removeModifier(p, Attribute.ENTITY_INTERACTION_RANGE, reachKey);
        removeModifier(p, Attribute.KNOCKBACK_RESISTANCE, kbKey);
        PotionEffect haste = p.getPotionEffect(PotionEffectType.HASTE);
        if (haste != null && haste.getAmplifier() == 1 && haste.getDuration() <= 40) p.removePotionEffect(PotionEffectType.HASTE);
        if (natural && p.isOnline()) {
            Location c = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, c, 40, 0.5, 0.8, 0.5, 0,
                    new Particle.DustTransition(ChimeraDarkness.COLOR, Color.fromRGB(10, 8, 16), 1.5f), true);
            p.getWorld().playSound(c, Sound.ENTITY_RAVAGER_STUNNED, 0.7f, 1.2f);
        }
    }

    // ------------------------------------------------------------ visuals

    /** The change: green light spirals up the body, ground ring, a roar. */
    private void transformBurst(Player p) {
        World w = p.getWorld();
        Location c = p.getLocation();
        Sfx.play(c, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.8f, Sfx.Theme.CHIMERA);
        w.playSound(c, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.6f);
        Particle.DustTransition d = new Particle.DustTransition(Color.fromRGB(140, 255, 150), ChimeraDarkness.COLOR, 1.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!p.isOnline() || t > 12) { cancel(); return; }
                Location base = p.getLocation();
                for (int arm = 0; arm < 3; arm++) {
                    double a = t * 0.7 + arm * Math.PI * 2 / 3, y = t * 0.17;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(Math.cos(a) * 0.9, y, Math.sin(a) * 0.9), 2, 0.05, 0.05, 0.05, 0, d, true);
                }
                double r = 0.5 + t * 0.3;
                for (int i = 0; i < 28; i++) {
                    double a = Math.PI * 2 * i / 28;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 1, 0, 0, 0, 0, d, true);
                }
                if (t == 6) w.spawnParticle(Particle.HAPPY_VILLAGER, base.clone().add(0, 1, 0), 25, 0.6, 0.9, 0.6, 0.1);
                t++;
            }
        }.runTaskTimer(LimboPlugin.get(), 0L, 1L);
    }

    /** While evolved: a slow green helix, drifting spores and thorns at the shoulders. */
    private void aura(Player p, int t) {
        World w = p.getWorld();
        Location base = p.getLocation();
        Particle.DustTransition d = new Particle.DustTransition(ChimeraDarkness.COLOR, Color.fromRGB(10, 40, 15), 1.2f);
        double spin = t * 0.25;
        for (int i = 0; i < 2; i++) {
            double a = spin + i * Math.PI, y = (t % 40) / 20.0;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(Math.cos(a) * 0.75, y, Math.sin(a) * 0.75), 1, 0, 0, 0, 0, d, true);
        }
        double yaw = Math.toRadians(base.getYaw());
        for (int side = -1; side <= 1; side += 2) { // thorns rising off the shoulders
            double sx = Math.cos(yaw) * 0.45 * side * -1, sz = Math.sin(yaw) * 0.45 * side * -1;
            for (int k = 0; k < 3; k++) {
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(sx, 1.5 + k * 0.18, sz), 1, 0.02, 0.02, 0.02, 0,
                        new Particle.DustTransition(Color.fromRGB(150, 255, 160), ChimeraDarkness.COLOR, 1.0f), true);
            }
        }
        if (t % 6 == 0) w.spawnParticle(Particle.SPORE_BLOSSOM_AIR, base.clone().add(0, 1.2, 0), 3, 0.5, 0.7, 0.5, 0);
    }

    // ------------------------------------------------------------ cleanup

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { end(e.getPlayer(), false); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) { end(e.getEntity(), false); }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p, false);
        }
        ACTIVE.clear();
    }
}
