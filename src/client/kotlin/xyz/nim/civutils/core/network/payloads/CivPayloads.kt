package xyz.nim.civutils.core.network.payloads

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Base payload for CivUtils plugin channel communication.
 * All payloads use JSON strings for simplicity and extensibility.
 */

/**
 * Handshake payload for capability negotiation.
 * Channel: civ:handshake
 */
data class CivHandshakePayload(
    val jsonData: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<CivHandshakePayload> = TYPE

    companion object {
        val ID: Identifier = Identifier.fromNamespaceAndPath("civ", "handshake")
        val TYPE: CustomPacketPayload.Type<CivHandshakePayload> = CustomPacketPayload.Type(ID)

        val CODEC: StreamCodec<FriendlyByteBuf, CivHandshakePayload> = StreamCodec.of(
            { buf, payload -> buf.writeUtf(payload.jsonData) },
            { buf -> CivHandshakePayload(buf.readUtf()) }
        )
    }
}

/**
 * Class XP data payload.
 * Channel: civ:class_xp
 */
data class ClassXpPayload(
    val jsonData: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ClassXpPayload> = TYPE

    companion object {
        val ID: Identifier = Identifier.fromNamespaceAndPath("civ", "class_xp")
        val TYPE: CustomPacketPayload.Type<ClassXpPayload> = CustomPacketPayload.Type(ID)

        val CODEC: StreamCodec<FriendlyByteBuf, ClassXpPayload> = StreamCodec.of(
            { buf, payload -> buf.writeUtf(payload.jsonData) },
            { buf -> ClassXpPayload(buf.readUtf()) }
        )
    }
}
