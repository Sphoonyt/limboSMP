package me.limbo.smp.event;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.ParticleUtil;
import me.limbo.smp.util.SpotFinder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * The Big Rift the deity opens at the end of the server start event: a huge tear in the air that pulls everyone
 * up and in, then sends them out across the world. People standing next to each other (a "group") arrive together.
 */
public final class BigRift {
    private static final Color VOID = Color.fromRGB(10, 8, 16);
    private static final Color DEEP = Color.fromRGB(45, 12, 80);
    private static final Color PURPLE = Color.fromRGB(155, 89, 255);
    private static final Color LIGHT = Color.fromRGB(228, 205, 255);

    private final LimboPlugin plugin;
    private BukkitTask task;
    private Location base;     // bottom centre of the rift
    private double yaw;
    private int ticks;
    private int openTicks = 100;
    private boolean closing;
    private final Set<UUID> pulled = new HashSet<>();

    public BigRift(LimboPlugin plugin) { this.plugin = plugin; }

    public boolean isOpen() { return task != null; }

    // ------------------------------------------------------------ the rift itself

    /** Opens the rift with its bottom centre at `bottom`. It grows over `openTicks`, then stays until close(). */
    public void open(Location bottom, double facingYaw, int openTicks) {
        close();
        this.base = bottom.clone();
        this.yaw = facingYaw;
        this.openTicks = Math.max(20, openTicks);
        this.ticks = 0;
        this.closing = false;
        World w = base.getWorld();
        w.playSound(base.clone().add(0, 8, 0), Sound.BLOCK_END_PORTAL_SPAWN, 5f, 0.5f);
        w.playSound(base.clone().add(0, 8, 0), Sound.ENTITY_WARDEN_EMERGE, 5f, 0.6f);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 2L);
    }

    /** Collapses the rift with a flash. */
    public void close() {
        if (task == null) return;
        task.cancel();
        task = null;
        pulled.clear();
        if (base != null && base.getWorld() != null) {
            Location c = base.clone().add(0, 9, 0);
            ParticleUtil.burst(c, PURPLE, 80);
            base.getWorld().playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 5f, 0.5f);
        }
    }

    /** From now on these players are drawn up into the rift. */
    public void pull(Collection<Player> players) {
        for (Player p : players) pulled.add(p.getUniqueId());
    }

    private void tick() {
        ticks += 2;
        World w = base.getWorld();
        if (w == null) { close(); return; }
        double open = Math.min(1.0, ticks / (double) openTicks);
        open = 1 - Math.pow(1 - open, 3);
        render(w, open);
        if (ticks % 20 == 0) w.playSound(base.clone().add(0, 9, 0), Sound.BLOCK_PORTAL_AMBIENT, 4f, 0.5f);

        if (open > 0.6 && !pulled.isEmpty()) {
            Location centre = base.clone().add(0, 9, 0);
            for (UUID id : new ArrayList<>(pulled)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || p.getWorld() != w) continue;
                if (p.getLocation().distanceSquared(centre) > 70 * 70) continue;
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0, false, false, false));
                Vector to = centre.toVector().subtract(p.getLocation().toVector()).setY(0);
                if (to.lengthSquared() > 4) {
                    p.setVelocity(p.getVelocity().add(to.normalize().multiply(0.07)));
                }
            }
        }
    }

    private void render(World w, double open) {
        double cos = Math.cos(Math.toRadians(yaw)), sin = Math.sin(Math.toRadians(yaw));
        double W = 5.5 * open, H = 10 * open, t = ticks;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // torn, wobbling edge
        int edge = 90;
        for (int k = 0; k < edge; k++) {
            double a = Math.PI * 2 * k / edge;
            double n = 1 + 0.12 * Math.sin(7 * a + t * 0.17) + 0.08 * Math.sin(13 * a - t * 0.29);
            Location l = local(cos, sin, Math.cos(a) * W * n, 10 * open + Math.sin(a) * H * n);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0,
                    new Particle.DustTransition(k % 4 == 0 ? LIGHT : PURPLE, VOID, 1.7f), true);
        }
        // swirling inside
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 1; i <= 14; i++) {
                double u = i / 14.0;
                double ang = arm * Math.PI * 2 / 3 + u * 4 - t * 0.12;
                Location l = local(cos, sin, Math.cos(ang) * W * u, 10 * open + Math.sin(ang) * H * u);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0,
                        new Particle.DustTransition(DEEP, PURPLE, 1.5f), true);
            }
        }
        // things being sucked in
        Location centre = local(cos, sin, 0, 10 * open);
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(0.4, 1.0);
            Location from = local(cos, sin, Math.cos(a) * W * 1.6 * d, 10 * open + Math.sin(a) * H * 1.3 * d);
            Vector dir = centre.toVector().subtract(from.toVector()).normalize();
            w.spawnParticle(Particle.REVERSE_PORTAL, from, 0, dir.getX(), dir.getY(), dir.getZ(), 0.6, null, true);
        }
        if (open > 0.8 && r.nextInt(3) == 0) { // crackle
            double a = r.nextDouble(Math.PI * 2);
            Location e1 = local(cos, sin, Math.cos(a) * W, 10 * open + Math.sin(a) * H);
            Location e2 = local(cos, sin, Math.cos(a + 0.6) * W * 1.4, 10 * open + Math.sin(a + 0.6) * H * 1.4);
            for (int i = 0; i <= 8; i++) {
                double f = i / 8.0;
                w.spawnParticle(Particle.END_ROD, e1.clone().add(e2.clone().subtract(e1).toVector().multiply(f)), 1, 0, 0, 0, 0, null, true);
            }
        }
    }

    /** A point on the rift's plane: x across, y up. */
    private Location local(double cos, double sin, double x, double y) {
        return new Location(base.getWorld(), base.getX() + x * -cos, base.getY() + y, base.getZ() + x * -sin);
    }

    // ------------------------------------------------------------ groups

    /** Players within `radius` blocks of each other (chained) end up in the same group. */
    public static List<List<Player>> groups(Collection<Player> players, double radius) {
        List<Player> list = new ArrayList<>(players);
        int n = list.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double r2 = radius * radius;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Player a = list.get(i), b = list.get(j);
                if (a.getWorld() != b.getWorld()) continue;
                if (a.getLocation().distanceSquared(b.getLocation()) <= r2) union(parent, i, j);
            }
        }
        java.util.Map<Integer, List<Player>> byRoot = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(list.get(i));
        return new ArrayList<>(byRoot.values());
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

    // ------------------------------------------------------------ sending people out

    /**
     * Teleports every group to its own random spot inside the world border (the group stays together, spread in a
     * small ring). `each` runs for every player that arrived, `done` once everyone has been handled.
     */
    public void sendOut(World world, List<List<Player>> groups, Consumer<Player> each, Runnable done) {
        if (groups.isEmpty()) { if (done != null) done.run(); return; }
        SpotFinder.find(plugin, world, groups.size(), 400, spots -> {
            if (spots.isEmpty()) {
                Bukkit.getLogger().warning("Big Rift: no valid spots found inside the world border.");
                for (List<Player> g : groups) for (Player p : g) Msg.send(p, "<red>Couldn't find you a spot, ask an op.");
                if (done != null) done.run();
                return;
            }
            for (int gi = 0; gi < groups.size(); gi++) {
                List<Player> g = groups.get(gi);
                Location spot = spots.get(gi % spots.size());
                int n = g.size(), i = 0;
                for (Player p : g) {
                    if (!p.isOnline()) { i++; continue; }
                    Location dest = around(spot, i++, n);
                    arrive(p, dest, each);
                }
            }
            if (done != null) Bukkit.getScheduler().runTaskLater(plugin, done, 10L);
        });
    }

    private void arrive(Player p, Location dest, Consumer<Player> each) {
        ParticleUtil.burst(p.getLocation().add(0, 1, 0), PURPLE, 50);
        p.leaveVehicle();
        p.removePotionEffect(PotionEffectType.LEVITATION);
        p.setFallDistance(0f);
        p.teleportAsync(dest).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setFallDistance(0f);
            Location at = p.getLocation().add(0, 1, 0);
            ParticleUtil.burst(at, PURPLE, 60);
            p.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.7f);
            p.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            if (ok && each != null) each.accept(p);
        }));
    }

    /** Spreads a group in a small ring around the spot, each member on real ground. */
    private static Location around(Location spot, int index, int count) {
        if (count <= 1) return spot.clone();
        World w = spot.getWorld();
        double ang = Math.PI * 2 * index / count;
        double rad = Math.min(4.0, 1.0 + 0.6 * count);
        int x = (int) Math.floor(spot.getX() + Math.cos(ang) * rad);
        int z = (int) Math.floor(spot.getZ() + Math.sin(ang) * rad);
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block ground = w.getBlockAt(x, y, z);
        boolean ok = ground.getType().isSolid() && !ground.isLiquid()
                && w.getBlockAt(x, y + 1, z).isPassable() && w.getBlockAt(x, y + 2, z).isPassable()
                && Math.abs(y + 1 - spot.getY()) <= 6;
        return ok ? new Location(w, x + 0.5, y + 1, z + 0.5) : spot.clone();
    }
}
