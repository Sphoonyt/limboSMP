package me.limbo.smp.command;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LimboCommand implements TabExecutor {
    private static final List<String> PLAYER_SUBS = List.of("darknessinfo", "trust");
    private static final List<String> ADMIN_SUBS = List.of("set", "reset", "siphon", "unsiphon", "give",
            "rift", "cooldown", "reload", "debug");

    private final LimboPlugin plugin;
    private final TrustCommand trustCmd;

    public LimboCommand(LimboPlugin plugin, TrustCommand trustCmd) {
        this.plugin = plugin;
        this.trustCmd = trustCmd;
    }

    private boolean isAdmin(CommandSender s) { return s.hasPermission("limbo.admin"); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) { help(s); return true; }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "darknessinfo", "info" -> info(s);
            case "trust" -> trustCmd.run(s, Arrays.copyOfRange(a, 1, a.length));
            default -> {
                if (!ADMIN_SUBS.contains(sub)) { help(s); return true; }
                if (!isAdmin(s)) { Msg.send(s, "<red>You don't have permission."); return true; }
                admin(s, sub, a);
            }
        }
        return true;
    }

    private void help(CommandSender s) {
        Msg.send(s, "<white>/limbo darknessinfo <gray>- your darkness, passives, channels, siphon status");
        Msg.send(s, "<white>/trust <player> <gray>- stop your abilities from affecting a player (/limbo trust works too)");
        if (isAdmin(s)) {
            Msg.send(s, "<gray>Admin: <white>set, reset, siphon, unsiphon, give, rift, cooldown, reload, debug");
        }
    }

    // ---------------- /limbo darknessinfo ----------------

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) { Msg.send(s, "<red>Players only."); return; }
        Darkness d = plugin.data().getDarkness(p);
        if (d == null) { Msg.send(p, "<gray>You have no darkness."); return; }

        Color col = d.color();
        TextColor tc = TextColor.color(col.getRed(), col.getGreen(), col.getBlue());
        p.sendMessage(Component.text("━━ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(d.displayName() + " Darkness", tc))
                .append(Component.text(" ━━", NamedTextColor.DARK_GRAY)));
        p.sendMessage(Component.text(d.description(), NamedTextColor.GRAY));

        p.sendMessage(Component.text("Passives ", NamedTextColor.WHITE)
                .append(Component.text("(null orb in inventory)", NamedTextColor.DARK_GRAY)));
        if (d.passives().isEmpty()) {
            p.sendMessage(Component.text("  none yet", NamedTextColor.DARK_GRAY));
        }
        for (Passive ps : d.passives()) {
            p.sendMessage(Component.text("  • " + ps.name() + ": ", tc).append(Component.text(ps.description(), NamedTextColor.GRAY)));
        }

        p.sendMessage(Component.text("Flux passive ", NamedTextColor.WHITE)
                .append(Component.text("(holding the orb)", NamedTextColor.DARK_GRAY)));
        if (d.fluxPassive() == null) {
            p.sendMessage(Component.text("  none yet", NamedTextColor.DARK_GRAY));
        } else {
            var f = d.fluxPassive();
            p.sendMessage(Component.text("  • " + f.name() + ": ", tc).append(Component.text(f.description(), NamedTextColor.GRAY)));
        }

        p.sendMessage(Component.text("Channels", NamedTextColor.WHITE));
        if (d.channels().isEmpty()) {
            p.sendMessage(Component.text("  none yet", NamedTextColor.DARK_GRAY));
        }
        for (Channel ch : d.channels()) {
            long left = plugin.dispatcher().cooldowns().remaining(p.getUniqueId(), d.id() + ":" + ch.id());
            Component line = Component.text("  [" + ch.input().shortName() + "] " + ch.name(), tc)
                    .append(Component.text(" - " + ch.description(), NamedTextColor.GRAY))
                    .append(Component.text(" (cd " + TimeUtil.seconds(ch.cooldownMillis()) + ")", NamedTextColor.DARK_GRAY));
            if (left > 0) line = line.append(Component.text(" ready in " + TimeUtil.seconds(left), NamedTextColor.RED));
            p.sendMessage(line);
        }

        long siphon = plugin.data().siphonRemaining(p.getUniqueId());
        if (siphon > 0) {
            p.sendMessage(Component.text("Siphoned: ", NamedTextColor.DARK_RED)
                    .append(Component.text("channels locked for " + TimeUtil.format(siphon)
                            + " (or consume a Shadow Lantern)", NamedTextColor.GRAY)));
        } else {
            p.sendMessage(Component.text("Siphon: ", NamedTextColor.WHITE).append(Component.text("none", NamedTextColor.GREEN)));
        }
    }

    // ---------------- admin ----------------

    private Player online(CommandSender s, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) Msg.send(s, "<red>Player not online: " + Msg.esc(name));
        return t;
    }

    private void admin(CommandSender s, String sub, String[] a) {
        switch (sub) {
            case "set" -> {
                if (a.length < 3) { Msg.send(s, "<gray>/limbo set <player> <darkness>"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                Darkness d = plugin.registry().get(a[2]);
                if (d == null) { Msg.send(s, "<red>Unknown darkness. Valid: " + String.join(", ", plugin.registry().ids())); return; }
                plugin.data().setDarkness(t.getUniqueId(), d.id());
                plugin.data().saveIfDirty();
                Msg.send(s, "<gray>Set <white>" + Msg.esc(t.getName()) + "<gray> to <white>" + d.displayName());
                Msg.send(t, "<gray>Your darkness is now <white>" + d.displayName());
            }
            case "reset" -> {
                if (a.length < 2) { Msg.send(s, "<gray>/limbo reset <player>  (re-rolls randomly)"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                Darkness d = plugin.data().assignRandom(t.getUniqueId());
                plugin.data().clearSiphon(t.getUniqueId());
                plugin.data().saveIfDirty();
                Msg.send(s, "<gray>Rerolled <white>" + Msg.esc(t.getName()) + "<gray> -> <white>" + (d == null ? "none" : d.displayName()));
                if (d != null) Msg.send(t, "<gray>Your darkness is now <white>" + d.displayName());
            }
            case "siphon" -> {
                if (a.length < 2) { Msg.send(s, "<gray>/limbo siphon <player> [hours]"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                double hours = plugin.getConfig().getDouble("siphon.duration-hours", 12);
                if (a.length > 2) {
                    try { hours = Double.parseDouble(a[2]); } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); return; }
                }
                plugin.data().siphon(t.getUniqueId(), (long) (hours * 3600_000L));
                plugin.data().saveIfDirty();
                Msg.send(s, "<gray>Siphoned <white>" + Msg.esc(t.getName()) + "<gray> for <white>" + hours + "h");
            }
            case "unsiphon" -> {
                if (a.length < 2) { Msg.send(s, "<gray>/limbo unsiphon <player>"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                plugin.data().clearSiphon(t.getUniqueId());
                plugin.data().saveIfDirty();
                Msg.send(s, "<gray>Cleared siphon on <white>" + Msg.esc(t.getName()));
            }
            case "give" -> {
                if (a.length < 3) { Msg.send(s, "<gray>/limbo give <player> <orb|lantern> [amount]"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                int amt = 1;
                if (a.length > 3) {
                    try { amt = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); return; }
                }
                var item = switch (a[2].toLowerCase()) {
                    case "orb" -> plugin.items().createOrb();
                    case "lantern" -> plugin.items().createLantern();
                    default -> null;
                };
                if (item == null) { Msg.send(s, "<red>Item must be orb or lantern."); return; }
                item.setAmount(amt);
                plugin.items().give(t, item);
                Msg.send(s, "<gray>Gave <white>" + amt + "x " + a[2].toLowerCase() + "<gray> to <white>" + Msg.esc(t.getName()));
            }
            case "rift" -> rift(s, a);
            case "cooldown" -> {
                if (a.length < 3 || !a[1].equalsIgnoreCase("reset")) { Msg.send(s, "<gray>/limbo cooldown reset <player>"); return; }
                Player t = online(s, a[2]); if (t == null) return;
                plugin.dispatcher().cooldowns().clear(t.getUniqueId());
                Msg.send(s, "<gray>Reset cooldowns for <white>" + Msg.esc(t.getName()));
            }
            case "reload" -> {
                plugin.reloadAll();
                Msg.send(s, "<gray>Reloaded config, data, rifts and recipes.");
            }
            case "debug" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "<red>Players only."); return; }
                boolean on = plugin.toggleDebug(p.getUniqueId());
                Msg.send(p, "<gray>Input debug: " + (on ? "<green>on" : "<red>off"));
            }
            default -> help(s);
        }
    }

    private void rift(CommandSender s, String[] a) {
        if (a.length < 2) { Msg.send(s, "<gray>/limbo rift <create|remove|list>"); return; }
        switch (a[1].toLowerCase()) {
            case "list" -> {
                List<String> l = plugin.rifts().list();
                if (l.isEmpty()) { Msg.send(s, "<gray>No rifts."); return; }
                l.forEach(r -> Msg.send(s, "<gray>- <white>" + r));
            }
            case "create", "remove" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "<red>Players only."); return; }
                Block b = p.getTargetBlockExact(6);
                if (b == null) { Msg.send(p, "<red>Look at a block (within 6 blocks)."); return; }
                if (a[1].equalsIgnoreCase("create")) {
                    plugin.rifts().create(b);
                    Msg.send(p, "<gray>Shadow rift created at <white>" + b.getX() + " " + b.getY() + " " + b.getZ());
                } else {
                    if (plugin.rifts().remove(b, true)) Msg.send(p, "<gray>Shadow rift removed.");
                    else Msg.send(p, "<red>That block isn't a rift.");
                }
            }
            default -> Msg.send(s, "<gray>/limbo rift <create|remove|list>");
        }
    }

    // ---------------- tab ----------------

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (isAdmin(s)) out.addAll(ADMIN_SUBS);
        } else if (a[0].equalsIgnoreCase("trust")) {
            return trustCmd.tab(s, Arrays.copyOfRange(a, 1, a.length));
        } else if (isAdmin(s)) {
            String sub = a[0].toLowerCase();
            if (a.length == 2) {
                switch (sub) {
                    case "set", "reset", "siphon", "unsiphon", "give" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    case "rift" -> out.addAll(List.of("create", "remove", "list"));
                    case "cooldown" -> out.add("reset");
                    default -> {}
                }
            } else if (a.length == 3) {
                switch (sub) {
                    case "set" -> out.addAll(plugin.registry().ids());
                    case "give" -> out.addAll(List.of("orb", "lantern"));
                    case "cooldown" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    default -> {}
                }
            }
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).toList();
    }
}
