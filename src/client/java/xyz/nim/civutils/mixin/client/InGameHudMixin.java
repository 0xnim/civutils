package xyz.nim.civutils.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nim.civutils.core.CivutilsMod;
import xyz.nim.civutils.core.event.ActionBarMessageEvent;

/**
 * Mixin to capture actionbar messages and fire ActionBarMessageEvent.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    /**
     * Inject at the start of setOverlayMessage to capture actionbar text.
     */
    @Inject(method = "setOverlayMessage(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
    private void onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        if (message == null) return;

        String rawMessage = message.getString();
        ActionBarMessageEvent event = new ActionBarMessageEvent(message, rawMessage);
        CivutilsMod.INSTANCE.getEventBus().post(event);

        if (event.getCancelled()) {
            ci.cancel();
        }
    }
}
