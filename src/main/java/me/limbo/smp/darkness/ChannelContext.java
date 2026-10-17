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
 */
public record ChannelContext(Player player, Darkness darkness, ChannelInput input,
                             @Nullable LivingEntity target, ItemStack orb, boolean offhand) {
}
