package me.limbo.smp.rift;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-click a rift while holding any ingredient of a recipe; if the inventory holds
 * all ingredients they are consumed and the result is given. Recipes live in config.yml (placeholders).
 */
public final class RiftCrafting {
    private record Ingredient(Material material, int amount) {}
    private record Recipe(String id, List<Ingredient> ingredients) {}

    private final LimboPlugin plugin;
    private final List<Recipe> recipes = new ArrayList<>();

    public RiftCrafting(LimboPlugin plugin) { this.plugin = plugin; }

    public void reload() {
        recipes.clear();
        var sec = plugin.getConfig().getConfigurationSection("recipes");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            List<Ingredient> list = new ArrayList<>();
            for (String s : sec.getStringList(id + ".ingredients")) {
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
            if (!list.isEmpty()) recipes.add(new Recipe(id, list));
        }
    }

    private ItemStack result(String id) {
        return switch (id) {
            case "null-orb" -> plugin.items().createOrb();
            case "shadow-lantern" -> plugin.items().createLantern();
            default -> null;
        };
    }

    private int count(PlayerInventory inv, Material m) {
        ItemStack plain = new ItemStack(m);
        int total = 0;
        for (ItemStack s : inv.getStorageContents()) if (s != null && s.isSimilar(plain)) total += s.getAmount();
        return total;
    }

    /** @return true if the click was consumed by the rift (crafted or told what's missing). */
    public boolean tryCraft(Player p, ItemStack held, Location riftLoc) {
        if (held == null || held.getType().isAir()) {
            Msg.send(p, "<#9b59ff>The rift hums. <gray>Offer it ingredients.");
            return true;
        }
        PlayerInventory inv = p.getInventory();
        List<String> firstMissing = null;

        for (Recipe r : recipes) {
            if (r.ingredients().stream().noneMatch(i -> i.material() == held.getType())) continue;
            List<String> missing = new ArrayList<>();
            for (Ingredient i : r.ingredients()) {
                int have = count(inv, i.material());
                if (have < i.amount()) missing.add((i.amount() - have) + "x " + i.material().name().toLowerCase());
            }
            if (missing.isEmpty()) {
                ItemStack out = result(r.id());
                if (out == null) continue;
                for (Ingredient i : r.ingredients()) inv.removeItem(new ItemStack(i.material(), i.amount()));
                plugin.items().give(p, out);
                Location fx = riftLoc.clone().add(0.5, 1.2, 0.5);
                ParticleUtil.burst(fx, ParticleUtil.RIFT_ACCENT, 40);
                p.getWorld().playSound(fx, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
                Msg.send(p, "<#9b59ff>The rift answers.");
                return true;
            }
            if (firstMissing == null) firstMissing = missing;
        }

        if (firstMissing != null) {
            Msg.send(p, "<red>The rift is unsatisfied. <gray>Missing: <white>" + String.join(", ", firstMissing));
        } else {
            Msg.send(p, "<gray>That means nothing to the rift.");
        }
        return true;
    }
}
