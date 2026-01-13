package xyz.nim.civutils.mixin.client;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nim.civutils.features.players.PlayerTagFeature;
import xyz.nim.civutils.utils.PlayerTagStyler;

/**
 * Mixin to modify player names in the tab list (player list HUD).
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    /**
     * Modify the player name displayed in the tab list.
     */
    @Inject(
            method = "getNameForDisplay",
            at = @At("RETURN"),
            cancellable = true
    )
    private void civutils$modifyTabListName(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        PlayerTagFeature feature = PlayerTagFeature.Companion.getInstance();
        if (feature == null || !feature.getEnabled() || !feature.getEnableTabList().getValue()) {
            return;
        }

        String playerName = entry.getProfile().getName();
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        if (PlayerTagStyler.INSTANCE.isTagged(playerName)) {
            Component original = cir.getReturnValue();
            Component styled = PlayerTagStyler.INSTANCE.applyStyle(original, playerName);
            cir.setReturnValue(styled);
        }
    }
}
