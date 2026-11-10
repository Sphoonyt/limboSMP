package me.limbo.smp.input;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.Darkness;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ability bar, drawn on the action bar with the resource pack's limbo:hud font:
 *
 *     [channel 1 icon] ready        [totem]        [channel 2 icon] 42s
 *
 * Each channel shows its own icon (tinted your darkness colour when ready, dim grey while cooling down) and either
 * "ready" or the seconds left. The totem in the middle is the flux indicator: hollow normally, filled in your darkness
 * colour while your flux is active, solid black while you have darkness fatigue.
 * Shown whenever a null totem is anywhere in your inventory (and while holding the cursed item, where only channel 1 is usable).
 * Art + glyph table: tools/gen_hud.py. Keep the code points below in sync with it.
 */
public final class CooldownHud {
    private static final Key FONT = Key.key("limbo", "hud");

    /** Channel id -> glyph (order of the icons in tools/gen_hud.py). */
    private static final Map<String, Character> ICONS = Map.ofEntries(
            Map.entry("enchain", '\uE100'), Map.entry("black_flash", '\uE101'), Map.entry("drought_compression", '\uE102'),
            Map.entry("open_furnace", '\uE103'), Map.entry("propel", '\uE104'), Map.entry("kinetic_thief", '\uE105'),
            Map.entry("imbalance_ring", '\uE106'), Map.entry("temporal_repose", '\uE107'), Map.entry("reality_shatter", '\uE108'),
            Map.entry("reflected_construct", '\uE109'), Map.entry("axiom_dagger", '\uE10A'), Map.entry("black_hole", '\uE10B'),
            Map.entry("evolution", '\uE10C'), Map.entry("silencing_maw", '\uE10D'));
    private static final char FLUX_OUTLINE = '\uE110', FLUX_FILLED = '\uE111';
    private static final char DIGIT0 = '\uE120', S = '\uE12A', R = '\uE12B', E = '\uE12C', A = '\uE12D', D = '\uE12E', Y = '\uE12F';
    private static final char CELL = '\uE130', GAP = '\uE131', BIG_GAP = '\uE132';
    private static final int FIELD_CELLS = 5; // "ready" is 5 cells; numbers are padded to the same width so the bar never shifts

    private static final TextColor DIM = TextColor.color(0x55555A);
    private static final TextColor HOLLOW = TextColor.color(0xB8B8C0);
    private static final TextColor BLACK = TextColor.color(0x000000);

    private final LimboPlugin plugin;
    private BukkitTask task;

    public CooldownHud(LimboPlugin plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) show(p);
        }, 10L, 4L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    private boolean hasNullTotem(Player p) {
        for (ItemStack s : p.getInventory().getContents()) if (s != null && plugin.items().isOrb(s)) return true;
        return plugin.items().isOrb(p.getInventory().getItemInOffHand());
    }

    private void show(Player p) {
        if (p.isDead()) return;
        boolean totem = hasNullTotem(p);
        boolean cursed = !totem && plugin.cursed().isHoldingActive(p);
        if (!totem && !cursed) return;
        Darkness d = plugin.data().getDarkness(p);
        if (d == null) return;
        List<Channel> channels = d.channels();
        if (channels.isEmpty()) return;
        UUID id = p.getUniqueId();
        Color c = d.color();
        TextColor mine = TextColor.color(c.getRed(), c.getGreen(), c.getBlue());

        Component bar = Component.empty();
        bar = bar.append(channel(d, channels.get(0), id, mine, true));
        bar = bar.append(glyphs(String.valueOf(BIG_GAP), null));

        boolean fatigued = plugin.fatigue().isFatigued(id);
        boolean flux = !fatigued && plugin.fluxActive(p, d.id());
        if (fatigued) bar = bar.append(glyphs(String.valueOf(FLUX_FILLED), BLACK));
        else if (flux) bar = bar.append(glyphs(String.valueOf(FLUX_FILLED), mine));
        else bar = bar.append(glyphs(String.valueOf(FLUX_OUTLINE), HOLLOW));

        bar = bar.append(glyphs(String.valueOf(BIG_GAP), null));
        if (channels.size() > 1) bar = bar.append(channel(d, channels.get(1), id, mine, !cursed));
        p.sendActionBar(bar);
    }

    /** icon, a gap, then "ready" or the seconds, padded to a fixed width. `usable` = false dims it (cursed item: channel 2). */
    private Component channel(Darkness d, Channel ch, UUID id, TextColor mine, boolean usable) {
        Character icon = ICONS.get(ch.id());
        long left;
        if (ch.usesCustomCooldown()) {
            String charges = ch.hudCharges(id); // "1/2": usable while at least one charge is back
            boolean anyReady = charges != null && !charges.startsWith("0");
            left = anyReady ? 0 : ch.hudRemaining(id);
        } else {
            left = plugin.dispatcher().cooldowns().remaining(id, d.id() + ":" + ch.id());
        }
        boolean ready = left <= 0;

        Component out = Component.empty();
        if (icon != null) out = out.append(glyphs(String.valueOf(icon), usable && ready ? mine : DIM));
        out = out.append(glyphs(String.valueOf(GAP), null));
        if (!usable) return out.append(glyphs(pad(""), null));
        if (ready) return out.append(glyphs("" + R + E + A + D + Y, TextColor.color(0xFFFFFF)));
        long secs = (left + 999) / 1000;
        StringBuilder num = new StringBuilder();
        for (char ch2 : String.valueOf(secs).toCharArray()) num.append((char) (DIGIT0 + (ch2 - '0')));
        num.append(S);
        return out.append(glyphs(pad(num.toString()), TextColor.color(0xE0E0E6)));
    }

    private static String pad(String cells) {
        StringBuilder sb = new StringBuilder(cells);
        while (sb.length() < FIELD_CELLS) sb.append(CELL);
        return sb.toString();
    }

    private static Component glyphs(String s, TextColor color) {
        Component c = Component.text(s).font(FONT);
        return color == null ? c : c.color(color);
    }
}
