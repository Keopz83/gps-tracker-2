package com.example.gpstracker.domain

/** REQ-21: the gender of the Google text-to-speech voices, which only show a three-letter code in their names. */
private val GOOGLE_VOICES = mapOf(
    // en-GB
    "gba" to VoiceGender.FEMALE, "gbb" to VoiceGender.MALE, "gbc" to VoiceGender.FEMALE,
    "gbd" to VoiceGender.MALE, "gbg" to VoiceGender.FEMALE, "rjs" to VoiceGender.MALE,
    // en-US
    "iob" to VoiceGender.FEMALE, "iog" to VoiceGender.FEMALE, "iol" to VoiceGender.MALE, "iom" to VoiceGender.MALE,
    "sfg" to VoiceGender.FEMALE, "tpc" to VoiceGender.FEMALE, "tpd" to VoiceGender.MALE, "tpf" to VoiceGender.FEMALE,
    // en-AU
    "aua" to VoiceGender.FEMALE, "aub" to VoiceGender.MALE, "auc" to VoiceGender.FEMALE, "aud" to VoiceGender.MALE,
)

/** Google voices, e.g. `en-gb-x-gbb-local`. */
private val GOOGLE_NAME = Regex("""^[a-z]{2}-[a-z]{2}-x-([a-z]{3})-""")

/** Samsung voices, e.g. `en-GB-SMTf00` (f: female, m: male). */
private val SAMSUNG_NAME = Regex("""SMT([fm])\d""")

/**
 * REQ-21: the gender of a text-to-speech voice, read from its [name] since Android does not tell it, or null when
 * the voice is not known.
 */
fun voiceGender(name: String): VoiceGender? {
    val lower = name.lowercase()
    if ("female" in lower) return VoiceGender.FEMALE
    if ("male" in lower) return VoiceGender.MALE
    SAMSUNG_NAME.find(name)?.let { return if (it.groupValues[1] == "f") VoiceGender.FEMALE else VoiceGender.MALE }
    return GOOGLE_NAME.find(lower)?.let { GOOGLE_VOICES[it.groupValues[1]] }
}

/** REQ-21: one installed voice as offered by the text-to-speech engine. */
data class VoiceOption(val name: String, val languageTag: String, val needsNetwork: Boolean, val installed: Boolean)

/**
 * REQ-21: the voice to speak English notes with in the [gender] chosen: an installed British voice first, then an
 * American one, then any English one, preferring voices that work offline; null when none is known, so the engine's
 * default voice is used.
 */
fun pickVoice(voices: List<VoiceOption>, gender: VoiceGender): VoiceOption? {
    val candidates = voices.filter { it.installed && it.languageTag.startsWith("en") && voiceGender(it.name) == gender }
    return candidates.minWithOrNull(
        compareBy<VoiceOption> {
            when {
                it.languageTag.equals("en-GB", ignoreCase = true) -> 0
                it.languageTag.equals("en-US", ignoreCase = true) -> 1
                else -> 2
            }
        }.thenBy { it.needsNetwork }.thenBy { it.name },
    )
}
