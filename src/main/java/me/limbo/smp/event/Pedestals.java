package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The blocks the marked players have to stand on to finish the transition event. They are unmineable (and immune to
 * explosions and pistons) netherite blocks set into the ground in a ring around the deity. The blocks they replaced
 * are remembered (events.yml) and put back when the event ends.
 */
public final class Pedestals implements Listener {
    public record Spot(String world, int x, int y, int z, String original) {}

    private final LimboPlugin plugin;
    private final List<Spot> spots = new ArrayList<>();

    public Pedestals(LimboPlugin plugin) { this.plugin = plugin; }

    public List<Spot> spots() { return Collections.unmodifiableList(spots); }
    public int size() { return spots.size(); }
    public boolean isEmpty() { return spots.isEmpty(); }

    // ------------------------------------------------------------ persistence

    public void load(YamlConfiguration y) {
        spots.clear();
        for (String s : y.getStringList("progression.pedestals")) {
            String[] a = s.split("\\|", 5);
            if (a.length < 5) continue;
            try {
                spots.add(new Spot(a[0], Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), a[4]));
            } catch (NumberFormatException ignored) {}
        }
    }

    public void save(YamlConfiguration y) {
        List<String> out = new ArrayList<>();
        for (Spot s : spots) out.add(s.world() + "|" + s.x() + "|" + s.y() + "|" + s.z() + "|" + s.original());
        y.set("progression.pedestals", out);
    }

    // ------------------------------------------------------------ placing / removing

    /** Sets `count` blocks into the ground in an even ring of the given radius around `centre`. */
    public void place(World w, Location centre, int count, double radius, Material material) {
        remove();
        double offset = Math.PI / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + offset;
            int x = (int) Math.floor(centre.getX() + Math.cos(a) * radius);
            int z = (int) Math.floor(centre.getZ() + Math.sin(a) * radius);
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block b = w.getBlockAt(x, y, z);
            spots.add(new Spot(w.getName(), x, y, z, b.getBlockData().getAsString()));
            b.setType(material, false);
            for (int k = 1; k <= 3; k++) { // room to stand
                Block above = w.getBlockAt(x, y + k, z);
                if (!above.getType().isAir()) above.setType(Material.AIR, false);
            }
        }
        plugin.events().save();
    }

    /** Puts the ground back the way it was. */
    public void remove() {
        for (Spot s : spots) {
            World w = Bukkit.getWorld(s.world());
            if (w == null) continue;
            try {
                w.getBlockAt(s.x(), s.y(), s.z()).setBlockData(Bukkit.createBlockData(s.original()), false);
            } catch (IllegalArgumentException ex) {
                w.getBlockAt(s.x(), s.y(), s.z()).setType(Material.DIRT, false);
            }
        }
        spots.clear();
    }

    // ------------------------------------------------------------ queries

    public boolean isPedestal(Block b) {
        if (spots.isEmpty()) return false;
        for (Spot s : spots) {
            if (s.x() == b.getX() && s.y() == b.getY() && s.z() == b.getZ() && s.world().equals(b.getWorld().getName())) return true;
        }
        return false;
    }

    /** The top centre of a pedestal (where a player would stand). */
    public Location top(int i) {
        Spot s = spots.get(i);
        World w = Bukkit.getWorld(s.world());
        return new Location(w, s.x() + 0.5, s.y() + 1.0, s.z() + 0.5);
    }

    /** Which pedestal the player is standing on, or -1. */
    public int indexAt(Player p) {
        Location l = p.getLocation();
        String world = l.getWorld().getName();
        for (int i = 0; i < spots.size(); i++) {
            Spot s = spots.get(i);
            if (!s.world().equals(world)) continue;
            if (l.getBlockX() == s.x() && l.getBlockZ() == s.z() && l.getY() >= s.y() + 0.9 && l.getY() <= s.y() + 1.6) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------ unmineable

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isPedestal(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!spots.isEmpty()) e.blockList().removeIf(this::isPedestal);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!spots.isEmpty()) e.blockList().removeIf(this::isPedestal);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (spots.isEmpty()) return;
        for (Block b : e.getBlocks()) if (isPedestal(b)) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (spots.isEmpty()) return;
        for (Block b : e.getBlocks()) if (isPedestal(b)) { e.setCancelled(true); return; }
    }
}
