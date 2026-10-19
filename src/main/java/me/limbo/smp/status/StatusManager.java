package me.limbo.smp.status;

import me.limbo.smp.LimboPlugin;
import me.limbo.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared status effects used by abilities: stun, time slow, ceasefire. */
public final class StatusManager implements Listener {
    private final LimboPlugin plugin;
    private final NamespacedKey stunMove, stunJump, slowMove, slowJump, slowGravity, slowAttack, slowBreak;
    private final TickRatePackets packets;
    private final NamespacedKey kbImmune;
    private final NamespacedKey groundJump;

    private final Map<UUID, Long> stunUntil = new HashMap<>();
    private final Map<UUID, LivingEntity> stunned = new HashMap<>();
    private final Map<UUID, BukkitTask> stunTasks = new HashMap<>();

    private final Map<UUID, Long> slowUntil = new HashMap<>();
    private final Map<UUID, LivingEntity> slowed = new HashMap<>();
    private final Map<UUID, BukkitTask> slowTasks = new HashMap<>();

    private final Map<UUID, Double> slowScale = new HashMap<>();
    private final Set<UUID> packetSlowed = new HashSet<>();

    private final Map<UUID, Long> groundUntil = new HashMap<>();
    private final Map<UUID, LivingEntity> groundEntities = new HashMap<>();
    private final Map<UUID, BukkitTask> groundTasks = new HashMap<>();

    private final Map<UUID, Long> kbUntil = new HashMap<>();
    private final Map<UUID, LivingEntity> kbEntities = new HashMap<>();
    private final Map<UUID, BukkitTask> kbTasks = new HashMap<>();

    private final Map<UUID, Long> ceasefireUntil = new HashMap<>();
    private final Map<UUID, Long> shieldUntil = new HashMap<>();

    public StatusManager(LimboPlugin plugin) {
        this.plugin = plugin;
        stunMove = new NamespacedKey(plugin, "stun_move");
        stunJump = new NamespacedKey(plugin, "stun_jump");
        slowMove = new NamespacedKey(plugin, "timeslow_move");
        slowJump = new NamespacedKey(plugin, "timeslow_jump");
        slowGravity = new NamespacedKey(plugin, "timeslow_gravity");
        slowAttack = new NamespacedKey(plugin, "timeslow_attack");
        slowBreak = new NamespacedKey(plugin, "timeslow_break");
        packets = new TickRatePackets(plugin.getLogger());
        kbImmune = new NamespacedKey(plugin, "kb_immunity");
        groundJump = new NamespacedKey(plugin, "ground_jump");
    }

    // ---------- attribute helpers ----------

    private void setMod(LivingEntity e, Attribute attr, NamespacedKey key, double amount) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        clearMod(e, attr, key);
        inst.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
    }

    private void clearMod(LivingEntity e, Attribute attr, NamespacedKey key) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(key)) inst.removeModifier(m);
        }
    }

    // ---------- stun ----------

    public void stun(LivingEntity e, int ticks) {
        UUID id = e.getUniqueId();
        BukkitTask old = stunTasks.remove(id);
        if (old != null) old.cancel();
        setMod(e, Attribute.MOVEMENT_SPEED, stunMove, -1.0);
        setMod(e, Attribute.JUMP_STRENGTH, stunJump, -1.0);
        if (e instanceof Mob m) m.setAware(false);
        stunUntil.put(id, System.currentTimeMillis() + ticks * 50L);
        stunned.put(id, e);
        stunTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> endStun(id), ticks));
        if (e instanceof Player p) p.sendActionBar(Msg.parse("<dark_red>Stunned"));
    }

    private void endStun(UUID id) {
        BukkitTask t = stunTasks.remove(id);
        if (t != null) t.cancel();
        stunUntil.remove(id);
        LivingEntity e = stunned.remove(id);
        if (e == null) return;
        clearMod(e, Attribute.MOVEMENT_SPEED, stunMove);
        clearMod(e, Attribute.JUMP_STRENGTH, stunJump);
        if (e instanceof Mob m) m.setAware(true);
    }

    public boolean isStunned(UUID id) {
        Long u = stunUntil.get(id);
        return u != null && u > System.currentTimeMillis();
    }

    // ---------- time slow (Temporal Repose) ----------

    /**
     * Emulates a tick rate decrease for ONE entity.
     * Players: their client's tick rate is lowered via packet (movement, jumping, falling, animations
     * all run slower for them only). If the packet can't be sent (or mode=attributes), movement/jump/gravity
     * are scaled with attribute modifiers instead. Either way attack speed and block-break speed are scaled
     * server-side (those run on server ticks), and projectiles they shoot are slowed.
     * Mobs always use the attribute path.
     */
    public void slowTime(LivingEntity e, int ticks) {
        UUID id = e.getUniqueId();
        BukkitTask old = slowTasks.remove(id);
        if (old != null) old.cancel();

        double s = Math.max(0.2, Math.min(1.0, plugin.getConfig().getDouble("temporal-repose.time-scale", 0.6)));
        String mode = plugin.getConfig().getString("temporal-repose.mode", "auto");

        boolean viaPacket = false;
        if (e instanceof Player p && !"attributes".equalsIgnoreCase(mode)) {
            viaPacket = packets.send(p, (float) (20.0 * s));
        }
        if (viaPacket) packetSlowed.add(id); else packetSlowed.remove(id);

        if (!viaPacket) {
            setMod(e, Attribute.MOVEMENT_SPEED, slowMove, s - 1.0);
            setMod(e, Attribute.JUMP_STRENGTH, slowJump, s - 1.0);
            setMod(e, Attribute.GRAVITY, slowGravity, s * s - 1.0); // keeps jump height the same, just slower
        }
        setMod(e, Attribute.ATTACK_SPEED, slowAttack, s - 1.0);
        setMod(e, Attribute.BLOCK_BREAK_SPEED, slowBreak, s - 1.0);

        slowScale.put(id, s);
        slowUntil.put(id, System.currentTimeMillis() + ticks * 50L);
        slowed.put(id, e);
        slowTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> endSlow(id), ticks));
    }

    private void endSlow(UUID id) {
        BukkitTask t = slowTasks.remove(id);
        if (t != null) t.cancel();
        slowUntil.remove(id);
        slowScale.remove(id);
        LivingEntity e = slowed.remove(id);
        boolean wasPacket = packetSlowed.remove(id);
        if (e == null) return;
        clearMod(e, Attribute.MOVEMENT_SPEED, slowMove);
        clearMod(e, Attribute.JUMP_STRENGTH, slowJump);
        clearMod(e, Attribute.GRAVITY, slowGravity);
        clearMod(e, Attribute.ATTACK_SPEED, slowAttack);
        clearMod(e, Attribute.BLOCK_BREAK_SPEED, slowBreak);
        if (wasPacket && e instanceof Player p && p.isOnline()) packets.send(p, 20.0f);
    }

    public boolean isTimeSlowed(UUID id) {
        Long u = slowUntil.get(id);
        return u != null && u > System.currentTimeMillis();
    }

    // ---------- ceasefire ----------

    /**
     * @param protect true for the caster only: players also can't hit this player.
     * Everyone with ceasefire can't hit other players.
     */
    public void setCeasefire(UUID id, int ticks, boolean protect) {
        long until = System.currentTimeMillis() + ticks * 50L;
        ceasefireUntil.put(id, until);
        if (protect) shieldUntil.put(id, until);
    }

    /** Can't hit other players. */
    public boolean isCeasefire(UUID id) {
        Long u = ceasefireUntil.get(id);
        if (u == null) return false;
        if (u <= System.currentTimeMillis()) { ceasefireUntil.remove(id); return false; }
        return true;
    }

    /** Can't be hit by other players (Ceasefire caster). */
    public boolean isProtected(UUID id) {
        Long u = shieldUntil.get(id);
        if (u == null) return false;
        if (u <= System.currentTimeMillis()) { shieldUntil.remove(id); return false; }
        return true;
    }

    // ---------- grounded (Axiom Dagger): can't jump, can't throw wind charges ----------

    public void ground(LivingEntity e, int ticks) {
        UUID id = e.getUniqueId();
        BukkitTask old = groundTasks.remove(id);
        if (old != null) old.cancel();
        setMod(e, Attribute.JUMP_STRENGTH, groundJump, -1.0);
        groundUntil.put(id, System.currentTimeMillis() + ticks * 50L);
        groundEntities.put(id, e);
        groundTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> endGround(id), ticks));
    }

    private void endGround(UUID id) {
        BukkitTask t = groundTasks.remove(id);
        if (t != null) t.cancel();
        groundUntil.remove(id);
        LivingEntity e = groundEntities.remove(id);
        if (e != null) clearMod(e, Attribute.JUMP_STRENGTH, groundJump);
    }

    public boolean isGrounded(UUID id) {
        Long u = groundUntil.get(id);
        return u != null && u > System.currentTimeMillis();
    }

    // ---------- knockback immunity ----------

    /**
     * Complete knockback immunity (knockback resistance 1.0) for the given ticks. A shorter request
     * never cuts a longer immunity that's already running. Works for the knockback applied in the same
     * tick as a hit, so it can be granted from inside a damage event.
     */
    public void knockbackImmunity(LivingEntity e, int ticks) {
        UUID id = e.getUniqueId();
        long until = System.currentTimeMillis() + ticks * 50L;
        Long cur = kbUntil.get(id);
        if (cur != null && cur >= until) return;
        AttributeInstance inst = e.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (inst == null) return;
        BukkitTask old = kbTasks.remove(id);
        if (old != null) old.cancel();
        clearMod(e, Attribute.KNOCKBACK_RESISTANCE, kbImmune);
        inst.addTransientModifier(new AttributeModifier(kbImmune, 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        kbUntil.put(id, until);
        kbEntities.put(id, e);
        kbTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> endKb(id), ticks));
    }

    private void endKb(UUID id) {
        BukkitTask t = kbTasks.remove(id);
        if (t != null) t.cancel();
        kbUntil.remove(id);
        LivingEntity e = kbEntities.remove(id);
        if (e != null) clearMod(e, Attribute.KNOCKBACK_RESISTANCE, kbImmune);
    }

    public boolean isKnockbackImmune(UUID id) {
        Long u = kbUntil.get(id);
        return u != null && u > System.currentTimeMillis();
    }

    // ---------- cleanup ----------

    public void shutdown() {
        new ArrayList<>(stunned.keySet()).forEach(this::endStun);
        new ArrayList<>(slowed.keySet()).forEach(this::endSlow);
        new ArrayList<>(kbEntities.keySet()).forEach(this::endKb);
        new ArrayList<>(groundEntities.keySet()).forEach(this::endGround);
        ceasefireUntil.clear();
        shieldUntil.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        endStun(e.getPlayer().getUniqueId());
        endSlow(e.getPlayer().getUniqueId());
        endKb(e.getPlayer().getUniqueId());
        endGround(e.getPlayer().getUniqueId());
        ceasefireUntil.remove(e.getPlayer().getUniqueId());
        shieldUntil.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        endStun(e.getEntity().getUniqueId());
        endSlow(e.getEntity().getUniqueId());
        endKb(e.getEntity().getUniqueId());
        endGround(e.getEntity().getUniqueId());
        ceasefireUntil.remove(e.getEntity().getUniqueId());
        shieldUntil.remove(e.getEntity().getUniqueId());
    }

    // ---------- enforcement ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;

        if (e.getDamager() instanceof LivingEntity le && isStunned(le.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (attacker == null) return;
        if (isStunned(attacker.getUniqueId())) { e.setCancelled(true); return; }
        if (e.getEntity() instanceof Player v && !v.getUniqueId().equals(attacker.getUniqueId())
                && (isCeasefire(attacker.getUniqueId()) || isProtected(v.getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    /** Time-slowed shooters fire slower projectiles. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof LivingEntity shooter)) return;
        if (e.getEntity() instanceof WindCharge && isGrounded(shooter.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        Double scale = slowScale.get(shooter.getUniqueId());
        if (scale == null || !isTimeSlowed(shooter.getUniqueId())) return;
        e.getEntity().setVelocity(e.getEntity().getVelocity().multiply(scale));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (isStunned(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (isStunned(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isStunned(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isStunned(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
}
