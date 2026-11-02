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
 * Event rewards: null totems that are claimed from chat (so a full inventory never eats one), and rerolls.
 * Rerolls are a plain counter per player for now (no item designed yet): /limbo reroll uses one, /limbo reroll give
 * shares them. When the reroll item exists, only this class needs to change.
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
            if (p.isOnline()) sendClaimPrompt(p);
        }, 60L);
    }

    // ------------------------------------------------------------ rerolls

    public int rerolls(UUID id) { return plugin.data().getRerolls(id); }

    public void grantRerolls(UUID id, int amount) {
        plugin.data().addRerolls(id, amount);
        plugin.data().saveIfDirty();
    }

    /** /limbo reroll: spend one reroll on a new darkness (never the same one). */
    public void useReroll(Player p) {
        UUID id = p.getUniqueId();
        if (plugin.data().getRerolls(id) <= 0) { Msg.send(p, "<red>You have no rerolls."); return; }
        Darkness current = plugin.data().getDarkness(p);
        List<Darkness> options = new ArrayList<>(plugin.registry().all());
        if (current != null) options.remove(current);
        if (options.isEmpty()) { Msg.send(p, "<red>There is nothing to reroll into."); return; }
        plugin.data().takeReroll(id);
        Darkness next = options.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(options.size()));
        plugin.data().setDarkness(id, next.id());
        plugin.data().clearSiphon(id);
        plugin.data().saveIfDirty();
        plugin.items().syncTotems(p);
        Msg.send(p, "<gray>Reroll spent. <white>" + plugin.data().getRerolls(id) + "<gray> left.");
        DarknessRoll.play(plugin, p, next, 5.0, null);
    }

    /** /limbo reroll give <player> [amount] */
    public void giveReroll(Player from, Player to, int amount) {
        if (from.equals(to)) { Msg.send(from, "<red>You can't give rerolls to yourself."); return; }
        if (amount < 1) { Msg.send(from, "<red>Give at least 1."); return; }
        if (plugin.data().getRerolls(from.getUniqueId()) < amount) {
            Msg.send(from, "<red>You only have " + plugin.data().getRerolls(from.getUniqueId()) + " reroll(s).");
            return;
        }
        plugin.data().addRerolls(from.getUniqueId(), -amount);
        plugin.data().addRerolls(to.getUniqueId(), amount);
        plugin.data().saveIfDirty();
        Msg.send(from, "<gray>You gave <white>" + amount + "<gray> reroll(s) to <white>" + Msg.esc(to.getName()) + "<gray>.");
        Msg.send(to, "<white>" + Msg.esc(from.getName()) + "<gray> gave you <white>" + amount + "<gray> reroll(s). Use <white>/limbo reroll<gray>.");
    }

}
