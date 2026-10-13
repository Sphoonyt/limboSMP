package me.limbo.smp.darkness.entropy;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H - rot for 12s. Every second EVERY damageable item in the victim's inventory, armor and offhand
 * loses durability. The TOTAL over the 12 seconds is a % of the item's max durability:
 * armor 20%, tools/weapons 15%, shield 25%, anything else 30% (spread evenly per second). 145s cooldown.
 * Players only (mobs have no inventory).
 */
public final class Ruination extends Channel {
    private static final int SECONDS = 12;
    private static final double ARMOR = 0.20, TOOL = 0.15, SHIELD = 0.25, OTHER = 0.30;

    private final Map<UUID, BukkitTask> rots = new HashMap<>();

    public Ruination() {
        super("ruination", "Ruination",
                "The enemy hit rots for 12s: all their items lose durability (armor 20%, tools 15%, shield 25%, other 30% total).",
                ChannelInput.H, 145_000L);
    }

    @Override
    public boolean execute(ChannelContext ctx) {
        if (!(ctx.target() instanceof Player victim)) {
            ctx.player().sendActionBar(Msg.parse("<gray>Ruination only affects players."));
            return false;
        }
        LimboPlugin pl = LimboPlugin.get();
        UUID id = victim.getUniqueId();
        BukkitTask old = rots.remove(id);
        if (old != null) old.cancel();

        victim.sendActionBar(Msg.parse("<dark_red>Rot spreads through your gear..."));
        ctx.player().sendActionBar(Msg.parse("<#961428>Ruination"));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.5f);

        BukkitTask task = new BukkitRunnable() {
            int sec = 0;
            @Override public void run() {
                if (!victim.isOnline() || victim.isDead()) { stop(id); return; }
                sec++;
                rot(victim, sec);
                var c = victim.getLocation().add(0, 1, 0);
                ParticleUtil.blissDust(c, EntropyDarkness.COLOR, 14, 0.45);
                victim.getWorld().spawnParticle(Particle.SQUID_INK, c, 6, 0.4, 0.6, 0.4, 0.01);
                if (sec >= SECONDS) stop(id);
            }
        }.runTaskTimer(pl, 20L, 20L);
        rots.put(id, task);
        return true;
    }

    private void stop(UUID id) {
        BukkitTask t = rots.remove(id);
        if (t != null) t.cancel();
    }

    @Override
    public void onShutdown() {
        for (UUID id : new ArrayList<>(rots.keySet())) stop(id);
    }

    private void rot(Player v, int second) {
        boolean allowBreak = LimboPlugin.get().getConfig().getBoolean("entropy.ruination-allow-break", false);
        PlayerInventory inv = v.getInventory();
        // 0-35 storage, 36-39 armor, 40 offhand
        for (int i = 0; i <= 40; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            ItemMeta meta = it.getItemMeta();
            if (!(meta instanceof Damageable d) || meta.isUnbreakable()) continue;
            int max = d.hasMaxDamage() ? d.getMaxDamage() : it.getType().getMaxDurability();
            if (max <= 0) continue;

            double total = pct(i, it);
            long delta = Math.round(max * total * second / SECONDS) - Math.round(max * total * (second - 1) / SECONDS);
            if (delta <= 0) continue;

            int newDmg = d.getDamage() + (int) delta;
            if (newDmg >= max) {
                if (allowBreak) {
                    inv.setItem(i, null);
                    v.playSound(v.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.4f, 1.5f);
                    continue;
                }
                newDmg = max - 1;
            }
            d.setDamage(newDmg);
            it.setItemMeta(d);
            inv.setItem(i, it);
        }
    }

    private double pct(int slot, ItemStack it) {
        Material m = it.getType();
        if (m == Material.SHIELD) return SHIELD;
        if (slot >= 36 && slot <= 39) return ARMOR;
        EquipmentSlot es = m.getEquipmentSlot();
        if (es == EquipmentSlot.HEAD || es == EquipmentSlot.CHEST || es == EquipmentSlot.LEGS || es == EquipmentSlot.FEET) return ARMOR;
        String n = m.name();
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.endsWith("_SPEAR")
                || m == Material.TRIDENT || m == Material.MACE || m == Material.BOW || m == Material.CROSSBOW) {
            return TOOL;
        }
        return OTHER;
    }
}
