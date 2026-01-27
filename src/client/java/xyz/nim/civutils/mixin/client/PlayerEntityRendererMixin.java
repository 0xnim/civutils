package xyz.nim.civutils.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nim.civutils.utils.PlayerTagStyler;

/**
 * Mixin to modify player name tag rendering to apply attribute-based styling.
 */
@Mixin(EntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    /**
     * Modify the name tag text before it's rendered.
     * This intercepts the getNameTag return value and applies our styling for players.
     */
    @Inject(
            method = "getNameTag",
            at = @At("RETURN"),
            cancellable = true
    )
    private void civutils$modifyNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        // Only modify for players
        if (!(entity instanceof Player player)) {
            return;
        }

        String playerName = player.getGameProfile().name();
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        Component original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        Component styled = PlayerTagStyler.INSTANCE.applyStyle(original, playerName);
        cir.setReturnValue(styled);
    }
}
