package me.limbo.smp.reward;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.event.DarknessRoll;
import me.limbo.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Event rewards: null totems that are claimed from chat (so a full inventory never eats one), and Devotion Cores
 * (the reroll item, used at a ritual, see DevotionRitual). Cores are real items, so they can be traded freely.
 */
public final class RewardManager implements Listener {
    private final LimboPlugin plugin;

    public RewardManager(LimboPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------ null totem claims

    /** Gives a null totem now if there's room, otherwise banks it and shows a clickable claim button. */
    public void grantTotem(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && hasRoom(p)) {
            plugin.items().give(p, plugin.items().createOrb(p));
            Msg.send(p, "<#9b59ff>A Null Totem has been placed in your inventory.");
            return;
        }
        plugin.data().addTotemClaims(id, 1);
        plugin.data().saveIfDirty();
        if (p != null) sendClaimPrompt(p);
    }

    private boolean hasRoom(Player p) {
        return p.getInventory().firstEmpty() != -1;
    }

    private void sendClaimPrompt(Player p) {
        int n = plugin.data().getTotemClaims(p.getUniqueId());
        if (n <= 0) return;
        Component button = Component.text("[ Claim Null Totem ]", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.runCommand("/limbo claim"))
                .hoverEvent(HoverEvent.showText(Component.text("Click once you have a free inventory slot")));
        p.sendMessage(Msg.parse(Msg.PREFIX + "<#9b59ff>You have <white>" + n + "<#9b59ff> Null Totem" + (n == 1 ? "" : "s")
                + " waiting. <gray>Free a slot, then ").append(button));
    }

    /** /limbo claim */
    public void claim(Player p) {
        UUID id = p.getUniqueId();
        int n = plugin.data().getTotemClaims(id);
        if (n <= 0) { Msg.send(p, "<gray>You have nothing to claim."); return; }
        int given = 0;
        while (given < n && hasRoom(p)) {
            plugin.items().give(p, plugin.items().createOrb(p));
            given++;
        }
        if (given == 0) { Msg.send(p, "<red>Your inventory is full, free a slot and try again."); return; }
        plugin.data().addTotemClaims(id, -given);
        plugin.data().saveIfDirty();
        Msg.send(p, "<#9b59ff>Claimed <white>" + given + "<#9b59ff> Null Totem" + (given == 1 ? "" : "s") + ".");
        if (given < n) sendClaimPrompt(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            deliverBankedCores(p);
            sendClaimPrompt(p);
        }, 60L);
    }

    // ------------------------------------------------------------ Devotion Cores (the reroll item)

    /** Gives Devotion Cores. Offline players have them banked and receive them when they next join. */
    public void grantCores(UUID id, int amount) {
        if (amount <= 0) return;
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            plugin.data().addRerolls(id, amount); // the stored counter now means "cores waiting"
            plugin.data().saveIfDirty();
            return;
        }
        plugin.items().give(p, plugin.items().createCore(amount)); // anything that doesn't fit drops at their feet
    }

    private void deliverBankedCores(Player p) {
        int n = plugin.data().getRerolls(p.getUniqueId());
        if (n <= 0) return;
        plugin.data().addRerolls(p.getUniqueId(), -n);
        plugin.data().saveIfDirty();
        plugin.items().give(p, plugin.items().createCore(n));
        Msg.send(p, "<#9b59ff>You received <white>" + n + "<#9b59ff> Devotion Core" + (n == 1 ? "" : "s") + ".");
    }

    /**
     * Gives the player a new darkness (never the one they have now). Clears siphon and refreshes their totems.
     * The caller shows the slot machine (DarknessRoll).
     */
    public Darkness rerollDarkness(Player p) {
        UUID id = p.getUniqueId();
        Darkness current = plugin.data().getDarkness(p);
        List<Darkness> options = new ArrayList<>(plugin.registry().all());
        if (current != null && options.size() > 1) options.remove(current);
        Darkness next = options.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(options.size()));
        plugin.data().setDarkness(id, next.id());
        plugin.data().clearSiphon(id);
        plugin.data().saveIfDirty();
        plugin.items().syncTotems(p);
        return next;
    }
}
