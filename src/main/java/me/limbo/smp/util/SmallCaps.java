package me.limbo.smp.util;

/** Converts letters to Unicode small caps (ᴀʙᴄ...). Digits and symbols are left alone. */
public final class SmallCaps {
    // a-z (x has no small-cap glyph, so it stays "x")
    private static final String MAP = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    public static String of(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 'a' && c <= 'z') sb.append(MAP.charAt(c - 'a'));
            else if (c >= 'A' && c <= 'Z') sb.append(MAP.charAt(c - 'A'));
            else sb.append(c);
        }
        return sb.toString();
    }
}
