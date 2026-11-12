package me.limbo.smp.item;

import me.limbo.smp.LimboPlugin;
import org.bukkit.EntityEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The null totem works as a totem of undying, but ONLY against a killing blow dealt by a passive or a channel
 * (anything that goes through TrueDamage, Targeting.damage or AbilityDamage.run). Normal damage still kills you.
 * Once it saves you it is on cooldown for null-totem-undying.cooldown-minutes (30): the totem item turns grey for
 * that long (see LimboItems#syncTotems) and nothing else tells you.
 * By default the totem only has to be somewhere in your inventory; null-totem-undying.require-held: true makes it
 * work only from the main hand or offhand like a vanilla totem.
 */
public final class NullTotemGuard implements Listener {
    /** True while an ability is dealing normal damage (so the damage event below knows where it came from). */
    private static boolean abilityDamage;

    public static void abilityDamage(Runnable damage) {
        boolean prev = abilityDamage;
        abilityDamage = true;
        try { damage.run(); } finally { abilityDamage = prev; }
    }

    private final LimboPlugin plugin;
    private final NamespacedKey cdKey;

    public NullTotemGuard(LimboPlugin plugin) {
        this.plugin = plugin;
        this.cdKey = new NamespacedKey(plugin, "null_totem_cooldown_until");
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("null-totem-undying.enabled", true); }

    private long cooldownMillis() {
        return (long) (plugin.getConfig().getDouble("null-totem-undying.cooldown-minutes", 30.0) * 60_000L);
    }

    public boolean onCooldown(Player p) {
        Long until = p.getPersistentDataContainer().get(cdKey, PersistentDataType.LONG);
        return until != null && until > System.currentTimeMillis();
    }

    private boolean hasTotem(Player p) {
        if (plugin.getConfig().getBoolean("null-totem-undying.require-held", false)) {
            return plugin.items().isOrb(p.getInventory().getItemInMainHand()) || plugin.items().isOrb(p.getInventory().getItemInOffHand());
        }
        for (ItemStack s : p.getInventory().getContents()) if (s != null && plugin.items().isOrb(s)) return true;
        return false;
    }

    /** Called when a passive/channel would kill `p`. @return true if the totem saved them (the caller must not kill). */
    public boolean tryUndying(Player p) {
        if (!enabled() || onCooldown(p) || !hasTotem(p)) return false;
        p.getPersistentDataContainer().set(cdKey, PersistentDataType.LONG, System.currentTimeMillis() + cooldownMillis());
        p.setHealth(1.0);
        p.clearActivePotionEffects();
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        p.setFireTicks(0);
        p.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.4);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.items().syncTotems(p)); // grey it out right away
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!abilityDamage || !(e.getEntity() instanceof Player p)) return;
        double hp = p.getHealth() + p.getAbsorptionAmount();
        if (e.getFinalDamage() < hp) return;
        if (tryUndying(p)) e.setCancelled(true);
    }
}
