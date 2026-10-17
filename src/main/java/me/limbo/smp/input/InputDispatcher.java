package me.limbo.smp.input;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.ChannelContext;
import me.limbo.smp.darkness.ChannelInput;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Single entry point for every channel activation:
 * darkness check -> siphon check -> channel lookup -> cooldown -> execute.
 */
public final class InputDispatcher {
    private final LimboPlugin plugin;
    private final CooldownManager cooldowns = new CooldownManager();
    /** Players currently inside a channel's execute(); prevents H from re-triggering via ability damage. */
    private final Set<UUID> executing = new HashSet<>();
    private long lastSiphonNotice = 0;

    public InputDispatcher(LimboPlugin plugin) { this.plugin = plugin; }

    public CooldownManager cooldowns() { return cooldowns; }

    public void forget(UUID id) { executing.remove(id); }

    public void dispatch(Player p, ChannelInput input, LivingEntity target, boolean offhand) {
        UUID id = p.getUniqueId();
        if (executing.contains(id)) return;

        Darkness d = plugin.data().getDarkness(p);
        if (d == null) return;

        if (plugin.isDebug(id)) {
            p.sendActionBar(Msg.parse("<dark_gray>[debug] <gray>input <white>" + input.shortName()
                    + (offhand ? " <gray>(offhand)" : "")));
        }

        long siphonLeft = plugin.data().siphonRemaining(id);
        if (siphonLeft > 0) {
            long now = System.currentTimeMillis();
            if (now - lastSiphonNotice > 500) {
                lastSiphonNotice = now;
                p.sendActionBar(Msg.parse("<dark_red>Your darkness is siphoned <gray>(" + TimeUtil.format(siphonLeft) + ")"));
            }
            return;
        }

        Channel c = d.channelFor(input);
        if (c == null) return;

        String key = d.id() + ":" + c.id();
        long left = cooldowns.remaining(id, key);
        if (left > 0) {
            p.sendActionBar(Msg.parse("<gray>" + Msg.esc(c.name()) + " <dark_gray>- <red>" + TimeUtil.seconds(left)));
            return;
        }

        ItemStack orb = (offhand ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand());
        ChannelContext ctx = new ChannelContext(p, d, input, target, orb, offhand);
        executing.add(id);
        try {
            if (c.execute(ctx)) cooldowns.set(id, key, c.cooldownMillis());
        } catch (Throwable t) {
            plugin.getLogger().severe("Channel " + key + " threw: " + t);
            t.printStackTrace();
        } finally {
            executing.remove(id);
        }
    }
}
