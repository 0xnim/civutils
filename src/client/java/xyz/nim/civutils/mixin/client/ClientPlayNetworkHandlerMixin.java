package xyz.nim.civutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nim.civutils.core.CivutilsMod;
import xyz.nim.civutils.core.event.ContainerUpdateEvent;

import java.util.List;

/**
 * Mixin to capture container slot updates.
 */
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        var handler = client.player.containerMenu;
        if (handler != null && handler.containerId == packet.getContainerId()) {
            ItemStack stack = packet.getItem();
            int slot = packet.getSlot();
            CivutilsMod.INSTANCE.getEventBus().post(new ContainerUpdateEvent(handler, slot, stack));
        }
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void onInventoryUpdate(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        var handler = client.player.containerMenu;
        if (handler != null) {
            // Get the stacks from packet using the correct field access
            List<ItemStack> stacks = packet.items();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                CivutilsMod.INSTANCE.getEventBus().post(new ContainerUpdateEvent(handler, i, stack));
            }
        }
    }
}
