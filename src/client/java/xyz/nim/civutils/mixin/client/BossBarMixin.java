package xyz.nim.civutils.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nim.civutils.core.CivutilsMod;
import xyz.nim.civutils.core.event.BossBarAction;
import xyz.nim.civutils.core.event.BossBarEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Mixin to capture BossBar updates and fire BossBarEvent.
 * Also handles hiding specific boss bars from rendering.
 */
@Mixin(BossHealthOverlay.class)
public class BossBarMixin {

    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    // TODO: Hide vanilla bar feature disabled - causes network protocol errors
    // The approach of removing/restoring bars from the events map during render
    // seems to interfere with Minecraft's boss bar handling somehow.
    // Need to investigate a different approach (e.g., mixin into drawBar method)

    @Inject(method = "update", at = @At("TAIL"))
    private void onBossBarUpdate(ClientboundBossEventPacket packet, CallbackInfo ci) {
        // After the packet is processed, check what boss events we have
        // and fire appropriate events
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            @Override
            public void add(UUID uuid, net.minecraft.network.chat.Component name, float progress,
                           BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay,
                           boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
                CivutilsMod.INSTANCE.getEventBus().post(new BossBarEvent(
                    uuid,
                    name.getString(),
                    progress,
                    color,
                    overlay,
                    BossBarAction.ADD
                ));
            }

            @Override
            public void remove(UUID uuid) {
                CivutilsMod.INSTANCE.getEventBus().post(new BossBarEvent(
                    uuid,
                    "",
                    0f,
                    BossEvent.BossBarColor.WHITE,
                    BossEvent.BossBarOverlay.PROGRESS,
                    BossBarAction.REMOVE
                ));
            }

            @Override
            public void updateProgress(UUID uuid, float progress) {
                LerpingBossEvent event = events.get(uuid);
                if (event != null) {
                    CivutilsMod.INSTANCE.getEventBus().post(new BossBarEvent(
                        uuid,
                        event.getName().getString(),
                        progress,
                        event.getColor(),
                        event.getOverlay(),
                        BossBarAction.UPDATE
                    ));
                }
            }

            @Override
            public void updateName(UUID uuid, net.minecraft.network.chat.Component name) {
                LerpingBossEvent event = events.get(uuid);
                if (event != null) {
                    CivutilsMod.INSTANCE.getEventBus().post(new BossBarEvent(
                        uuid,
                        name.getString(),
                        event.getProgress(),
                        event.getColor(),
                        event.getOverlay(),
                        BossBarAction.UPDATE
                    ));
                }
            }

            @Override
            public void updateStyle(UUID uuid, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
                LerpingBossEvent event = events.get(uuid);
                if (event != null) {
                    CivutilsMod.INSTANCE.getEventBus().post(new BossBarEvent(
                        uuid,
                        event.getName().getString(),
                        event.getProgress(),
                        color,
                        overlay,
                        BossBarAction.UPDATE
                    ));
                }
            }

            @Override
            public void updateProperties(UUID uuid, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
                // Don't need to fire event for property changes
            }
        });
    }
}
