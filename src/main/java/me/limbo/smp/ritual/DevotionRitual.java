package me.limbo.smp.ritual;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.event.DarknessRoll;
import me.limbo.smp.util.Msg;
import me.limbo.smp.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The reroll ritual. Right click a Devotion Core: it is consumed and a circle appears where you stand. It stays
 * there for ritual.seconds (60) with a floating countdown above it. When the timer ends, every player inside the
 * circle is rerolled with the same slot machine display as the first assignment, and the ritual's coordinates are
 * announced. Rituals are saved (rituals.yml) so a restart doesn't eat the core, the clock keeps running.
 */
public final class DevotionRitual implements Listener {
    private static final Color VOID = Color.fromRGB(10, 8, 16);
    private static final Color DEEP = Color.fromRGB(70, 20, 120);
    private static final Color PURPLE = Color.fromRGB(155, 89, 255);
    private static final Color LIGHT = Color.fromRGB(235, 215, 255);

    private static final class Ritual {
        final UUID id = UUID.randomUUID();
        final UUID owner;
        final String world;
        final double x, y, z;
        final long startedAt, endsAt;
        TextDisplay display;
        String shownText = "";
        int lastCountdownSecond = -1;

        Ritual(UUID owner, String world, double x, double y, double z, long startedAt, long endsAt) {
            this.owner = owner; this.world = world; this.x = x; this.y = y; this.z = z;
            this.startedAt = startedAt; this.endsAt = endsAt;
        }

        Location loc() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    private final LimboPlugin plugin;
    private final File file;
    private final List<Ritual> rituals = new ArrayList<>();
    private BukkitTask task;
    private long tick;

    public DevotionRitual(LimboPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rituals.yml");
    }

    private double radius() { return plugin.getConfig().getDouble("ritual.radius", 6.0); }
    private long durationMs() { return (long) (plugin.getConfig().getDouble("ritual.seconds", 60.0) * 1000L); }

    // ------------------------------------------------------------ persistence / lifecycle

    public void load() {
        rituals.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            String b = key + ".";
            try {
                rituals.add(new Ritual(UUID.fromString(y.getString(b + "owner", "")), y.getString(b + "world", ""),
                        y.getDouble(b + "x"), y.getDouble(b + "y"), y.getDouble(b + "z"),
                        y.getLong(b + "started"), y.getLong(b + "ends")));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Ritual r : rituals) {
            String b = "r" + (i++) + ".";
            y.set(b + "owner", r.owner.toString());
            y.set(b + "world", r.world);
            y.set(b + "x", r.x);
            y.set(b + "y", r.y);
            y.set(b + "z", r.z);
            y.set(b + "started", r.startedAt);
            y.set(b + "ends", r.endsAt);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save rituals.yml: " + ex.getMessage());
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Ritual r : rituals) removeDisplay(r);
        save();
    }

    // ------------------------------------------------------------ starting one

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!plugin.items().isCore(hand)) return;
        e.setCancelled(true); // never place the head
        begin(p, hand);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.items().isCore(e.getItemInHand())) e.setCancelled(true);
    }

    private void begin(Player p, ItemStack hand) {
        Location at = p.getLocation();
        double r = radius();
        for (Ritual o : rituals) {
            Location ol = o.loc();
            if (ol != null && ol.getWorld() == at.getWorld() && ol.distanceSquared(at) < (r * 2) * (r * 2)) {
                Msg.send(p, "<red>Another ritual is too close. Move away from it.");
                return;
            }
        }
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);

        long now = System.currentTimeMillis();
        Ritual ritual = new Ritual(p.getUniqueId(), at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), now, now + durationMs());
        rituals.add(ritual);
        save();

        Location c = ritual.loc();
        Sfx.play(c, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.6f, Sfx.Theme.RITUAL);
        Sfx.play(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f, Sfx.Theme.RITUAL);
        for (int i = 0; i < 3; i++) ring(c.getWorld(), c, 1 + i * 2.5, 40 + i * 20, LIGHT, PURPLE, 1.6f);
        Msg.send(p, "<#9b59ff>The ritual begins. <gray>Anyone inside the circle when the timer ends will be rerolled ("
                + (int) (durationMs() / 1000) + "s).");
    }

    // ------------------------------------------------------------ the tick

    private void tick() {
        tick += 4;
        long now = System.currentTimeMillis();
        for (Ritual r : new ArrayList<>(rituals)) {
            Location c = r.loc();
            if (c == null) continue;
            World w = c.getWorld();
            long left = r.endsAt - now;
            if (left <= 0) { complete(r); continue; }
            if (!w.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) continue;

            ensureDisplay(r, c, left);
            if (!w.getNearbyPlayers(c, 100).isEmpty()) render(w, c, r, left, now);

            // players inside get the time on their action bar and ambient sound
            Collection<Player> inside = inside(c);
            if (tick % 20 == 0) {
                String t = timeText(left);
                for (Player p : inside) p.sendActionBar(Msg.parse("<#9b59ff>Reroll ritual <gray>- <white>" + t));
            }
            if (tick % 40 == 0) {
                Sfx.play(c, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 0.7f, Sfx.Theme.RITUAL);
                w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.5f);
            }
            int sec = (int) Math.ceil(left / 1000.0);
            if (sec <= 5 && sec != r.lastCountdownSecond) { // last five seconds: rising ticks
                r.lastCountdownSecond = sec;
                w.playSound(c, Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 0.7f + (5 - sec) * 0.15f);
                w.playSound(c, Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.6f);
            }
        }
    }

    private static String timeText(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private Collection<Player> inside(Location c) {
        double r = radius();
        List<Player> out = new ArrayList<>();
        for (Player p : c.getWorld().getNearbyPlayers(c, r, 6, r)) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double dx = p.getLocation().getX() - c.getX(), dz = p.getLocation().getZ() - c.getZ();
            if (dx * dx + dz * dz <= r * r) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------ the floating countdown

    private void ensureDisplay(Ritual r, Location c, long left) {
        String text = "<#e8b4ff><bold>Reroll Ritual</bold>\n<white>" + timeText(left);
        if (r.display == null || !r.display.isValid()) {
            Location at = c.clone().add(0, 3.4, 0);
            r.display = c.getWorld().spawn(at, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
                td.setShadowed(true);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setViewRange(2.0f);
                td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.6f, 2.6f, 2.6f), new AxisAngle4f()));
            });
            r.shownText = "";
        }
        if (!text.equals(r.shownText)) {
            r.display.text(Msg.parse(text));
            r.shownText = text;
        }
    }

    private void removeDisplay(Ritual r) {
        if (r.display != null && r.display.isValid()) r.display.remove();
        r.display = null;
    }

    // ------------------------------------------------------------ visuals

    private static void ring(World w, Location c, double radius, int points, Color from, Color to, float size) {
        Particle.DustTransition d = new Particle.DustTransition(from, to, size);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius),
                    1, 0, 0, 0, 0, d, true);
        }
    }

    private void render(World w, Location c, Ritual r, long left, long now) {
        double R = radius();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double total = Math.max(1, r.endsAt - r.startedAt);
        double progress = Math.min(1.0, (now - r.startedAt) / total);
        double t = tick * 0.05;
        boolean closing = left < 10_000;
        double pulse = closing ? 0.5 + 0.5 * Math.sin(tick * 0.35) : 0;

        // outer ring: solid, with the progress arc glowing brighter
        int pts = (int) (R * 12);
        Particle.DustTransition base = new Particle.DustTransition(DEEP, VOID, 1.3f);
        Particle.DustTransition lit = new Particle.DustTransition(LIGHT, PURPLE, 1.5f);
        for (int i = 0; i < pts; i++) {
            double f = i / (double) pts, a = Math.PI * 2 * f - Math.PI / 2;
            boolean filled = f <= progress;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * R, 0.12, Math.sin(a) * R),
                    1, 0, 0, 0, 0, filled ? lit : base, true);
        }
        // inner ring turning one way, rune dashes turning the other
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48 + t;
            if (Math.sin(a * 8 - t * 4) < 0.2) continue;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(Math.cos(a) * R * 0.72, 0.12, Math.sin(a) * R * 0.72),
                    1, 0, 0, 0, 0, new Particle.DustTransition(PURPLE, DEEP, 1.2f), true);
        }
        for (int k = 0; k < 6; k++) { // a hexagram turning slowly in the middle
            double a1 = Math.PI * 2 * k / 6 - t * 0.6, a2 = Math.PI * 2 * (k + 2) / 6 - t * 0.6;
            for (int i = 0; i <= 8; i++) {
                double f = i / 8.0;
                double x = Math.cos(a1) * R * 0.5 * (1 - f) + Math.cos(a2) * R * 0.5 * f;
                double z = Math.sin(a1) * R * 0.5 * (1 - f) + Math.sin(a2) * R * 0.5 * f;
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(x, 0.14, z), 1, 0, 0, 0, 0,
                        new Particle.DustTransition(PURPLE, LIGHT, 1.0f), true);
            }
        }
        // motes rising, a pillar that grows with the progress, sparks near the end
        int motes = 4 + (int) (progress * 6);
        for (int i = 0; i < motes; i++) {
            double a = rnd.nextDouble(Math.PI * 2), d = Math.sqrt(rnd.nextDouble()) * R * 0.9;
            w.spawnParticle(Particle.REVERSE_PORTAL, c.clone().add(Math.cos(a) * d, 0.2, Math.sin(a) * d), 0, 0, 1, 0, 0.4 + progress * 0.6, null, true);
        }
        double height = 2 + progress * 7;
        for (int i = 0; i < 6; i++) {
            double y = rnd.nextDouble(height);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.clone().add(rnd.nextDouble(-0.35, 0.35), y, rnd.nextDouble(-0.35, 0.35)),
                    1, 0, 0, 0, 0, new Particle.DustTransition(LIGHT, PURPLE, 1.3f), true);
        }
        if (closing) {
            w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1 + pulse * 2, 0), 2, R * 0.4, 0.5, R * 0.4, 0.02, null, true);
            ring(w, c, R * (0.3 + 0.7 * pulse), 40, LIGHT, PURPLE, 1.4f);
        }
    }

    // ------------------------------------------------------------ the end

    private void complete(Ritual r) {
        rituals.remove(r);
        removeDisplay(r);
        save();
        Location c = r.loc();
        if (c == null) return;
        World w = c.getWorld();

        List<Player> targets = new ArrayList<>(inside(c));
        // the burst
        Sfx.play(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f, Sfx.Theme.RITUAL);
        Sfx.play(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.8f, Sfx.Theme.RITUAL);
        w.playSound(c, Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.8f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
        for (int i = 0; i < 5; i++) ring(w, c.clone().add(0, i * 0.5, 0), radius() * (0.4 + i * 0.25), 60, LIGHT, PURPLE, 1.8f);
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1.5, 0), 60, 0.6, 1.4, 0.6, 0.15, null, true);

        for (Player p : targets) {
            Darkness next = plugin.rewards().rerollDarkness(p);
            DarknessRoll.play(plugin, p, next, 5, () ->
                    Msg.send(p, "<#9b59ff>Your darkness: <white>" + Msg.esc(next.displayName())
                            + "<gray>. <dark_gray>/limbo darknessinfo for details."));
        }

        String where = c.getBlockX() + ", " + c.getBlockY() + ", " + c.getBlockZ();
        String coords = "<#9b59ff>The reroll ritual has ended at <white>" + where
                + " <gray>(" + Msg.esc(w.getName()) + "). <white>" + targets.size() + "<gray> player"
                + (targets.size() == 1 ? " was" : "s were") + " rerolled.";
        if (plugin.getConfig().getBoolean("ritual.announce-coordinates", true)) {
            Bukkit.broadcast(Msg.parse(Msg.PREFIX + coords));
        } else {
            for (Player p : targets) Msg.send(p, coords);
        }
        Player owner = Bukkit.getPlayer(r.owner);
        if (owner != null && targets.isEmpty()) Msg.send(owner, "<gray>Nobody was inside the circle. The ritual fizzles out.");
    }
}
