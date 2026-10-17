package me.limbo.smp.darkness;

import org.bukkit.Color;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A darkness type: 2 flat passives, 1 flux passive, 2 channels. */
public abstract class Darkness {
    public static final int MAX_PASSIVES = 2;
    public static final int MAX_CHANNELS = 2;

    private final String id;
    private final String displayName;
    private final String description;
    private final Color color;

    private final List<Passive> passives = new ArrayList<>();
    private FluxPassive fluxPassive;
    private final List<Channel> channels = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    protected Darkness(String id, String displayName, String description, Color color) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    protected final void addPassive(Passive p) {
        if (passives.size() >= MAX_PASSIVES) throw new IllegalStateException(id + " already has " + MAX_PASSIVES + " passives");
        passives.add(p);
        if (p instanceof Listener l) listeners.add(l);
    }

    protected final void setFluxPassive(FluxPassive p) {
        this.fluxPassive = p;
        if (p instanceof Listener l) listeners.add(l);
    }

    protected final void addChannel(Channel c) {
        if (channels.size() >= MAX_CHANNELS) throw new IllegalStateException(id + " already has " + MAX_CHANNELS + " channels");
        if (channelFor(c.input()) != null) throw new IllegalStateException(id + " already has a channel on " + c.input());
        channels.add(c);
        if (c instanceof Listener l) listeners.add(l);
    }

    public @Nullable Channel channelFor(ChannelInput input) {
        for (Channel c : channels) if (c.input() == input) return c;
        return null;
    }

    /** Called when the plugin disables. */
    public final void shutdown() {
        passives.forEach(Passive::onShutdown);
        if (fluxPassive != null) fluxPassive.onShutdown();
        channels.forEach(Channel::onShutdown);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    /** Accent colour used for all visuals (mixed with void-black). */
    public Color color() { return color; }
    public List<Passive> passives() { return Collections.unmodifiableList(passives); }
    public @Nullable FluxPassive fluxPassive() { return fluxPassive; }
    public List<Channel> channels() { return Collections.unmodifiableList(channels); }
    /** Passives/channels that implement Listener; registered by the plugin. */
    public List<Listener> listeners() { return Collections.unmodifiableList(listeners); }
}
