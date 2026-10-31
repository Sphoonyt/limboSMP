package me.limbo.smp.siphon;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.TimeUtil;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Death handling + shadow lantern.
 *  - Natural death: nothing.
 *  - Death with a player kill credit (getKiller): channels siphoned for siphon.duration-hours (real time).
 *  - Right click a shadow lantern while siphoned: consume it, restore channels.
 */
public final class SiphonListener implements Listener {
    private final LimboPlugin plugin;

    public SiphonListener(LimboPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        Darkness d = plugin.data().getDarkness(victim);
        if (d == null) return;

        long dur = (long) (plugin.getConfig().getDouble("siphon.duration-hours", 12) * 3600_000L);
        plugin.data().siphon(victim.getUniqueId(), dur);
        plugin.data().saveIfDirty();
        ParticleUtil.burst(victim.getLocation().add(0, 1, 0), d.color(), 30);
        // the killed player's soul drops (head + the darkness they had)
        victim.getWorld().dropItemNaturally(victim.getLocation(), plugin.items().createSoul(victim, d));
        Msg.send(victim, "<dark_red>Your darkness was siphoned away. <gray>Channels locked for <white>"
                + TimeUtil.format(dur) + "<gray> unless you consume a <white>Shadow Lantern<gray>.");
        // killer gains nothing (for now)
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLanternUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        ItemStack item = hand == EquipmentSlot.HAND ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        if (!plugin.items().isLantern(item)) return;
        e.setCancelled(true);

        if (!plugin.data().isSiphoned(p.getUniqueId())) {
            Msg.send(p, "<gray>Your darkness is intact. The lantern stays dim.");
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        plugin.data().clearSiphon(p.getUniqueId());
        plugin.data().saveIfDirty();
        Darkness d = plugin.data().getDarkness(p);
        ParticleUtil.burst(p.getLocation().add(0, 1, 0), d != null ? d.color() : ParticleUtil.RIFT_ACCENT, 40);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 0.8f);
        Msg.send(p, "<#6fe3ff>Your darkness returns to you.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.items().isLantern(e.getItemInHand())) e.setCancelled(true);
    }
}
