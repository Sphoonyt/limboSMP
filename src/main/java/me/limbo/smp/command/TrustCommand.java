package me.limbo.smp.command;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** /trust <player> | /trust remove <player> | /trust list   (also reachable as /limbo trust ...) */
public final class TrustCommand implements TabExecutor {
    private final LimboPlugin plugin;

    public TrustCommand(LimboPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        run(s, a);
        return true;
    }

    public void run(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "<red>Players only.");
            return;
        }
        if (a.length == 0) {
            Msg.send(s, "<gray>Usage: <white>/trust <player> <gray>| <white>/trust remove <player> <gray>| <white>/trust list");
            return;
        }
        switch (a[0].toLowerCase()) {
            case "list" -> list(p);
            case "remove", "untrust" -> {
                if (a.length < 2) { Msg.send(p, "<gray>Usage: <white>/trust remove <player>"); return; }
                remove(p, a[1]);
            }
            case "add" -> {
                if (a.length < 2) { Msg.send(p, "<gray>Usage: <white>/trust <player>"); return; }
                add(p, a[1]);
            }
            default -> add(p, a[0]);
        }
    }

    private void add(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) { Msg.send(p, "<red>That player isn't online."); return; }
        if (t.getUniqueId().equals(p.getUniqueId())) { Msg.send(p, "<red>You can't trust yourself."); return; }
        if (plugin.trust().trust(p.getUniqueId(), t.getUniqueId())) {
            Msg.send(p, "<gray>You now trust <white>" + Msg.esc(t.getName()) + "<gray>. Your abilities won't affect them.");
            Msg.send(t, "<white>" + Msg.esc(p.getName()) + "<gray> trusts you. Their abilities won't affect you.");
        } else {
            Msg.send(p, "<gray>You already trust <white>" + Msg.esc(t.getName()) + "<gray>.");
        }
    }

    private void remove(Player p, String name) {
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(name);
        if (t == null) { Msg.send(p, "<red>Unknown player."); return; }
        if (plugin.trust().untrust(p.getUniqueId(), t.getUniqueId())) {
            Msg.send(p, "<gray>You no longer trust <white>" + Msg.esc(String.valueOf(t.getName())) + "<gray>.");
        } else {
            Msg.send(p, "<gray>You weren't trusting them.");
        }
    }

    private void list(Player p) {
        var set = plugin.trust().trustedBy(p.getUniqueId());
        if (set.isEmpty()) { Msg.send(p, "<gray>You trust nobody."); return; }
        List<String> names = new ArrayList<>();
        for (UUID id : set) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            names.add(n != null ? n : id.toString());
        }
        Msg.send(p, "<gray>You trust: <white>" + Msg.esc(String.join(", ", names)));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return tab(s, a);
    }

    public List<String> tab(CommandSender s, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("remove", "list"));
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("remove") && s instanceof Player p) {
            for (UUID id : plugin.trust().trustedBy(p.getUniqueId())) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                if (n != null) out.add(n);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("add")) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).toList();
    }
}
