package me.limbo.smp.event.deity;

import me.limbo.smp.LimboPlugin;

import java.util.List;
import java.util.Map;

/**
 * The deity's "voice lines". They are never spoken, they show up on the action bar like subtitles.
 * These are placeholders in a vague, lore-like voice. Override any list in config.yml under
 * events.deity.lines.<key> (same keys as below) without touching code.
 */
public final class DeityLines {
    private DeityLines() {}

    private static final Map<String, List<String>> DEFAULTS = Map.ofEntries(
            // ---- server start
            Map.entry("awaken", List.of(
                    "...so many of you.",
                    "You fell a long way to reach me.",
                    "I will remember none of your names. Only what you carry.")),
            Map.entry("group", List.of(
                    "The dark does not take the lonely. It takes the scattered.",
                    "Find those you would not leave behind.",
                    "Stand close. What stands apart is lost.")),
            Map.entry("group-warning", List.of(
                    "The threshold stirs.",
                    "Those who are not near each other will not arrive together.")),
            Map.entry("darkness", List.of(
                    "Each of you is hollow in a different shape.",
                    "Let me fill you.")),
            Map.entry("cursed", List.of(
                    "Some things have already been touched by the dark.",
                    "Hold them, and your hollow will answer.")),
            Map.entry("rift", List.of(
                    "Go. Be scattered across the Limbo.",
                    "Survive it.")),
            // ---- transition
            Map.entry("reawaken", List.of(
                    "I stir again.",
                    "You have lived in the shallows. That was the easy part.")),
            Map.entry("summon", List.of(
                    "You. Rise.",
                    "You carry the mark now. Let the others come for it.")),
            Map.entry("launch", List.of("Run.")),
            Map.entry("pedestals", List.of(
                    "Eight stones. Eight marked. Stand upon them together.",
                    "Hold what is yours, or lose it to someone hungrier.")),
            Map.entry("charging", List.of(
                    "Yes... like that.",
                    "Do not move.")),
            Map.entry("finale", List.of(
                    "Enough.",
                    "I was never the wall. I was the lock.",
                    "Go on. Grow."))
    );

    public static List<String> get(LimboPlugin plugin, String key) {
        List<String> custom = plugin.getConfig().getStringList("events.deity.lines." + key);
        if (!custom.isEmpty()) return custom;
        return DEFAULTS.getOrDefault(key, List.of());
    }
}
