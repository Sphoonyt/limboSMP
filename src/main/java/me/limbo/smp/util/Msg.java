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

    /**
     * Marker put on ability feedback lines. The Limbo FX client mod removes any chat line carrying it 10 seconds
     * after it arrives (it is a shift-click insertion string, invisible and harmless for players without the mod).
     */
    public static final String FADE_TAG = "limbo_fb";

    /** Like send(), but the line fades out of chat after 10 seconds for players running the Limbo FX mod. */
    public static void sendFading(CommandSender to, String s) { to.sendMessage(parse(PREFIX + s).insertion(FADE_TAG)); }
}
