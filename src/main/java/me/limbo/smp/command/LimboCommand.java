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
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LimboCommand implements TabExecutor {
    private static final List<String> PLAYER_SUBS = List.of("darknessinfo", "trust", "claim", "cursed");
    private static final List<String> ADMIN_SUBS = List.of("set", "reset", "siphon", "unsiphon", "give",
            "rift", "cooldown", "reload", "debug", "event", "unlock", "lock", "unlocks", "reroll", "fx");

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
            case "claim" -> {
                if (s instanceof Player p) plugin.rewards().claim(p); else Msg.send(s, "<red>Players only.");
            }
            case "cursed" -> cursed(s, a);
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
        Msg.send(s, "<white>/limbo cursed <gray>- the current cursed item, /limbo claim");
        Msg.send(s, "<white>/trust <player> <gray>- stop your abilities from affecting a player (/limbo trust works too)");
        if (isAdmin(s)) {
            Msg.send(s, "<gray>Admin: <white>set, reset, siphon, unsiphon, give, rift, cooldown, reload, debug, event, unlock, lock, unlocks, reroll, fx");
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

    // ---------------- rerolls / cursed items ----------------

    private void cursed(CommandSender s, String[] a) {
        var c = plugin.cursed();
        String sub = a.length > 1 ? a[1].toLowerCase() : "";
        if (!sub.isEmpty() && !sub.equals("list")) {
            if (!isAdmin(s)) { Msg.send(s, "<red>You don't have permission."); return; }
            switch (sub) {
                case "roll" -> {
                    if (c.pool().isEmpty()) { Msg.send(s, "<red>The pool is empty. <gray>/limbo cursed pool add <item|hand>"); return; }
                    c.roll(true);
                    return;
                }
                case "set" -> {
                    Material m = a.length > 2 ? Material.matchMaterial(a[2]) : null;
                    if (m == null || c.setCurrent(m, true) == null) Msg.send(s, "<gray>/limbo cursed set <item>");
                    return;
                }
                case "on" -> { c.setEnabled(true); Msg.send(s, "<gray>Cursed item <green>on"); return; }
                case "off" -> { c.setEnabled(false); Msg.send(s, "<gray>Cursed item <red>off"); return; }
                case "pool" -> { cursedPool(s, a); return; }
                default -> { Msg.send(s, "<gray>/limbo cursed [list|roll|set <item>|pool <add|remove|list|clear>|on|off]"); return; }
            }
        }
        if (!c.enabled()) { Msg.send(s, "<gray>The cursed item is not active."); return; }
        long left = c.millisUntilRotation();
        Msg.send(s, "<#9b59ff>Cursed item: <white>" + Msg.esc(c.describe())
                + (left >= 0 ? " <gray>(changes in <white>" + TimeUtil.format(left) + "<gray>)" : ""));
        Msg.send(s, "<gray>Hold it to use your first ability at double cooldown (no passives). It glows in your hotbar or offhand.");
    }

    private void cursedPool(CommandSender s, String[] a) {
        var c = plugin.cursed();
        String op = a.length > 2 ? a[2].toLowerCase() : "list";
        switch (op) {
            case "list" -> {
                if (c.pool().isEmpty()) { Msg.send(s, "<gray>The cursed pool is empty."); return; }
                StringBuilder sb = new StringBuilder();
                for (Material m : c.pool()) sb.append(sb.length() > 0 ? "<dark_gray>, " : "").append(m == c.current() ? "<#9b59ff>" : "<white>").append(Msg.esc(me.limbo.smp.item.CursedItems.prettyName(m)));
                Msg.send(s, "<gray>Pool (" + c.pool().size() + "): " + sb);
            }
            case "add", "remove" -> {
                Material m = null;
                if (a.length > 3) m = Material.matchMaterial(a[3]);
                else if (s instanceof Player p && !p.getInventory().getItemInMainHand().getType().isAir()) m = p.getInventory().getItemInMainHand().getType();
                if (m == null) { Msg.send(s, "<gray>/limbo cursed pool " + op + " <item>  (or hold the item)"); return; }
                boolean ok = op.equals("add") ? c.addToPool(m) : c.removeFromPool(m);
                Msg.send(s, ok ? "<gray>" + (op.equals("add") ? "Added <white>" : "Removed <white>") + Msg.esc(me.limbo.smp.item.CursedItems.prettyName(m))
                        : "<red>" + (op.equals("add") ? "Already in the pool (or not an item)." : "Not in the pool."));
            }
            case "clear" -> { c.clearPool(); Msg.send(s, "<gray>Cleared the cursed pool."); }
            default -> Msg.send(s, "<gray>/limbo cursed pool <add|remove|list|clear> [item]");
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
                if (a.length < 3) { Msg.send(s, "<gray>/limbo give <player> <totem|lantern|core> [amount]"); return; }
                Player t = online(s, a[1]); if (t == null) return;
                int amt = 1;
                if (a.length > 3) {
                    try { amt = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException e) { Msg.send(s, "<red>Bad number."); return; }
                }
                var item = switch (a[2].toLowerCase()) {
                    case "orb", "totem" -> plugin.items().createOrb(t);
                    case "lantern" -> plugin.items().createLantern();
                    case "core", "devotion_core" -> plugin.items().createCore(1);
                    default -> null;
                };
                if (item == null) { Msg.send(s, "<red>Item must be totem, lantern or core."); return; }
                item.setAmount(amt);
                plugin.items().give(t, item);
                Msg.send(s, "<gray>Gave <white>" + amt + "x " + a[2].toLowerCase() + "<gray> to <white>" + Msg.esc(t.getName()));
            }
            case "rift" -> rift(s, a);
            case "reroll" -> reroll(s, a);
            case "fx" -> fx(s, a);
            case "event" -> plugin.events().command(s, Arrays.copyOfRange(a, 1, a.length));
            case "unlock" -> plugin.unlocks().command(s, true, a);
            case "lock" -> plugin.unlocks().command(s, false, a);
            case "unlocks" -> plugin.unlocks().list(s);
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

    /** /limbo reroll [player|all]: the slot machine, a fresh random darkness (siphon cleared). Defaults to yourself. */
    private void reroll(CommandSender s, String[] a) {
        java.util.List<Player> targets = new ArrayList<>();
        if (a.length < 2) {
            if (!(s instanceof Player p)) { Msg.send(s, "<gray>/limbo reroll <player|all>"); return; }
            targets.add(p);
        } else if (a[1].equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() != GameMode.SPECTATOR) targets.add(p);
        } else {
            Player t = online(s, a[1]); if (t == null) return;
            targets.add(t);
        }
        for (Player t : targets) {
            Darkness d = plugin.data().assignRandom(t.getUniqueId());
            plugin.data().clearSiphon(t.getUniqueId());
            plugin.data().saveIfDirty();
            plugin.items().syncTotems(t);
            if (d == null) continue;
            me.limbo.smp.event.DarknessRoll.play(plugin, t, d, 5, () ->
                    Msg.send(t, "<gray>Your darkness is now <white>" + d.displayName()));
        }
        Msg.send(s, "<gray>Rerolled <white>" + targets.size() + "<gray> player" + (targets.size() == 1 ? "" : "s") + ".");
    }

    /** /limbo fx <test <effect> [player] | clear [player] | mode [shader|bossbar|both|off] | list> */
    private void fx(CommandSender s, String[] a) {
        var sfx = plugin.screenFx();
        String op = a.length > 1 ? a[1].toLowerCase() : "";
        switch (op) {
            case "mode" -> {
                if (a.length < 3) { Msg.send(s, "<gray>Status display: <white>" + sfx.mode() + " <gray>(shader|bossbar|both|off)"); return; }
                String m = a[2].toLowerCase();
                if (!List.of("shader", "bossbar", "both", "off").contains(m)) { Msg.send(s, "<red>shader, bossbar, both or off."); return; }
                sfx.setMode(m);
                Msg.send(s, "<gray>Status display is now <white>" + m);
            }
            case "test" -> {
                var f = a.length > 2 ? me.limbo.smp.status.ScreenFx.Fx.byName(a[2]) : null;
                Player t = a.length > 3 ? online(s, a[3]) : (s instanceof Player p ? p : null);
                if (f == null || t == null) { Msg.send(s, "<gray>/limbo fx test <effect> [player]  (effects: " + fxNames() + ")"); return; }
                sfx.show(t, f, 100);
                Msg.send(s, "<gray>Showing <white>" + f.name().toLowerCase() + "<gray> to <white>" + Msg.esc(t.getName()) + "<gray> for 5s.");
            }
            case "vanilla" -> { // pipeline check: a built-in effect, no resource pack needed
                if (!(s instanceof Player p)) return;
                boolean ok = sfx.raw(p, true, "minecraft:invert");
                Msg.send(p, "<gray>posteffect add minecraft:invert -> " + (ok ? "<green>ran" : "<red>failed") + "<gray>. You should see inverted colours for 3s.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> sfx.raw(p, false, "minecraft:invert"), 60L);
            }
            case "clear" -> {
                Player t = a.length > 2 ? online(s, a[2]) : (s instanceof Player p ? p : null);
                if (t == null) return;
                sfx.clearAll(t);
                Msg.send(s, "<gray>Cleared the screen effects of <white>" + Msg.esc(t.getName()));
            }
            default -> Msg.send(s, "<gray>/limbo fx <vanilla | test <effect> [player] | clear [player] | mode [shader|bossbar|both|off]>  effects: " + fxNames());
        }
    }

    private static String fxNames() {
        StringBuilder sb = new StringBuilder();
        for (var f : me.limbo.smp.status.ScreenFx.Fx.values()) sb.append(sb.length() > 0 ? ", " : "").append(f.name().toLowerCase());
        return sb.toString();
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
                    case "reroll" -> { out.add("all"); Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); }
                    case "fx" -> out.addAll(List.of("vanilla", "test", "clear", "mode"));
                    case "rift" -> out.addAll(List.of("create", "remove", "list", "spawn", "interval", "count", "info"));
                    case "cooldown" -> out.add("reset");
                    case "event" -> out.addAll(List.of("start", "stop", "end", "status", "deity"));
                    case "unlock", "lock" -> out.addAll(List.of("nether", "villagers", "rifts", "enchants"));
                    case "cursed" -> out.addAll(List.of("list", "roll", "set", "pool", "on", "off"));
                    default -> {}
                }
            } else if (a.length == 3) {
                switch (sub) {
                    case "set" -> out.addAll(plugin.registry().ids());
                    case "fx" -> {
                        if (a[1].equalsIgnoreCase("test")) for (var f : me.limbo.smp.status.ScreenFx.Fx.values()) out.add(f.name().toLowerCase());
                        else if (a[1].equalsIgnoreCase("mode")) out.addAll(List.of("shader", "bossbar", "both", "off"));
                        else Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    }
                    case "cursed" -> { if (a[1].equalsIgnoreCase("pool")) out.addAll(List.of("add", "remove", "list", "clear")); }
                    case "give" -> out.addAll(List.of("totem", "lantern", "core"));
                    case "cooldown" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    case "event" -> {
                        if (a[1].equalsIgnoreCase("deity")) out.addAll(List.of("spawn", "set", "remove", "awaken", "sleep", "say", "shatter"));
                        else out.addAll(List.of("server_start", "progression", "dispersal"));
                    }
                    default -> {}
                }
            }
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).toList();
    }
}
