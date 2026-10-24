package me.limbo.smp.command;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.darkness.Passive;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.SmallCaps;
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

    private static String sc(String s) { return SmallCaps.of(s); }

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) { Msg.send(s, "<red>Players only."); return; }
        Darkness d = plugin.data().getDarkness(p);
        if (d == null) { p.sendMessage(Component.text(sc("You have no darkness."), NamedTextColor.GRAY)); return; }

        Color col = d.color();
        TextColor tc = TextColor.color(col.getRed(), col.getGreen(), col.getBlue());
        p.sendMessage(Component.text("━━ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(sc(d.displayName()), tc))
                .append(Component.text(" ━━", NamedTextColor.DARK_GRAY)));
        p.sendMessage(Component.text(sc(d.description()), NamedTextColor.GRAY));

        p.sendMessage(Component.text(sc("Passives"), NamedTextColor.WHITE));
        if (d.passives().isEmpty()) p.sendMessage(Component.text("  " + sc("none yet"), NamedTextColor.DARK_GRAY));
        for (Passive ps : d.passives()) {
            p.sendMessage(Component.text("  • " + sc(ps.name()), tc));
        }

        p.sendMessage(Component.text(sc("Flux"), NamedTextColor.WHITE));
        if (d.fluxPassive() == null) p.sendMessage(Component.text("  " + sc("none yet"), NamedTextColor.DARK_GRAY));
        else p.sendMessage(Component.text("  • " + sc(d.fluxPassive().name()), tc));

        p.sendMessage(Component.text(sc("Channels"), NamedTextColor.WHITE));
        if (d.channels().isEmpty()) p.sendMessage(Component.text("  " + sc("none yet"), NamedTextColor.DARK_GRAY));
        for (Channel ch : d.channels()) {
            long left = plugin.dispatcher().cooldowns().remaining(p.getUniqueId(), d.id() + ":" + ch.id());
            Component line = Component.text("  [" + sc(ch.input().shortName()) + "] " + sc(ch.name()), tc)
                    .append(Component.text(" - " + sc(ch.description()), NamedTextColor.GRAY))
                    .append(Component.text(" (" + (ch.cooldownMillis() / 1000) + sc("s") + ")", NamedTextColor.DARK_GRAY));
            if (left > 0) line = line.append(Component.text(" " + sc("ready in ") + (left / 1000 + 1) + sc("s"), NamedTextColor.RED));
            p.sendMessage(line);
        }

        long siphon = plugin.data().siphonRemaining(p.getUniqueId());
        if (siphon > 0) {
            p.sendMessage(Component.text(sc("Siphoned: "), NamedTextColor.DARK_RED)
                    .append(Component.text(sc("channels locked for ") + TimeUtil.format(siphon).replace("h", sc("h")).replace("m", sc("m")).replace("s", sc("s")), NamedTextColor.GRAY)));
        } else {
            p.sendMessage(Component.text(sc("Siphon: "), NamedTextColor.WHITE).append(Component.text(sc("none"), NamedTextColor.GREEN)));
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
                plugin.items().syncTotems(t);
                Msg.send(s, "<gray>Set <white>" + Msg.esc(t.getName()) + "<gray> to <white>" + d.displayName());
                Msg.send(t, "<gray>Your darkness is now <white>" + d.displayName());
            }
            case "reset" -> {
                if (a.length < 2) { Msg.send(s, "<gray>/limbo reset <player>  (re-rolls randomly)"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                Darkness d = plugin.data().assignRandom(t.getUniqueId());
                plugin.data().clearSiphon(t.getUniqueId());
                plugin.data().saveIfDirty();
                plugin.items().syncTotems(t);
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
                if (a.length < 3) { Msg.send(s, "<gray>/limbo give <player> <totem|lantern> [amount]"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                int amt = 1;
                if (a.length > 3) {
                    try { amt = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); return; }
                }
                var item = switch (a[2].toLowerCase()) {
                    case "orb", "totem" -> plugin.items().createOrb(t);
                    case "lantern" -> plugin.items().createLantern();
                    default -> null;
                };
                if (item == null) { Msg.send(s, "<red>Item must be totem or lantern."); return; }
                item.setAmount(amt);
                plugin.items().give(t, item);
                Msg.send(s, "<gray>Gave <white>" + amt + "x " + a[2].toLowerCase() + "<gray> to <white>" + Msg.esc(t.getName()));
            }
            case "rift" -> rift(s, a);
            case "cooldown" -> {
                if (a.length < 3 || !a[1].equalsIgnoreCase("reset")) { Msg.send(s, "<gray>/limbo cooldown reset <player>"); return; }
                Player t = online(s, a[2]); if (t == null) return;
                plugin.dispatcher().cooldowns().clear(t.getUniqueId());
                plugin.registry().all().forEach(dk -> dk.channels().forEach(ch -> ch.onCooldownReset(t.getUniqueId())));
                plugin.fatigue().clear(t.getUniqueId());
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
        var rm = plugin.rifts();
        if (a.length < 2) { Msg.send(s, "<gray>/limbo rift <create|remove|list|spawn|interval|count|info>"); return; }
        switch (a[1].toLowerCase()) {
            case "list" -> {
                var all = rm.all();
                if (all.isEmpty()) { Msg.send(s, "<gray>No rifts."); return; }
                for (var r : all) {
                    var l = r.location();
                    Msg.send(s, "<gray>- <white>" + r.id() + " <gray>" + l.getWorld().getName() + " "
                            + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                            + " <dark_gray>(" + r.type().name().toLowerCase() + ")"
                            + (rm.relocationIn(r.id()) >= 0 ? " <red>moves in " + TimeUtil.format(rm.relocationIn(r.id())) : ""));
                }
            }
            case "create" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "<red>Players only."); return; }
                Block b = p.getTargetBlockExact(6);
                var loc = b != null ? b.getLocation().add(0.5, 1, 0.5) : p.getLocation();
                var r = rm.create(loc, me.limbo.smp.rift.RiftManager.Type.MANUAL);
                Msg.send(p, "<gray>Shadow rift <white>" + r.id() + "<gray> created.");
            }
            case "remove" -> {
                if (!(s instanceof Player p)) { Msg.send(s, "<red>Players only."); return; }
                var r = rm.nearest(p.getLocation(), 8);
                if (r == null) { Msg.send(p, "<red>No rift within 8 blocks."); return; }
                rm.remove(r.id(), true);
                Msg.send(p, "<gray>Shadow rift <white>" + r.id() + "<gray> removed.");
            }
            case "spawn" -> {
                if (rm.isSpawning()) { Msg.send(s, "<red>A spawn is already in progress."); return; }
                int n = rm.count();
                if (a.length > 2) {
                    try { n = Math.max(1, Math.min(20, Integer.parseInt(a[2]))); } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); return; }
                }
                rm.spawnCycle(n);
                Msg.send(s, "<gray>Spawning <white>" + n + "<gray> rifts at random sunlit spots (replaces the current auto rifts).");
            }
            case "interval" -> {
                if (a.length < 3) { Msg.send(s, "<gray>/limbo rift interval <hours>  (now " + rm.intervalHours() + "h)"); return; }
                try {
                    double h = Double.parseDouble(a[2]);
                    if (h < 0.01) { Msg.send(s, "<red>Minimum is 0.01 hours."); return; }
                    rm.setIntervalHours(h);
                    Msg.send(s, "<gray>Rifts now spawn every <white>" + h + "h<gray>. Next spawn in <white>"
                            + TimeUtil.format(rm.nextSpawnMillis() - System.currentTimeMillis()) + "<gray>.");
                } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); }
            }
            case "count" -> {
                if (a.length < 3) { Msg.send(s, "<gray>/limbo rift count <number>  (now " + rm.count() + ")"); return; }
                try {
                    int n = Integer.parseInt(a[2]);
                    if (n < 1 || n > 20) { Msg.send(s, "<red>Pick 1-20."); return; }
                    rm.setCount(n);
                    Msg.send(s, "<gray>Each cycle now spawns <white>" + n + "<gray> rifts.");
                } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); }
            }
            case "info" -> {
                long autos = rm.all().stream().filter(r -> r.type() == me.limbo.smp.rift.RiftManager.Type.AUTO).count();
                Msg.send(s, "<gray>Rifts: <white>" + rm.all().size() + " <gray>(" + autos + " auto, " + (rm.all().size() - autos) + " manual)");
                Msg.send(s, "<gray>Every <white>" + rm.intervalHours() + "h<gray>, <white>" + rm.count() + "<gray> per cycle.");
                Msg.send(s, "<gray>Next spawn in <white>" + TimeUtil.format(rm.nextSpawnMillis() - System.currentTimeMillis()));
            }
            default -> Msg.send(s, "<gray>/limbo rift <create|remove|list|spawn|interval|count|info>");
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
                    case "rift" -> out.addAll(List.of("create", "remove", "list", "spawn", "interval", "count", "info"));
                    case "cooldown" -> out.add("reset");
                    default -> {}
                }
            } else if (a.length == 3) {
                switch (sub) {
                    case "set" -> out.addAll(plugin.registry().ids());
                    case "give" -> out.addAll(List.of("totem", "lantern"));
                    case "cooldown" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    default -> {}
                }
            }
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).toList();
    }
}
