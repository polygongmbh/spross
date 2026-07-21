package net.spross.kern.model

/** Exercise role of a scheduling unit. */
enum class Role(val rank: Int, val keySegment: String) {
    Produce(0, "produce"),
    Recognize(1, "recognize"),
}

/**
 * Persisted scheduling-unit identity: `id|produce`, or `id|recognize|<form>` for EVERY
 * recognize unit (canonical form included). `form` is stored in normalized form-key shape.
 * Keys are source-agnostic — switching source preserves every schedule.
 */
data class UnitKey(
    val cardId: String,
    val role: Role,
    /** Normalized form key; present iff [role] == [Role.Recognize]. */
    val form: String? = null,
) {
    init {
        require((role == Role.Recognize) == (form != null)) {
            "form present iff recognize: $cardId/$role/$form"
        }
        require(cardId.isNotEmpty() && '|' !in cardId) { "invalid cardId: $cardId" }
        require(form == null || form.isNotEmpty()) { "recognize form must be non-empty: $cardId" }
    }

    val encoded: String
        get() = when (role) {
            Role.Produce -> "$cardId|${role.keySegment}"
            Role.Recognize -> "$cardId|${role.keySegment}|$form"
        }

    companion object {
        fun produce(cardId: String): UnitKey = UnitKey(cardId, Role.Produce, null)

        /** Builds the recognize key for a raw catalog form (normalizes it). */
        fun recognize(cardId: String, rawForm: String): UnitKey =
            UnitKey(cardId, Role.Recognize, formKey(rawForm))

        /** Parses an encoded key; null when malformed. Does not re-normalize the form. */
        fun parse(encoded: String): UnitKey? {
            val parts = encoded.split('|')
            return when {
                parts.size == 2 && parts[1] == Role.Produce.keySegment && parts[0].isNotEmpty() ->
                    UnitKey(parts[0], Role.Produce, null)
                parts.size == 3 && parts[1] == Role.Recognize.keySegment &&
                    parts[0].isNotEmpty() && parts[2].isNotEmpty() ->
                    UnitKey(parts[0], Role.Recognize, parts[2])
                else -> null
            }
        }

        private val whitespaceRun = Regex("\\s+")

        /**
         * Key shape of a recognize form: NFC-normalized, trimmed, whitespace-collapsed,
         * `|`-stripped (newlines collapse as whitespace). Cyrillic-safe — never slugified.
         */
        fun formKey(raw: String): String =
            nfcNormalized(raw).replace("|", "").trim().replace(whitespaceRun, " ")
    }
}
