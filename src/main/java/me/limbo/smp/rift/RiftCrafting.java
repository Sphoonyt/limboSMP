package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recipes for the null totem and shadow lantern. Defaults are built in; ops can override them
 * in the rift GUI, which writes recipes.yml.
 */
public final class RiftCrafting {
    public record Ingredient(Material material, int amount) {}

    public static final String ORB = "null-orb";
    public static final String LANTERN = "shadow-lantern";
    /** Cheaper recipes, used by a rift while its relocation timer is running. */
    public static final String ORB_ACTIVE = "null-orb-active";
    public static final String LANTERN_ACTIVE = "shadow-lantern-active";

    /** The recipe key for an item, depending on whether the rift is on its timer. */
    public static String key(String base, boolean active) {
        return active ? base + "-active" : base;
    }

    /** Default requirements. Ops override them in the rift GUI, which writes recipes.yml. */
    private static final Map<String, List<String>> DEFAULTS = Map.of(
            ORB, List.of("TOTEM_OF_UNDYING:1", "WITHER_SKELETON_SKULL:1", "ECHO_SHARD:1", "SCULK:32",
                    "AMETHYST_SHARD:4", "AMETHYST_BLOCK:4"),
            LANTERN, List.of("TOTEM_OF_UNDYING:1", "SCULK_CATALYST:2", "SOUL_SOIL:16", "SOUL_SAND:16",
                    "SOUL_LANTERN:1"),
            ORB_ACTIVE, List.of("WITHER_SKELETON_SKULL:1", "ECHO_SHARD:1", "SCULK:16", "AMETHYST_SHARD:2",
                    "AMETHYST_BLOCK:2"),
            LANTERN_ACTIVE, List.of("SCULK_CATALYST:1", "SOUL_SOIL:8", "SOUL_SAND:8", "SOUL_LANTERN:1"));

    private final LimboPlugin plugin;
    private final File file;
    private final Map<String, List<Ingredient>> recipes = new LinkedHashMap<>();

    public RiftCrafting(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recipes.yml");
    }

    public void reload() {
        recipes.clear();
        YamlConfiguration y = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        for (String id : new String[]{ORB, LANTERN, ORB_ACTIVE, LANTERN_ACTIVE}) {
            String path = "recipes." + id + ".ingredients";
            List<String> raw = y.contains(path) ? y.getStringList(path) : DEFAULTS.get(id);
            List<Ingredient> parsed = parse(id, raw);
            if (parsed.isEmpty()) parsed = parse(id, DEFAULTS.get(id)); // never leave a recipe empty
            recipes.put(id, parsed);
        }
    }

    private List<Ingredient> parse(String id, List<String> raw) {
        List<Ingredient> list = new ArrayList<>();
        for (String s : raw) {
            String[] p = s.split(":");
            Material m = Material.matchMaterial(p[0]);
            int amt = 1;
            try { if (p.length > 1) amt = Math.max(1, Integer.parseInt(p[1])); } catch (NumberFormatException ignored) {}
            if (m == null) {
                plugin.getLogger().warning("Unknown material in recipe " + id + ": " + p[0]);
                continue;
            }
            list.add(new Ingredient(m, amt));
        }
        return list;
    }

    public List<Ingredient> ingredients(String id) {
        return recipes.getOrDefault(id, List.of());
    }

    /** Saves a new requirement list (written to recipes.yml). */
    public void setIngredients(String id, List<Ingredient> list) {
        YamlConfiguration y = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        y.set("recipes." + id + ".ingredients", list.stream().map(i -> i.material().name() + ":" + i.amount()).toList());
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save recipes.yml: " + ex.getMessage());
        }
        recipes.put(id, new ArrayList<>(list));
    }

    /** Counts plain (unnamed) items of a material in the main inventory. */
    public int count(PlayerInventory inv, Material m) {
        ItemStack plain = new ItemStack(m);
        int total = 0;
        for (ItemStack s : inv.getStorageContents()) if (s != null && s.isSimilar(plain)) total += s.getAmount();
        return total;
    }

    private ItemStack result(String id, Player p) {
        return switch (id) {
            case ORB, ORB_ACTIVE -> plugin.items().createOrb(p);
            case LANTERN, LANTERN_ACTIVE -> plugin.items().createLantern();
            default -> null;
        };
    }

    /** @return true if crafted. Sends its own feedback. */
    public boolean craft(Player p, String id) {
        List<Ingredient> ing = recipes.get(id);
        ItemStack out = result(id, p);
        if (ing == null || out == null) return false;
        PlayerInventory inv = p.getInventory();

        List<String> missing = new ArrayList<>();
        for (Ingredient i : ing) {
            int have = count(inv, i.material());
            if (have < i.amount()) missing.add((i.amount() - have) + "x " + i.material().name().toLowerCase().replace('_', ' '));
        }
        if (!missing.isEmpty()) {
            Msg.send(p, "<red>The rift is unsatisfied. <gray>Missing: <white>" + String.join(", ", missing));
            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 1.2f);
            return false;
        }
        for (Ingredient i : ing) inv.removeItem(new ItemStack(i.material(), i.amount()));
        plugin.items().give(p, out);

        Location fx = p.getLocation().add(0, 1, 0);
        ParticleUtil.burst(fx, ParticleUtil.RIFT_ACCENT, 40);
        p.getWorld().playSound(fx, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
        Msg.send(p, "<#9b59ff>The rift answers.");
        return true;
    }
}
