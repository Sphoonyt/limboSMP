package me.limbo.smp.darkness.inertia;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Land 5 hits in a row without getting hit -> Haste III for 5s. 15s cooldown.
 * A combo also drops if 3s pass between hits (WINDOW_MS) so it can't be built up over a whole fight.
 * Hits only count on players and hostile mobs; any entity hit on you resets the combo.
 */
public final class ComboPassive implements Passive, Listener {
    private static final String CD_KEY = "inertia:combo";
    private static final int HITS_REQUIRED = 5;
    private static final long COOLDOWN_MS = 15_000L;
    private static final long WINDOW_MS = 3_000L;
    private static final int HASTE_TICKS = 100; // 5s
    private static final int HASTE_AMPLIFIER = 2; // Haste III

    private final Map<UUID, Integer> combo = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();

    @Override public String id() { return "combo"; }
    @Override public String name() { return "Combo"; }
    @Override public String description() { return "Land a 5 hit combo without getting hit for Haste III (5s). 15s cooldown."; }

    @Override public void onDeactivate(Player p) { reset(p.getUniqueId()); }

    private void reset(UUID id) {
        combo.remove(id);
        lastHit.remove(id);
    }

    /** Getting hit by anything with an attacker resets your combo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p) reset(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity v)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.passivesActive(p, InertiaDarkness.ID)) return;
        if (!Targeting.canHarm(p, v) || !Targeting.isEnemy(v)) return;

        UUID id = p.getUniqueId();
        if (pl.dispatcher().cooldowns().remaining(id, CD_KEY) > 0) return;

        long now = System.currentTimeMillis();
        Long last = lastHit.get(id);
        if (last != null && now - last > WINDOW_MS) combo.remove(id);
        lastHit.put(id, now);

        int n = combo.merge(id, 1, Integer::sum);
        if (n < HITS_REQUIRED) return;

        reset(id);
        pl.dispatcher().cooldowns().set(id, CD_KEY, COOLDOWN_MS);
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, HASTE_TICKS, HASTE_AMPLIFIER, false, true, true));
        ParticleUtil.blissDust(p.getLocation().add(0, 1, 0), InertiaDarkness.DARK, InertiaDarkness.LIGHT, 20, 0.5);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        p.sendActionBar(Msg.parse("<#40e0d0>Combo!"));
    }
}
