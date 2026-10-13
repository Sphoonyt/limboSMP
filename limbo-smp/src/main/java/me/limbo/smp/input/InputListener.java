package me.limbo.smp.input;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.ChannelInput;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Translates raw Bukkit events into ChannelInputs (R, CR, L, CL, H).
 *
 * Right click rules:
 *  - orb in MAIN hand            -> single right click = R / CR
 *  - orb in OFFHAND + mainhand is empty/sword/axe -> DOUBLE right click (window from config) = R / CR
 *  - orb in OFFHAND + any other mainhand item     -> single right click = R / CR
 * Left click / hit only count with the orb in the MAIN hand (you can't attack with the offhand).
 */
public final class InputListener implements Listener {
    private static final long DEBOUNCE_MS = 50; // ignore duplicate events for the same physical click

    private final LimboPlugin plugin;
    private final Map<UUID, Long> lastOffhandClick = new HashMap<>();

    public InputListener(LimboPlugin plugin) { this.plugin = plugin; }

    private static boolean needsDoubleClick(ItemStack main) {
        Material t = main.getType();
        return t.isAir() || Tag.ITEMS_SWORDS.isTagged(t) || Tag.ITEMS_AXES.isTagged(t);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action == Action.PHYSICAL) return;
        Player p = e.getPlayer();

        Block clicked = e.getClickedBlock();
        if (clicked != null && plugin.rifts().isRift(clicked)) return; // rift crafting handles these

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (e.getHand() != EquipmentSlot.HAND) return;
            if (!plugin.items().isOrb(p.getInventory().getItemInMainHand())) return;
            plugin.dispatcher().dispatch(p, p.isSneaking() ? ChannelInput.CL : ChannelInput.L, null, false);
            return;
        }

        // right click
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        if (clicked != null && !p.isSneaking() && clicked.getType().isInteractable()) return; // opening chests etc.

        PlayerInventory inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();
        ChannelInput in = p.isSneaking() ? ChannelInput.CR : ChannelInput.R;

        if (plugin.items().isOrb(main)) {
            if (hand != EquipmentSlot.HAND) return;
            e.setCancelled(true);
            plugin.dispatcher().dispatch(p, in, null, false);
        } else if (plugin.items().isOrb(off)) {
            if (hand != EquipmentSlot.OFF_HAND) return;
            e.setCancelled(true);
            if (!needsDoubleClick(main)) {
                plugin.dispatcher().dispatch(p, in, null, true);
                return;
            }
            long window = plugin.getConfig().getLong("input.double-click-window-ms", 250L);
            long now = System.currentTimeMillis();
            Long last = lastOffhandClick.get(p.getUniqueId());
            if (last != null && now - last < DEBOUNCE_MS) return;
            if (last != null && now - last <= window) {
                lastOffhandClick.remove(p.getUniqueId());
                plugin.dispatcher().dispatch(p, in, null, true);
            } else {
                lastOffhandClick.put(p.getUniqueId(), now);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!plugin.items().isOrb(p.getInventory().getItemInMainHand())) return;
        if (!me.limbo.smp.util.Targeting.canHarm(p, victim)) return; // trusted players aren't "enemies"
        plugin.dispatcher().dispatch(p, ChannelInput.H, victim, false);
    }
}
