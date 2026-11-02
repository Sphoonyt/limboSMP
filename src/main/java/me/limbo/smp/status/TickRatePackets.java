package me.limbo.smp.status;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Sends the vanilla "ticking state" packet to ONE player, which changes that client's tick rate
 * (same mechanism as /tick rate, but only for them). Done via reflection on Paper's Mojang-mapped
 * server so there's no extra dependency. If anything fails to resolve, send() returns false and the
 * caller falls back to attribute-based slowing.
 */
final class TickRatePackets {
    private final Logger log;
    private boolean failed;
    private Constructor<?> ctor;
    private Method getHandle;
    private Field connectionField;
    private Method sendMethod;

    TickRatePackets(Logger log) { this.log = log; }

    boolean isAvailable() { return !failed; }

    boolean send(Player p, float tickRate) {
        if (failed) return false;
        try {
            if (ctor == null) init(p);
            Object handle = getHandle.invoke(p);
            Object connection = connectionField.get(handle);
            if (connection == null) return false;
            Object packet = ctor.newInstance(tickRate, false);
            sendMethod.invoke(connection, packet);
            return true;
        } catch (Throwable t) {
            if (ctor == null) { // init failure is permanent
                failed = true;
                log.warning("Per-player tick rate packet unavailable, using attribute fallback: " + t);
            }
            return false;
        }
    }

    private void init(Player p) throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> tickingState = Class.forName("net.minecraft.network.protocol.game.ClientboundTickingStatePacket");
        Constructor<?> c = tickingState.getConstructor(float.class, boolean.class);
        Method gh = p.getClass().getMethod("getHandle");
        Object handle = gh.invoke(p);
        Field cf = handle.getClass().getField("connection");
        Object connection = cf.get(handle);
        Method sm = connection.getClass().getMethod("send", packetClass);
        this.getHandle = gh;
        this.connectionField = cf;
        this.sendMethod = sm;
        this.ctor = c; // set last: marks init as complete
    }
}
