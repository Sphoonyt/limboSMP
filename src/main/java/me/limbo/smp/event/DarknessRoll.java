package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.util.ParticleUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The slot machine: a title that spins through every darkness, slowing down until it lands on the one the player
 * already got. The result is decided before the animation starts, this is only the show.
 */
public final class DarknessRoll {
    private DarknessRoll() {}

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Component name(Darkness d, boolean bold) {
        return MiniMessage.miniMessage().deserialize("<" + hex(d.color()) + (bold ? "><bold>" : ">") + d.displayName().toUpperCase());
    }

    /**
     * @param result  the darkness the reel stops on
     * @param seconds unused hint, the length comes from the step count and easing (about 5 seconds)
     * @param done    run after the reveal (may be null)
     */
    public static void play(LimboPlugin plugin, Player p, Darkness result, double seconds, Runnable done) {
        List<Darkness> all = plugin.registry().all();
        if (all.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        int startIndex = ThreadLocalRandom.current().nextInt(all.size());
        // pick the step count so the final symbol is `result`
        int resultIndex = all.indexOf(result);
        int steps = 24 + ThreadLocalRandom.current().nextInt(6);
        int landAt = (resultIndex - startIndex) % all.size();
        if (landAt < 0) landAt += all.size();
        while (steps % all.size() != landAt) steps++;
        final int totalSteps = steps;

        new BukkitRunnable() {
            int step = 0;
            int wait = 0;
            int gap = 2; // ticks between symbols, grows as the reel slows

            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (wait-- > 0) return;

                int idx = (startIndex + step) % all.size();
                Darkness cur = all.get(idx);
                Darkness prev = all.get((idx - 1 + all.size()) % all.size());
                Darkness next = all.get((idx + 1) % all.size());
                boolean last = step >= totalSteps;

                Component sub = Component.text("◂ ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                        .append(name(prev, false).color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                        .append(Component.text("   │   ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                        .append(name(next, false).color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                        .append(Component.text(" ▸", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
                p.showTitle(Title.title(name(cur, true), last
                                ? Component.empty()
                                : sub,
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(last ? 3500 : 400), Duration.ofMillis(last ? 800 : 0))));

                float progress = Math.min(1f, step / (float) totalSteps);
                p.playSound(p.getLocation(), last ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_HAT,
                        last ? 1f : 0.6f, last ? 1f : 0.8f + 0.9f * progress);

                if (last) {
                    reveal(plugin, p, result);
                    cancel();
                    if (done != null) done.run();
                    return;
                }
                step++;
                // ease out: the last few symbols take noticeably longer
                double remaining = (totalSteps - step) / (double) totalSteps;
                gap = remaining > 0.5 ? 1 : remaining > 0.3 ? 2 : remaining > 0.15 ? 4 : remaining > 0.06 ? 7 : 11;
                wait = gap - 1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void reveal(LimboPlugin plugin, Player p, Darkness d) {
        Location l = p.getLocation().add(0, 1, 0);
        ParticleUtil.burst(l, d.color(), 60);
        p.playSound(l, Sound.ITEM_TOTEM_USE, 0.5f, 1.3f);
        p.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
    }
}
