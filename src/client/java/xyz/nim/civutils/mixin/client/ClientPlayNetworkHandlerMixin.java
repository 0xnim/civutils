package xyz.nim.civutils.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
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
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        var handler = client.player.currentScreenHandler;
        if (handler != null && handler.syncId == packet.getSyncId()) {
            ItemStack stack = packet.getStack();
            int slot = packet.getSlot();
            CivutilsMod.INSTANCE.getEventBus().post(new ContainerUpdateEvent(handler, slot, stack));
        }
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void onInventoryUpdate(InventoryS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        var handler = client.player.currentScreenHandler;
        if (handler != null) {
            // Get the stacks from packet using the correct field access
            List<ItemStack> stacks = packet.contents();
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = stacks.get(i);
                CivutilsMod.INSTANCE.getEventBus().post(new ContainerUpdateEvent(handler, i, stack));
            }
        }
    }
}
