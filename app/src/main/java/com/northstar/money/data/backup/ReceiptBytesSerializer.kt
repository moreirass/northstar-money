package com.northstar.money.data.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.util.Base64

/** Compact new backups while accepting the numeric arrays used by older versions. */
object ReceiptBytesSerializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("ReceiptBytes", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }
    override fun deserialize(decoder: Decoder): ByteArray {
        val value = (decoder as JsonDecoder).decodeJsonElement()
        return when (value) {
            is JsonPrimitive -> {
                require(value.content.length <= 1_333_336) { "Receipt is too large" }
                Base64.getDecoder().decode(value.content).also { require(it.size <= 1_000_000) }
            }
            is JsonArray -> {
                require(value.size <= 1_000_000) { "Receipt is too large" }
                ByteArray(value.size) { index -> value[index].jsonPrimitive.int.let { require(it in -128..127); it.toByte() } }
            }
            else -> throw IllegalArgumentException("Invalid receipt content")
        }
    }
}
