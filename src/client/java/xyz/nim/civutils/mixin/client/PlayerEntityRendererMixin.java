package xyz.nim.civutils.mixin.client;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nim.civutils.utils.PlayerTagStyler;

/**
 * Mixin to modify player name tag rendering to apply attribute-based styling.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerEntityRendererMixin {

    /**
     * Modify the name tag text before it's rendered.
     * This intercepts the Component parameter in renderNameTag and applies our styling.
     */
    @ModifyVariable(
            method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component civutils$modifyNameTag(Component originalText, PlayerRenderState state) {
        String playerName = state.name;
        if (playerName == null || playerName.isEmpty()) {
            return originalText;
        }

        return PlayerTagStyler.INSTANCE.applyStyle(originalText, playerName);
    }
}
