package net.spross.kern.store

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Persisted date shape: ISO-8601 UTC (e.g. "2026-07-01T12:00:00Z").
 * [Instant.toString] always renders UTC, so encoding is canonical;
 * parsing accepts any ISO offset and normalizes to the same instant.
 */
internal object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("net.spross.kern.store.IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val text = decoder.decodeString()
        return try {
            Instant.parse(text)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("invalid ISO-8601 instant \"$text\"")
        }
    }
}
