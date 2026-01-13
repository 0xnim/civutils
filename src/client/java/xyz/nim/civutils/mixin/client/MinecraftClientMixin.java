package xyz.nim.civutils.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nim.civutils.core.CivutilsMod;
import xyz.nim.civutils.core.event.ScreenOpenEvent;

/**
 * Mixin to capture screen open events.
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen == null) return;

        ScreenOpenEvent event = new ScreenOpenEvent(screen);
        CivutilsMod.INSTANCE.getEventBus().post(event);

        if (event.getCancelled()) {
            ci.cancel();
        }
    }
}
