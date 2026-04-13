package com.hluhovskyi.zero.sync

import com.hluhovskyi.zero.common.Id
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object IdKnownSerializer : KSerializer<Id.Known> {
    override val descriptor = PrimitiveSerialDescriptor("Id.Known", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Id.Known) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): Id.Known = Id.Known(decoder.decodeString())
}
