package com.sjbit.seniorshield.platform

import android.content.Context

data class SeniorShieldSettings(
    val trustedContact: String = "",
    val voiceLanguageTag: String = "hi-IN",
    val appLanguageTag: String = "system",
    val autoFamilyAlert: Boolean = true,
    val ttsRate: Float = 0.82f,
    val onboardingCompleted: Boolean = false
)

class SeniorShieldPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SeniorShieldSettings {
        return SeniorShieldSettings(
            trustedContact = prefs.getString(KEY_TRUSTED_CONTACT, "").orEmpty(),
            voiceLanguageTag = prefs.getString(KEY_VOICE_LANGUAGE_TAG, "hi-IN").orEmpty(),
            appLanguageTag = prefs.getString(KEY_APP_LANGUAGE_TAG, "system").orEmpty(),
            autoFamilyAlert = prefs.getBoolean(KEY_AUTO_FAMILY_ALERT, true),
            ttsRate = prefs.getFloat(KEY_TTS_RATE, 0.82f),
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        )
    }

    fun saveTrustedContact(value: String) {
        prefs.edit().putString(KEY_TRUSTED_CONTACT, value).apply()
    }

    fun saveVoiceLanguageTag(value: String) {
        prefs.edit().putString(KEY_VOICE_LANGUAGE_TAG, value).apply()
    }

    fun saveAppLanguageTag(value: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE_TAG, value).apply()
    }

    fun saveAutoFamilyAlert(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FAMILY_ALERT, value).apply()
    }

    fun saveTtsRate(value: Float) {
        prefs.edit().putFloat(KEY_TTS_RATE, value).apply()
    }

    fun saveOnboardingCompleted(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "senior_shield_prefs"
        const val KEY_TRUSTED_CONTACT = "trusted_contact"
        const val KEY_VOICE_LANGUAGE_TAG = "voice_language_tag"
        const val KEY_APP_LANGUAGE_TAG = "app_language_tag"
        const val KEY_AUTO_FAMILY_ALERT = "auto_family_alert"
        const val KEY_TTS_RATE = "tts_rate"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}
