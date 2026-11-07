package me.limbo.smp.darkness.chimera;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.ChimeraDarkness;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.Sfx;
import me.limbo.smp.util.Targeting;
import me.limbo.smp.util.TrueDamage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hit a player and this passive is replaced, for 30 seconds, by THEIR second flat passive. While it lasts the
 * copied passive runs for you exactly as it does for its owner (its lifecycle is driven from here, and its event
 * handlers accept you through LimboPlugin#passiveEnabled). While a copy is running further hits don't change it.
 */
public final class CopycatPassive implements Passive, Listener {
    private static final long DURATION_MS = 30_000L;

    public record Copy(String darknessId, Passive passive, long until) {}

    private static final Map<UUID, Copy> COPIES = new HashMap<>();

    @Override public String id() { return "copycat"; }
    @Override public String name() { return "Copycat"; }
    @Override public String description() { return "Hitting an enemy copies their second passive for 30 seconds, replacing this one."; }

    /** The passive this player is currently copying, or null. */
    public static Copy active(UUID id) {
        Copy c = COPIES.get(id);
        return c != null && c.until() > System.currentTimeMillis() ? c : null;
    }

    /** Used by LimboPlugin#passiveEnabled: is this player currently running that exact copied passive? */
    public static boolean isCopying(UUID id, String darknessId, String passiveId) {
        Copy c = active(id);
        return c != null && c.darknessId().equals(darknessId) && c.passive().id().equals(passiveId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (TrueDamage.isApplying()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof Player v)) return;
        LimboPlugin pl = LimboPlugin.get();
        if (!pl.passivesActive(p, ChimeraDarkness.ID)) return;
        if (!Targeting.canHarm(p, v)) return;
        if (active(p.getUniqueId()) != null) return; // already copying something

        Darkness theirs = pl.data().getDarkness(v);
        if (theirs == null || theirs.id().equals(ChimeraDarkness.ID) || theirs.passives().size() < 2) return;
        Passive copied = theirs.passives().get(1);

        COPIES.put(p.getUniqueId(), new Copy(theirs.id(), copied, System.currentTimeMillis() + DURATION_MS));
        copied.onActivate(p);
        ParticleUtil.burst(p.getLocation().add(0, 1, 0), theirs.color(), 24);
        Sfx.play(p.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.8f, 1.2f, Sfx.Theme.CHIMERA);
    }

    @Override
    public void onTick(Player p) {
        Copy c = COPIES.get(p.getUniqueId());
        if (c == null) return;
        if (c.until() <= System.currentTimeMillis()) { end(p, true); return; }
        c.passive().onTick(p);
    }

    @Override public void onDeactivate(Player p) { end(p, false); }

    private void end(Player p, boolean notify) {
        Copy c = COPIES.remove(p.getUniqueId());
        if (c == null) return;
        c.passive().onDeactivate(p);
    }
}
