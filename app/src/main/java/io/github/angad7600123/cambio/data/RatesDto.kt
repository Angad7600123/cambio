package io.github.angad7600123.cambio.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * Reads a JSON number straight into [BigDecimal] using its literal text.
 *
 * Going via [Double] would quietly lose precision on rates such as
 * `1300000.123456`, so this takes the raw token instead.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val literal = when (decoder) {
            is JsonDecoder -> decoder.decodeJsonElement().jsonPrimitive.content
            else -> decoder.decodeString()
        }
        return BigDecimal(literal)
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }
}

/**
 * The wire format of `GET https://open.er-api.com/v6/latest/{BASE}`.
 *
 * Only the fields the app actually uses are declared; the parser is configured to
 * ignore everything else, so the provider adding fields cannot break the app.
 */
@Serializable
data class ExchangeRatesResponse(
    val result: String,
    @SerialName("base_code") val baseCode: String? = null,
    @SerialName("time_last_update_unix") val timeLastUpdateUnix: Long? = null,
    @SerialName("time_next_update_unix") val timeNextUpdateUnix: Long? = null,
    @SerialName("error-type") val errorType: String? = null,
    val rates: Map<
        String,
        @Serializable(with = BigDecimalSerializer::class)
        BigDecimal,
        >? = null,
) {
    val isSuccess: Boolean get() = result.equals(RESULT_SUCCESS, ignoreCase = true)
}

/**
 * The provider's marker for a successful response. Declared at file scope because
 * `@Serializable` generates its own companion object on the class.
 */
private const val RESULT_SUCCESS = "success"
