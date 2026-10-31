package me.limbo.smp.darkness;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a channel needs when it fires.
 * @param target  the entity that was hit (only for H), otherwise null
 * @param orb     the orb stack that triggered the channel
 * @param offhand true if the orb was in the offhand
 * @param cursed  true if a cursed item was used instead of the null totem (channels with their own cooldown
 *                tracking should double it, the dispatcher already doubles the normal one)
 */
public record ChannelContext(Player player, Darkness darkness, ChannelInput input,
                             @Nullable LivingEntity target, ItemStack orb, boolean offhand, boolean cursed) {
}
