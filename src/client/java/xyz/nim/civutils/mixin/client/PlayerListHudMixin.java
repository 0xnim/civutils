package xyz.nim.civutils.mixin.client;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nim.civutils.features.players.PlayerTagFeature;
import xyz.nim.civutils.utils.PlayerTagStyler;

/**
 * Mixin to modify player names in the tab list (player list HUD).
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    /**
     * Modify the player name displayed in the tab list.
     */
    @Inject(
            method = "getPlayerName",
            at = @At("RETURN"),
            cancellable = true
    )
    private void civutils$modifyTabListName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        PlayerTagFeature feature = PlayerTagFeature.Companion.getInstance();
        if (feature == null || !feature.getEnableTabList().getValue()) {
            return;
        }

        String playerName = entry.getProfile().getName();
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        if (PlayerTagStyler.INSTANCE.isTagged(playerName)) {
            Text original = cir.getReturnValue();
            Text styled = PlayerTagStyler.INSTANCE.applyStyle(original, playerName);
            cir.setReturnValue(styled);
        }
    }
}
