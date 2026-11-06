package me.limbo.smp;

import me.limbo.smp.command.LimboCommand;
import me.limbo.smp.command.TrustCommand;
import me.limbo.smp.darkness.Darkness;
import me.limbo.smp.darkness.ChimeraDarkness;
import me.limbo.smp.darkness.DarknessRegistry;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.darkness.ReflectionDarkness;
import me.limbo.smp.darkness.SerenityDarkness;
import me.limbo.smp.darkness.SingularityDarkness;
import me.limbo.smp.data.PlayerDataManager;
import me.limbo.smp.data.PlayerListener;
import me.limbo.smp.event.EventManager;
import me.limbo.smp.input.InputDispatcher;
import me.limbo.smp.input.InputListener;
import me.limbo.smp.input.CooldownHud;
import me.limbo.smp.item.CursedItems;
import me.limbo.smp.item.ItemGlowListener;
import me.limbo.smp.item.LimboItems;
import me.limbo.smp.passive.PassiveManager;
import me.limbo.smp.progress.Unlocks;
import me.limbo.smp.reward.RewardManager;
import me.limbo.smp.ritual.DevotionRitual;
import me.limbo.smp.rift.RiftCrafting;
import me.limbo.smp.rift.RiftListener;
import me.limbo.smp.rift.RiftManager;
import me.limbo.smp.rift.RiftMenu;
import me.limbo.smp.rift.RiftTeleport;
import me.limbo.smp.siphon.SiphonListener;
import me.limbo.smp.soul.SoulListener;
import me.limbo.smp.soul.SoulMenu;
import me.limbo.smp.soul.SoulTracker;
import me.limbo.smp.status.FatigueManager;
import me.limbo.smp.status.StatusManager;
import me.limbo.smp.trust.TrustManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LimboPlugin extends JavaPlugin {
    private static LimboPlugin instance;

    private DarknessRegistry registry;
    private LimboItems items;
    private PlayerDataManager data;
    private TrustManager trust;
    private InputDispatcher dispatcher;
    private PassiveManager passives;
    private RiftManager rifts;
    private RiftCrafting crafting;
    private StatusManager status;
    private final FatigueManager fatigue = new FatigueManager();
    private RiftMenu menu;
    private EventManager events;
    private Unlocks unlocks;
    private CursedItems cursed;
    private CooldownHud cooldownHud;
    private RewardManager rewards;
    private DevotionRitual ritual;
    private RiftTeleport riftTeleport;
    private SoulMenu soulMenu;
    private SoulTracker soulTracker;
    private final Set<UUID> debug = ConcurrentHashMap.newKeySet();

    public static LimboPlugin get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        registry = new DarknessRegistry();
        registry.register(new IgnitionDarkness());
        registry.register(new SerenityDarkness());
        registry.register(new EntropyDarkness());
        registry.register(new InertiaDarkness());
        registry.register(new ReflectionDarkness());
        registry.register(new SingularityDarkness());
        registry.register(new ChimeraDarkness());

        items = new LimboItems(this);
        items.loadCoreSkin();
        unlocks = new Unlocks(this);
        cursed = new CursedItems(this);
        data = new PlayerDataManager(this);
        trust = new TrustManager(this);
        dispatcher = new InputDispatcher(this);
        cooldownHud = new CooldownHud(this);
        passives = new PassiveManager(this);
        rifts = new RiftManager(this);
        crafting = new RiftCrafting(this);
        status = new StatusManager(this);
        menu = new RiftMenu(this);
        rewards = new RewardManager(this);
        ritual = new DevotionRitual(this);
        events = new EventManager(this);
        riftTeleport = new RiftTeleport(this);
        soulMenu = new SoulMenu(this);
        soulTracker = new SoulTracker(this);

        unlocks.load();
        cursed.load();
        data.load();
        trust.load();
        rifts.load();
        crafting.reload();
        events.load();
        if (cursed.enabled() && cursed.active().isEmpty()) cursed.roll(false);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new InputListener(this), this);
        pm.registerEvents(new RiftListener(this), this);
        pm.registerEvents(menu, this);
        pm.registerEvents(events, this);
        pm.registerEvents(events.progression(), this);
        pm.registerEvents(events.serverStart(), this);
        pm.registerEvents(events.progression().pedestals(), this);
        pm.registerEvents(new me.limbo.smp.darkness.inertia.WindChargeCooldown(this), this);
        pm.registerEvents(unlocks, this);
        pm.registerEvents(cursed, this);
        pm.registerEvents(rewards, this);
        pm.registerEvents(ritual, this);
        pm.registerEvents(new SoulListener(this), this);
        pm.registerEvents(soulMenu, this);
        pm.registerEvents(soulTracker, this);
        pm.registerEvents(new ItemGlowListener(this), this);
        pm.registerEvents(new SiphonListener(this), this);
        pm.registerEvents(status, this);
        for (Darkness d : registry.all()) {
            for (var l : d.listeners()) pm.registerEvents(l, this);
        }

        TrustCommand trustCmd = new TrustCommand(this);
        LimboCommand limboCmd = new LimboCommand(this, trustCmd);
        getCommand("limbo").setExecutor(limboCmd);
        getCommand("limbo").setTabCompleter(limboCmd);
        getCommand("trust").setExecutor(trustCmd);
        getCommand("trust").setTabCompleter(trustCmd);

        passives.start();
        rifts.start();
        events.start();
        cursed.start();
        cooldownHud.start();
        ritual.load();
        ritual.start();
        Bukkit.getScheduler().runTaskTimer(this, () -> data.saveIfDirty(), 20L * 60, 20L * 60);

        // players already online (e.g. /reload)
        Bukkit.getOnlinePlayers().forEach(data::assignIfMissing);
        getLogger().info("LimboSMP enabled with " + registry.all().size() + " darknesses ("
                + registry.freeSlots() + " slots free).");
    }

    @Override
    public void onDisable() {
        if (menu != null) menu.shutdown();
        if (events != null) events.shutdown();
        if (cursed != null) cursed.shutdown();
        if (cooldownHud != null) cooldownHud.shutdown();
        if (ritual != null) ritual.shutdown();
        if (riftTeleport != null) riftTeleport.shutdown();
        if (soulTracker != null) soulTracker.shutdown();
        if (passives != null) passives.shutdown();
        if (registry != null) registry.all().forEach(Darkness::shutdown);
        if (status != null) status.shutdown();
        if (rifts != null) rifts.shutdown();
        if (data != null) data.save();
        if (trust != null) trust.save();
        if (rifts != null) rifts.save();
    }

    public void reloadAll() {
        reloadConfig();
        data.save();
        data.load();
        trust.load();
        rifts.load();
        crafting.reload();
        unlocks.load();
        cursed.load();
    }

    /**
     * True if the player's passives are currently running for this darkness: a null totem in the main inventory.
     * (Cursed items only give the first channel, never passives.)
     */
    public boolean passivesActive(Player p, String darknessId) {
        if (p.isDead() || !darknessId.equals(data.getDarknessId(p.getUniqueId()))) return false;
        if (status.isSilenced(p.getUniqueId())) return false; // Silencing Maw: the whole darkness is off
        if (!items.hasOrbInInventory(p)) return false; // cursed items never give passives
        // darkness fatigue: passives only work while the totem is actually held
        return !fatigue.isFatigued(p.getUniqueId()) || items.isHoldingOrb(p);
    }

    /**
     * True if this specific flat passive is running for the player: either it's theirs (their darkness' passives are
     * active) or a Chimera is currently copying it with Copycat. Second flat passives check this version.
     */
    public boolean passiveEnabled(Player p, String darknessId, String passiveId) {
        if (passivesActive(p, darknessId)) return true;
        return me.limbo.smp.darkness.chimera.CopycatPassive.isCopying(p.getUniqueId(), darknessId, passiveId)
                && passivesActive(p, ChimeraDarkness.ID);
    }

    /** True if the player's flux passive is running (totem in main hand or offhand). */
    public boolean fluxActive(Player p, String darknessId) {
        if (!passivesActive(p, darknessId)) return false;
        return items.isHoldingOrb(p);
    }

    public boolean toggleDebug(UUID id) {
        if (!debug.remove(id)) { debug.add(id); return true; }
        return false;
    }

    public boolean isDebug(UUID id) { return debug.contains(id); }

    public DarknessRegistry registry() { return registry; }
    public LimboItems items() { return items; }
    public PlayerDataManager data() { return data; }
    public TrustManager trust() { return trust; }
    public InputDispatcher dispatcher() { return dispatcher; }
    public PassiveManager passives() { return passives; }
    public RiftManager rifts() { return rifts; }
    public RiftCrafting crafting() { return crafting; }
    public StatusManager status() { return status; }
    public FatigueManager fatigue() { return fatigue; }
    public RiftMenu menu() { return menu; }
    public EventManager events() { return events; }
    public Unlocks unlocks() { return unlocks; }
    public CursedItems cursed() { return cursed; }
    public CooldownHud cooldownHud() { return cooldownHud; }
    public RewardManager rewards() { return rewards; }
    public DevotionRitual ritual() { return ritual; }
    public RiftTeleport riftTeleport() { return riftTeleport; }
    public SoulMenu soulMenu() { return soulMenu; }
    public SoulTracker soulTracker() { return soulTracker; }
}
