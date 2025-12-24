package xyz.nim.civutils.mixin.client;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nim.civutils.utils.PlayerTagStyler;

/**
 * Mixin to modify player name tag rendering to apply attribute-based styling.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    /**
     * Modify the name tag text before it's rendered.
     * This intercepts the Text parameter in renderLabelIfPresent and applies our styling.
     */
    @ModifyVariable(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Text civutils$modifyNameTag(Text originalText, PlayerEntityRenderState state) {
        String playerName = state.name;
        if (playerName == null || playerName.isEmpty()) {
            return originalText;
        }

        return PlayerTagStyler.INSTANCE.applyStyle(originalText, playerName);
    }
}
