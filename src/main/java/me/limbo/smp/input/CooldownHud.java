package me.limbo.smp.input;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Channel;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/**
 * Cooldowns on the action bar. Shown while you hold your null totem or the cursed item (so it never covers other
 * action bar text the rest of the time). One entry per channel: its name, then "ready" or the seconds left.
 * Channels with charges (Axiom Dagger, Reflected Construct) also show how many charges are ready.
 */
public final class CooldownHud {
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

    private void show(Player p) {
        if (p.isDead()) return;
        boolean orb = plugin.items().isOrb(p.getInventory().getItemInMainHand()) || plugin.items().isOrb(p.getInventory().getItemInOffHand());
        boolean cursed = !orb && plugin.cursed().isHoldingActive(p);
        if (!orb && !cursed) return;
        Darkness d = plugin.data().getDarkness(p);
        if (d == null) return;
        List<Channel> channels = d.channels();
        if (channels.isEmpty()) return;
        UUID id = p.getUniqueId();
        Color c = d.color();
        String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());

        StringBuilder sb = new StringBuilder();
        int n = cursed ? 1 : channels.size(); // the cursed item only unlocks the first channel
        for (int i = 0; i < n; i++) {
            Channel ch = channels.get(i);
            long left = ch.usesCustomCooldown() ? ch.hudRemaining(id) : plugin.dispatcher().cooldowns().remaining(id, d.id() + ":" + ch.id());
            String charges = ch.usesCustomCooldown() ? ch.hudCharges(id) : null;
            if (sb.length() > 0) sb.append("  <dark_gray>|  ");
            sb.append('<').append(hex).append('>').append(Msg.esc(ch.name())).append(' ');
            if (charges != null) sb.append("<white>").append(charges).append(' ');
            if (left <= 0) sb.append("<green>ready");
            else sb.append("<red>").append(left >= 10_000 ? String.valueOf((left + 999) / 1000) : String.format("%.1f", left / 1000.0)).append('s');
        }
        p.sendActionBar(Msg.parse(sb.toString()));
    }
}
