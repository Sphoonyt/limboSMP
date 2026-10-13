package me.limbo.smp;

import me.limbo.smp.command.LimboCommand;
import me.limbo.smp.command.TrustCommand;
import me.limbo.smp.darkness.DarknessRegistry;
import me.limbo.smp.darkness.EntropyDarkness;
import me.limbo.smp.darkness.IgnitionDarkness;
import me.limbo.smp.darkness.InertiaDarkness;
import me.limbo.smp.darkness.SerenityDarkness;
import me.limbo.smp.data.PlayerDataManager;
import me.limbo.smp.data.PlayerListener;
import me.limbo.smp.input.InputDispatcher;
import me.limbo.smp.input.InputListener;
import me.limbo.smp.item.LimboItems;
import me.limbo.smp.passive.PassiveManager;
import me.limbo.smp.rift.RiftCrafting;
import me.limbo.smp.rift.RiftListener;
import me.limbo.smp.rift.RiftManager;
import me.limbo.smp.siphon.SiphonListener;
import me.limbo.smp.trust.TrustManager;
import org.bukkit.Bukkit;
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
        // 2 slots intentionally left open (DarknessRegistry.MAX_SLOTS = 6)

        items = new LimboItems(this);
        data = new PlayerDataManager(this);
        trust = new TrustManager(this);
        dispatcher = new InputDispatcher(this);
        passives = new PassiveManager(this);
        rifts = new RiftManager(this);
        crafting = new RiftCrafting(this);

        data.load();
        trust.load();
        rifts.load();
        crafting.reload();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new InputListener(this), this);
        pm.registerEvents(new RiftListener(this), this);
        pm.registerEvents(new SiphonListener(this), this);

        TrustCommand trustCmd = new TrustCommand(this);
        LimboCommand limboCmd = new LimboCommand(this, trustCmd);
        getCommand("limbo").setExecutor(limboCmd);
        getCommand("limbo").setTabCompleter(limboCmd);
        getCommand("trust").setExecutor(trustCmd);
        getCommand("trust").setTabCompleter(trustCmd);

        passives.start();
        rifts.startAmbience();
        Bukkit.getScheduler().runTaskTimer(this, () -> data.saveIfDirty(), 20L * 60, 20L * 60);

        // players already online (e.g. /reload)
        Bukkit.getOnlinePlayers().forEach(data::assignIfMissing);
        getLogger().info("LimboSMP enabled with " + registry.all().size() + " darknesses ("
                + registry.freeSlots() + " slots free).");
    }

    @Override
    public void onDisable() {
        if (passives != null) passives.shutdown();
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
}
