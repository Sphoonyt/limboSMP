package me.limbo.smp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {
    public static final String PREFIX = "<dark_gray>[<#9b59ff>Limbo<dark_gray>] <gray>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component parse(String s) { return MM.deserialize(s); }

    public static String esc(String s) { return MM.escapeTags(s); }

    public static void send(CommandSender to, String s) { to.sendMessage(parse(PREFIX + s)); }
}
