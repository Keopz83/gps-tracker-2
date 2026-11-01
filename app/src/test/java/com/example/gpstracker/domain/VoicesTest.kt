package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicesTest {
    @Test
    fun `REQ-21 the gender of Google, Samsung and named voices is recognised`() {
        assertEquals(VoiceGender.FEMALE, voiceGender("en-gb-x-gba-local"))
        assertEquals(VoiceGender.MALE, voiceGender("en-gb-x-gbb-network"))
        assertEquals(VoiceGender.MALE, voiceGender("en-us-x-iom-local"))
        assertEquals(VoiceGender.FEMALE, voiceGender("en-GB-SMTf00"))
        assertEquals(VoiceGender.MALE, voiceGender("en-US-SMTm00"))
        assertEquals(VoiceGender.FEMALE, voiceGender("English Female 2"))
        assertEquals(VoiceGender.MALE, voiceGender("english_male"))
        assertNull(voiceGender("en-GB-language"))
    }

    @Test
    fun `REQ-21 an installed offline British voice of the chosen gender is preferred`() {
        val voices = listOf(
            VoiceOption("en-us-x-iom-local", "en-US", needsNetwork = false, installed = true),
            VoiceOption("en-gb-x-gbb-network", "en-GB", needsNetwork = true, installed = true),
            VoiceOption("en-gb-x-gbd-local", "en-GB", needsNetwork = false, installed = true),
            VoiceOption("en-gb-x-rjs-local", "en-GB", needsNetwork = false, installed = false),
            VoiceOption("en-gb-x-gba-local", "en-GB", needsNetwork = false, installed = true),
        )

        assertEquals("en-gb-x-gbd-local", pickVoice(voices, VoiceGender.MALE)?.name)
        assertEquals("en-gb-x-gba-local", pickVoice(voices, VoiceGender.FEMALE)?.name)
    }

    @Test
    fun `REQ-21 without a known voice of the chosen gender the default voice is kept`() {
        val voices = listOf(
            VoiceOption("en-GB-language", "en-GB", needsNetwork = false, installed = true),
            VoiceOption("de-de-x-deb-local", "de-DE", needsNetwork = false, installed = true),
        )

        assertNull(pickVoice(voices, VoiceGender.MALE))
    }
}
