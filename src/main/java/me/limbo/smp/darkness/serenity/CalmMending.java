package me.limbo.smp.darkness.serenity;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.FluxPassive;
import me.limbo.smp.darkness.SerenityDarkness;
import me.limbo.smp.util.Targeting;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standing completely still (position unchanged) for 3s starts repairing armor:
 * 2 durability on every armor piece each second. Moving resets everything, and it won't work
 * while an enemy is within 4 blocks.
 */
public final class CalmMending implements FluxPassive {
    private static final int STILL_TICKS_REQUIRED = 60;
    private static final int HEAL_INTERVAL_TICKS = 20;
    private static final int REPAIR = 2;
    private static final double ENEMY_RANGE = 4.0;

    private final Map<UUID, Location> last = new HashMap<>();
    private final Map<UUID, Integer> still = new HashMap<>();
    private final Map<UUID, Integer> acc = new HashMap<>();

    @Override public String id() { return "calm_mending"; }
    @Override public String name() { return "Calm Mending"; }
    @Override public String description() { return "Stand completely still for 3s to repair 2 durability on all armor every second. Doesn't work with an enemy within 4 blocks."; }

    @Override public void onDeactivate(Player p) { reset(p.getUniqueId()); }

    private void reset(UUID id) {
        last.remove(id);
        still.remove(id);
        acc.remove(id);
    }

    @Override
    public void onTick(Player p) {
        UUID id = p.getUniqueId();
        int iv = (int) Math.max(1L, LimboPlugin.get().getConfig().getLong("passives.tick-interval-ticks", 10L));
        Location now = p.getLocation();
        Location prev = last.put(id, now);
        boolean stationary = prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) < 1.0E-6;
        if (!stationary || p.isGliding()) {
            still.put(id, 0);
            acc.put(id, 0);
            return;
        }
        int st = still.merge(id, iv, Integer::sum);
        if (st < STILL_TICKS_REQUIRED) return;
        if (enemyNear(p)) { // no mending while someone hostile is close
            acc.put(id, 0);
            return;
        }

        int a = (st - iv < STILL_TICKS_REQUIRED) ? HEAL_INTERVAL_TICKS : acc.getOrDefault(id, 0) + iv;
        if (a >= HEAL_INTERVAL_TICKS) {
            a -= HEAL_INTERVAL_TICKS;
            repair(p);
        }
        acc.put(id, a);
    }

    private boolean enemyNear(Player p) {
        for (Entity e : p.getNearbyEntities(ENEMY_RANGE, ENEMY_RANGE, ENEMY_RANGE)) {
            if (!(e instanceof LivingEntity le) || le.equals(p)) continue;
            if (!Targeting.isEnemy(le) || !Targeting.canHarm(p, le)) continue; // trusted players aren't enemies
            if (le.getLocation().distanceSquared(p.getLocation()) <= ENEMY_RANGE * ENEMY_RANGE) return true;
        }
        return false;
    }

    private void repair(Player p) {
        ItemStack[] armor = p.getInventory().getArmorContents();
        boolean changed = false;
        for (ItemStack it : armor) {
            if (it == null || it.getType().isAir()) continue;
            if (!(it.getItemMeta() instanceof Damageable d) || d.getDamage() <= 0) continue;
            d.setDamage(Math.max(0, d.getDamage() - REPAIR));
            it.setItemMeta(d);
            changed = true;
        }
        if (!changed) return;
        p.getInventory().setArmorContents(armor);
        Location l = p.getLocation().add(0, 1, 0);
        l.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 8, 0.4, 0.6, 0.4, 0.02);
        me.limbo.smp.util.ParticleUtil.blissDust(l, SerenityDarkness.COLOR, 6, 0.4);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
    }
}
