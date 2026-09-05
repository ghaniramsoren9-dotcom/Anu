package com.ghaniram.zoya

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central persistent store for all Anu settings, options, configurations, and triggers.
 * Ensures every option in the 34-page specification is properly reactive, persisted,
 * and working smoothly.
 */
class AnuSettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("anu_settings_preferences", Context.MODE_PRIVATE)

    // Reactive StateFlow for UI updates
    private val _stateVersion = MutableStateFlow(0)
    val stateVersion: StateFlow<Int> = _stateVersion.asStateFlow()

    private fun notifyChanged() {
        _stateVersion.value += 1
    }

    // -------------------------------------------------------------
    // PERSONAL
    // -------------------------------------------------------------
    var userName: String
        get() = prefs.getString("user_name", "Ghaniram") ?: "Ghaniram"
        set(value) { prefs.edit().putString("user_name", value).apply(); notifyChanged() }

    var userGender: String
        get() = prefs.getString("user_gender", "Male") ?: "Male"
        set(value) { prefs.edit().putString("user_gender", value).apply(); notifyChanged() }

    var userPhone: String
        get() = prefs.getString("user_phone", "") ?: ""
        set(value) { prefs.edit().putString("user_phone", value).apply(); notifyChanged() }

    var musicApp: String
        get() = prefs.getString("music_app", "YouTube") ?: "YouTube"
        set(value) { prefs.edit().putString("music_app", value).apply(); notifyChanged() }

    var favoriteSong: String
        get() = prefs.getString("favorite_song", "Bye Bye") ?: "Bye Bye"
        set(value) { prefs.edit().putString("favorite_song", value).apply(); notifyChanged() }

    var customGeminiKey: String
        get() = prefs.getString("custom_gemini_key", "") ?: ""
        set(value) { prefs.edit().putString("custom_gemini_key", value).apply(); notifyChanged() }

    var youtubeChannel: String
        get() = prefs.getString("youtube_channel", "") ?: ""
        set(value) { prefs.edit().putString("youtube_channel", value).apply(); notifyChanged() }

    var youtubeApiKey: String
        get() = prefs.getString("youtube_api_key", "") ?: ""
        set(value) { prefs.edit().putString("youtube_api_key", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // ANU ASSISTANT
    // -------------------------------------------------------------
    var assistantName: String
        get() = prefs.getString("assistant_name", "Anu") ?: "Anu"
        set(value) { prefs.edit().putString("assistant_name", value).apply(); notifyChanged() }

    var persona: String
        get() = prefs.getString("persona", "Anu") ?: "Anu"
        set(value) { prefs.edit().putString("persona", value).apply(); notifyChanged() }

    var girlfriendMode: Boolean
        get() = prefs.getBoolean("girlfriend_mode", false)
        set(value) { prefs.edit().putBoolean("girlfriend_mode", value).apply(); notifyChanged() }

    var rememberOnHerOwn: Boolean
        get() = prefs.getBoolean("remember_on_her_own", true)
        set(value) { prefs.edit().putBoolean("remember_on_her_own", value).apply(); notifyChanged() }

    var incognitoMemory: Boolean
        get() = prefs.getBoolean("incognito_memory", false)
        set(value) { prefs.edit().putBoolean("incognito_memory", value).apply(); notifyChanged() }

    var selectedVoiceCategory: String
        get() = prefs.getString("voice_category", "Anu") ?: "Anu"
        set(value) { prefs.edit().putString("voice_category", value).apply(); notifyChanged() }

    var selectedVoiceTone: String
        get() = prefs.getString("voice_tone", "Breezy") ?: "Breezy"
        set(value) { prefs.edit().putString("voice_tone", value).apply(); notifyChanged() }

    var selectedVoiceSpeaker: String
        get() = prefs.getString("voice_speaker", "Aoede") ?: "Aoede"
        set(value) { prefs.edit().putString("voice_speaker", value).apply(); notifyChanged() }

    var bringWakeWordBack: Boolean
        get() = prefs.getBoolean("bring_wake_word_back", false)
        set(value) { prefs.edit().putBoolean("bring_wake_word_back", value).apply(); notifyChanged() }

    var proactiveAnu: Boolean
        get() = prefs.getBoolean("proactive_anu", true)
        set(value) { prefs.edit().putBoolean("proactive_anu", value).apply(); notifyChanged() }

    var announceIncomingCalls: Boolean
        get() = prefs.getBoolean("announce_incoming_calls", true)
        set(value) { prefs.edit().putBoolean("announce_incoming_calls", value).apply(); notifyChanged() }

    var keepRingtonePlaying: Boolean
        get() = prefs.getBoolean("keep_ringtone_playing", false)
        set(value) { prefs.edit().putBoolean("keep_ringtone_playing", value).apply(); notifyChanged() }

    var drivingModeRejectCalls: Boolean
        get() = prefs.getBoolean("driving_mode_reject_calls", false)
        set(value) { prefs.edit().putBoolean("driving_mode_reject_calls", value).apply(); notifyChanged() }

    var drivingAutoReplyText: String
        get() = prefs.getString(
            "driving_auto_reply_text",
            "{name} abhi drive kar rahe hain, isliye call nahi utha paye. Free hote hi call karenge. - Anu (auto reply)"
        ) ?: "{name} abhi drive kar rahe hain, isliye call nahi utha paye. Free hote hi call karenge. - Anu (auto reply)"
        set(value) { prefs.edit().putString("driving_auto_reply_text", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // SKILLS
    // -------------------------------------------------------------
    val defaultEnabledSkills = setOf(
        "pro-email", "group-report", "pro-whatsapp", "youtube-script",
        "youtube-title-thumbnail", "youtube-upload", "social-posting",
        "web-design", "coding-agent", "code-publish", "whatsapp-pro"
    )

    var enabledSkills: Set<String>
        get() = prefs.getStringSet("enabled_skills", defaultEnabledSkills) ?: defaultEnabledSkills
        set(value) { prefs.edit().putStringSet("enabled_skills", value).apply(); notifyChanged() }

    fun toggleSkill(skillId: String) {
        val current = enabledSkills.toMutableSet()
        if (current.contains(skillId)) current.remove(skillId) else current.add(skillId)
        enabledSkills = current
    }

    var installedStoreSkills: Set<String>
        get() = prefs.getStringSet("installed_store_skills", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("installed_store_skills", value).apply(); notifyChanged() }

    fun installStoreSkill(skillId: String) {
        val storeSet = installedStoreSkills.toMutableSet()
        storeSet.add(skillId)
        installedStoreSkills = storeSet
        val activeSet = enabledSkills.toMutableSet()
        activeSet.add(skillId)
        enabledSkills = activeSet
    }

    // -------------------------------------------------------------
    // SUB-AGENTS
    // -------------------------------------------------------------
    var codingModelsOrder: String
        get() = prefs.getString("coding_models_order", "gemini-3.6-flash,gemini-3.1-flash-lite,gemini-2.5-flash,gemini-3.5-flash")
            ?: "gemini-3.6-flash,gemini-3.1-flash-lite,gemini-2.5-flash,gemini-3.5-flash"
        set(value) { prefs.edit().putString("coding_models_order", value).apply(); notifyChanged() }

    var customProvidersEnabled: Boolean
        get() = prefs.getBoolean("custom_providers_enabled", true)
        set(value) { prefs.edit().putBoolean("custom_providers_enabled", value).apply(); notifyChanged() }

    var defaultGeminiProviderActive: Boolean
        get() = prefs.getBoolean("default_gemini_provider_active", true)
        set(value) { prefs.edit().putBoolean("default_gemini_provider_active", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // EMAIL
    // -------------------------------------------------------------
    var emailAddress: String
        get() = prefs.getString("email_address", "") ?: ""
        set(value) { prefs.edit().putString("email_address", value).apply(); notifyChanged() }

    var emailAppPassword: String
        get() = prefs.getString("email_app_password", "") ?: ""
        set(value) { prefs.edit().putString("email_app_password", value).apply(); notifyChanged() }

    var emailSenderName: String
        get() = prefs.getString("email_sender_name", "") ?: ""
        set(value) { prefs.edit().putString("email_sender_name", value).apply(); notifyChanged() }

    var emailSignature: String
        get() = prefs.getString("email_signature", "Regards,\nYour Name\n+91 90000 00000")
            ?: "Regards,\nYour Name\n+91 90000 00000"
        set(value) { prefs.edit().putString("email_signature", value).apply(); notifyChanged() }

    var emailSmtpHost: String
        get() = prefs.getString("email_smtp_host", "") ?: ""
        set(value) { prefs.edit().putString("email_smtp_host", value).apply(); notifyChanged() }

    var emailSmtpPort: String
        get() = prefs.getString("email_smtp_port", "465") ?: "465"
        set(value) { prefs.edit().putString("email_smtp_port", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // WHATSAPP GROUPS & REPORTS
    // -------------------------------------------------------------
    fun getWhatsAppGroups(): List<String> {
        val raw = prefs.getString("whatsapp_groups_list", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addWhatsAppGroup(name: String) {
        val current = getWhatsAppGroups().toMutableList()
        if (!current.contains(name.trim())) {
            current.add(name.trim())
            prefs.edit().putString("whatsapp_groups_list", current.joinToString("|||")).apply()
            notifyChanged()
        }
    }

    fun removeWhatsAppGroup(name: String) {
        val current = getWhatsAppGroups().toMutableList()
        current.remove(name.trim())
        prefs.edit().putString("whatsapp_groups_list", current.joinToString("|||")).apply()
        notifyChanged()
    }

    fun getReportFormats(): List<String> {
        val raw = prefs.getString("report_formats_list", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addReportFormat(format: String) {
        val current = getReportFormats().toMutableList()
        current.add(format.trim())
        prefs.edit().putString("report_formats_list", current.joinToString("|||")).apply()
        notifyChanged()
    }

    fun removeReportFormat(index: Int) {
        val current = getReportFormats().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            prefs.edit().putString("report_formats_list", current.joinToString("|||")).apply()
            notifyChanged()
        }
    }

    // -------------------------------------------------------------
    // SOCIAL MEDIA
    // -------------------------------------------------------------
    var socialHandle: String
        get() = prefs.getString("social_handle", "") ?: ""
        set(value) { prefs.edit().putString("social_handle", value).apply(); notifyChanged() }

    var socialPlatform: String
        get() = prefs.getString("social_platform", "Instagram") ?: "Instagram"
        set(value) { prefs.edit().putString("social_platform", value).apply(); notifyChanged() }

    var socialCaptionVoice: String
        get() = prefs.getString("social_caption_voice", "funny, seedha simple, motivational")
            ?: "funny, seedha simple, motivational"
        set(value) { prefs.edit().putString("social_caption_voice", value).apply(); notifyChanged() }

    var dailyStoryEnabled: Boolean
        get() = prefs.getBoolean("daily_story_enabled", false)
        set(value) { prefs.edit().putBoolean("daily_story_enabled", value).apply(); notifyChanged() }

    var autoPostWithoutAsking: Boolean
        get() = prefs.getBoolean("auto_post_without_asking", false)
        set(value) { prefs.edit().putBoolean("auto_post_without_asking", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // CONNECTORS
    // -------------------------------------------------------------
    var connectedAccounts: Set<String>
        get() = prefs.getStringSet("connected_accounts", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("connected_accounts", value).apply(); notifyChanged() }

    fun toggleConnector(name: String) {
        val current = connectedAccounts.toMutableSet()
        if (current.contains(name)) current.remove(name) else current.add(name)
        connectedAccounts = current
    }

    // -------------------------------------------------------------
    // THEME & APPEARANCE
    // -------------------------------------------------------------
    var themeMode: String
        get() = prefs.getString("theme_mode", "Light") ?: "Light"
        set(value) { prefs.edit().putString("theme_mode", value).apply(); notifyChanged() }

    var themePreset: String
        get() = prefs.getString("theme_preset", "Midnight") ?: "Midnight"
        set(value) { prefs.edit().putString("theme_preset", value).apply(); notifyChanged() }

    var typeface: String
        get() = prefs.getString("typeface_choice", "Inter") ?: "Inter"
        set(value) { prefs.edit().putString("typeface_choice", value).apply(); notifyChanged() }

    var textSize: String
        get() = prefs.getString("text_size_choice", "Default") ?: "Default"
        set(value) { prefs.edit().putString("text_size_choice", value).apply(); notifyChanged() }

    var surfaceStyle: String
        get() = prefs.getString("surface_style_choice", "Glass") ?: "Glass"
        set(value) { prefs.edit().putString("surface_style_choice", value).apply(); notifyChanged() }

    var surfaceCorners: String
        get() = prefs.getString("surface_corners_choice", "Rounded") ?: "Rounded"
        set(value) { prefs.edit().putString("surface_corners_choice", value).apply(); notifyChanged() }

    var orbStyle: String
        get() = prefs.getString("orb_style", "Anu 2047") ?: "Anu 2047"
        set(value) { prefs.edit().putString("orb_style", value).apply(); notifyChanged() }

    var orbColorName: String
        get() = prefs.getString("orb_color_name", "Persona") ?: "Persona"
        set(value) { prefs.edit().putString("orb_color_name", value).apply(); notifyChanged() }

    var orbSizeDp: Float
        get() = prefs.getFloat("orb_size_dp", 190f)
        set(value) { prefs.edit().putFloat("orb_size_dp", value).apply(); notifyChanged() }

    var useOrbOnHome: Boolean
        get() = prefs.getBoolean("use_orb_on_home", false)
        set(value) { prefs.edit().putBoolean("use_orb_on_home", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // BEHAVIOUR
    // -------------------------------------------------------------
    var floatingOrbEnabled: Boolean
        get() = prefs.getBoolean("floating_orb_enabled", true)
        set(value) { prefs.edit().putBoolean("floating_orb_enabled", value).apply(); notifyChanged() }

    var edgeGlowEnabled: Boolean
        get() = prefs.getBoolean("edge_glow_enabled", true)
        set(value) { prefs.edit().putBoolean("edge_glow_enabled", value).apply(); notifyChanged() }

    var echoGuardEnabled: Boolean
        get() = prefs.getBoolean("echo_guard_enabled", true)
        set(value) { prefs.edit().putBoolean("echo_guard_enabled", value).apply(); notifyChanged() }

    var screenRecordingMode: Boolean
        get() = prefs.getBoolean("screen_recording_mode", false)
        set(value) { prefs.edit().putBoolean("screen_recording_mode", value).apply(); notifyChanged() }

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", true)
        set(value) { prefs.edit().putBoolean("start_on_boot", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // TYPING
    // -------------------------------------------------------------
    var humanTypingInEditors: Boolean
        get() = prefs.getBoolean("human_typing_in_editors", true)
        set(value) { prefs.edit().putBoolean("human_typing_in_editors", value).apply(); notifyChanged() }

    var typingSpeed: String
        get() = prefs.getString("typing_speed", "Normal") ?: "Normal"
        set(value) { prefs.edit().putString("typing_speed", value).apply(); notifyChanged() }

    var realisticTypingWhileCoding: Boolean
        get() = prefs.getBoolean("realistic_typing_while_coding", true)
        set(value) { prefs.edit().putBoolean("realistic_typing_while_coding", value).apply(); notifyChanged() }

    var typingTargetApps: String
        get() = prefs.getString(
            "typing_target_apps",
            "com.google.android.keep,com.google.android.apps.docs.editors.docs,com.samsung.android.app.notes,com.termux,net.gsantner.markor,com.foxdebug.acode,org.jotdown.jota"
        ) ?: "com.google.android.keep,com.google.android.apps.docs.editors.docs,com.samsung.android.app.notes,com.termux,net.gsantner.markor,com.foxdebug.acode,org.jotdown.jota"
        set(value) { prefs.edit().putString("typing_target_apps", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // VOICE GUARDIAN
    // -------------------------------------------------------------
    var voiceGuardianOn: Boolean
        get() = prefs.getBoolean("voice_guardian_on", true)
        set(value) { prefs.edit().putBoolean("voice_guardian_on", value).apply(); notifyChanged() }

    var awayGuardModeLock: Boolean
        get() = prefs.getBoolean("away_guard_mode_lock", false)
        set(value) { prefs.edit().putBoolean("away_guard_mode_lock", value).apply(); notifyChanged() }

    var voiceListenMode: String
        get() = prefs.getString("voice_listen_mode", "Everyone") ?: "Everyone"
        set(value) { prefs.edit().putString("voice_listen_mode", value).apply(); notifyChanged() }

    var voiceMatchStrictness: Float
        get() = prefs.getFloat("voice_match_strictness", 0.40f)
        set(value) { prefs.edit().putFloat("voice_match_strictness", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // EMERGENCY SOS
    // -------------------------------------------------------------
    var sosCountryCode: String
        get() = prefs.getString("sos_country_code", "India (+91)") ?: "India (+91)"
        set(value) { prefs.edit().putString("sos_country_code", value).apply(); notifyChanged() }

    fun getSosContacts(): List<String> {
        val raw = prefs.getString("sos_contacts_list", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addSosContact(contact: String) {
        val current = getSosContacts().toMutableList()
        if (!current.contains(contact)) {
            current.add(contact)
            prefs.edit().putString("sos_contacts_list", current.joinToString("|||")).apply()
            notifyChanged()
        }
    }

    fun removeSosContact(contact: String) {
        val current = getSosContacts().toMutableList()
        current.remove(contact)
        prefs.edit().putString("sos_contacts_list", current.joinToString("|||")).apply()
        notifyChanged()
    }

    // -------------------------------------------------------------
    // TOUCH GUARD
    // -------------------------------------------------------------
    var touchGuardEnabled: Boolean
        get() = prefs.getBoolean("touch_guard_enabled", false)
        set(value) { prefs.edit().putBoolean("touch_guard_enabled", value).apply(); notifyChanged() }

    var touchGuardArmed: Boolean
        get() = prefs.getBoolean("touch_guard_armed", false)
        set(value) { prefs.edit().putBoolean("touch_guard_armed", value).apply(); notifyChanged() }

    var godModeEnabled: Boolean
        get() = prefs.getBoolean("god_mode_enabled", false)
        set(value) { prefs.edit().putBoolean("god_mode_enabled", value).apply(); notifyChanged() }

    var letAnyoneDisarmByVoice: Boolean
        get() = prefs.getBoolean("let_anyone_disarm_by_voice", false)
        set(value) { prefs.edit().putBoolean("let_anyone_disarm_by_voice", value).apply(); notifyChanged() }

    var warnFirstSirenSecond: Boolean
        get() = prefs.getBoolean("warn_first_siren_second", true)
        set(value) { prefs.edit().putBoolean("warn_first_siren_second", value).apply(); notifyChanged() }

    var lockScreenImmediatelyOnTouch: Boolean
        get() = prefs.getBoolean("lock_screen_immediately_on_touch", true)
        set(value) { prefs.edit().putBoolean("lock_screen_immediately_on_touch", value).apply(); notifyChanged() }

    var blinkTorchWithSiren: Boolean
        get() = prefs.getBoolean("blink_torch_with_siren", true)
        set(value) { prefs.edit().putBoolean("blink_torch_with_siren", value).apply(); notifyChanged() }

    var textSosAfter3Touches: Boolean
        get() = prefs.getBoolean("text_sos_after_3_touches", false)
        set(value) { prefs.edit().putBoolean("text_sos_after_3_touches", value).apply(); notifyChanged() }

    var touchMovementSensitivity: String
        get() = prefs.getString("touch_movement_sensitivity", "Medium") ?: "Medium"
        set(value) { prefs.edit().putString("touch_movement_sensitivity", value).apply(); notifyChanged() }

    var tripWhenChargerPulled: Boolean
        get() = prefs.getBoolean("trip_when_charger_pulled", true)
        set(value) { prefs.edit().putBoolean("trip_when_charger_pulled", value).apply(); notifyChanged() }

    var stealthRecordOnlyNoSound: Boolean
        get() = prefs.getBoolean("stealth_record_only_no_sound", false)
        set(value) { prefs.edit().putBoolean("stealth_record_only_no_sound", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // SCREEN LOCK
    // -------------------------------------------------------------
    var wakeScreenWhenNeeded: Boolean
        get() = prefs.getBoolean("wake_screen_when_needed", true)
        set(value) { prefs.edit().putBoolean("wake_screen_when_needed", value).apply(); notifyChanged() }

    var unlockForMe: Boolean
        get() = prefs.getBoolean("unlock_for_me", false)
        set(value) { prefs.edit().putBoolean("unlock_for_me", value).apply(); notifyChanged() }

    var savedPin: String
        get() = prefs.getString("saved_pin", "") ?: ""
        set(value) { prefs.edit().putString("saved_pin", value).apply(); notifyChanged() }

    var readPositionOffLockScreen: Boolean
        get() = prefs.getBoolean("read_position_off_lock_screen", true)
        set(value) { prefs.edit().putBoolean("read_position_off_lock_screen", value).apply(); notifyChanged() }

    var lockVerticalPosition: Float
        get() = prefs.getFloat("lock_vertical_position", 0.5f)
        set(value) { prefs.edit().putFloat("lock_vertical_position", value).apply(); notifyChanged() }

    var lockGridSize: Float
        get() = prefs.getFloat("lock_grid_size", 0.5f)
        set(value) { prefs.edit().putFloat("lock_grid_size", value).apply(); notifyChanged() }

    var lockDrawingSpeedMs: Float
        get() = prefs.getFloat("lock_drawing_speed_ms", 150f)
        set(value) { prefs.edit().putFloat("lock_drawing_speed_ms", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // WHATSAPP AUTO-REPLY
    // -------------------------------------------------------------
    var whatsAppAutoReplyEnabled: Boolean
        get() = prefs.getBoolean("whatsapp_auto_reply_enabled", false)
        set(value) { prefs.edit().putBoolean("whatsapp_auto_reply_enabled", value).apply(); notifyChanged() }

    var replyOnlyWhileAsleep: Boolean
        get() = prefs.getBoolean("reply_only_while_asleep", true)
        set(value) { prefs.edit().putBoolean("reply_only_while_asleep", value).apply(); notifyChanged() }

    var replyIncludeGroups: Boolean
        get() = prefs.getBoolean("reply_include_groups", false)
        set(value) { prefs.edit().putBoolean("reply_include_groups", value).apply(); notifyChanged() }

    var replyInstructions: String
        get() = prefs.getString(
            "reply_instructions",
            "Politely say I am busy right now and will reply myself soon. Keep it short, warm and friendly. Reply in the same language they wrote in."
        ) ?: "Politely say I am busy right now and will reply myself soon. Keep it short, warm and friendly. Reply in the same language they wrote in."
        set(value) { prefs.edit().putString("reply_instructions", value).apply(); notifyChanged() }

    var replyFallbackNoInternet: String
        get() = prefs.getString("reply_fallback_no_internet", "Abhi busy hoon, thodi der me reply karta hoon.")
            ?: "Abhi busy hoon, thodi der me reply karta hoon."
        set(value) { prefs.edit().putString("reply_fallback_no_internet", value).apply(); notifyChanged() }

    var replySignatureNote: String
        get() = prefs.getString("reply_signature_note", "- Anu (auto-reply)") ?: "- Anu (auto-reply)"
        set(value) { prefs.edit().putString("reply_signature_note", value).apply(); notifyChanged() }

    var replyOnlyChats: String
        get() = prefs.getString("reply_only_chats", "") ?: ""
        set(value) { prefs.edit().putString("reply_only_chats", value).apply(); notifyChanged() }

    var replyNeverChats: String
        get() = prefs.getString("reply_never_chats", "Mummy, Boss") ?: "Mummy, Boss"
        set(value) { prefs.edit().putString("reply_never_chats", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // EVENT TRIGGERS
    // -------------------------------------------------------------
    var eventAnnouncementsMaster: Boolean
        get() = prefs.getBoolean("event_announcements_master", true)
        set(value) { prefs.edit().putBoolean("event_announcements_master", value).apply(); notifyChanged() }

    var speakWhileAsleep: Boolean
        get() = prefs.getBoolean("speak_while_asleep", true)
        set(value) { prefs.edit().putBoolean("speak_while_asleep", value).apply(); notifyChanged() }

    var speakOnSilentToo: Boolean
        get() = prefs.getBoolean("speak_on_silent_too", false)
        set(value) { prefs.edit().putBoolean("speak_on_silent_too", value).apply(); notifyChanged() }

    // Triggers switches
    var triggerChargerPlugged: Boolean
        get() = prefs.getBoolean("trig_charger_plugged", true)
        set(value) { prefs.edit().putBoolean("trig_charger_plugged", value).apply(); notifyChanged() }

    var triggerChargerUnplugged: Boolean
        get() = prefs.getBoolean("trig_charger_unplugged", true)
        set(value) { prefs.edit().putBoolean("trig_charger_unplugged", value).apply(); notifyChanged() }

    var triggerBatteryFull: Boolean
        get() = prefs.getBoolean("trig_battery_full", true)
        set(value) { prefs.edit().putBoolean("trig_battery_full", value).apply(); notifyChanged() }

    var triggerBatteryLow: Boolean
        get() = prefs.getBoolean("trig_battery_low", true)
        set(value) { prefs.edit().putBoolean("trig_battery_low", value).apply(); notifyChanged() }

    var triggerBatteryCritical: Boolean
        get() = prefs.getBoolean("trig_battery_critical", true)
        set(value) { prefs.edit().putBoolean("trig_battery_critical", value).apply(); notifyChanged() }

    var triggerBatterySaverOn: Boolean
        get() = prefs.getBoolean("trig_battery_saver_on", false)
        set(value) { prefs.edit().putBoolean("trig_battery_saver_on", value).apply(); notifyChanged() }

    var triggerBatterySaverOff: Boolean
        get() = prefs.getBoolean("trig_battery_saver_off", false)
        set(value) { prefs.edit().putBoolean("trig_battery_saver_off", value).apply(); notifyChanged() }

    var triggerHeadphonesPlugged: Boolean
        get() = prefs.getBoolean("trig_headphones_plugged", true)
        set(value) { prefs.edit().putBoolean("trig_headphones_plugged", value).apply(); notifyChanged() }

    var triggerHeadphonesUnplugged: Boolean
        get() = prefs.getBoolean("trig_headphones_unplugged", true)
        set(value) { prefs.edit().putBoolean("trig_headphones_unplugged", value).apply(); notifyChanged() }

    var triggerBluetoothConnected: Boolean
        get() = prefs.getBoolean("trig_bluetooth_connected", true)
        set(value) { prefs.edit().putBoolean("trig_bluetooth_connected", value).apply(); notifyChanged() }

    var triggerBluetoothDisconnected: Boolean
        get() = prefs.getBoolean("trig_bluetooth_disconnected", true)
        set(value) { prefs.edit().putBoolean("trig_bluetooth_disconnected", value).apply(); notifyChanged() }

    var triggerWifiConnected: Boolean
        get() = prefs.getBoolean("trig_wifi_connected", false)
        set(value) { prefs.edit().putBoolean("trig_wifi_connected", value).apply(); notifyChanged() }

    var triggerWifiLost: Boolean
        get() = prefs.getBoolean("trig_wifi_lost", false)
        set(value) { prefs.edit().putBoolean("trig_wifi_lost", value).apply(); notifyChanged() }

    var triggerAirplaneModeOn: Boolean
        get() = prefs.getBoolean("trig_airplane_on", true)
        set(value) { prefs.edit().putBoolean("trig_airplane_on", value).apply(); notifyChanged() }

    var triggerAirplaneModeOff: Boolean
        get() = prefs.getBoolean("trig_airplane_off", true)
        set(value) { prefs.edit().putBoolean("trig_airplane_off", value).apply(); notifyChanged() }

    var triggerPhoneOnSilent: Boolean
        get() = prefs.getBoolean("trig_phone_silent", false)
        set(value) { prefs.edit().putBoolean("trig_phone_silent", value).apply(); notifyChanged() }

    var triggerRingerBackOn: Boolean
        get() = prefs.getBoolean("trig_ringer_back_on", false)
        set(value) { prefs.edit().putBoolean("trig_ringer_back_on", value).apply(); notifyChanged() }

    var triggerAppInstalled: Boolean
        get() = prefs.getBoolean("trig_app_installed", true)
        set(value) { prefs.edit().putBoolean("trig_app_installed", value).apply(); notifyChanged() }

    var triggerAppUninstalled: Boolean
        get() = prefs.getBoolean("trig_app_uninstalled", true)
        set(value) { prefs.edit().putBoolean("trig_app_uninstalled", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // OPTIONAL INTEGRATIONS & PLACES
    // -------------------------------------------------------------
    var mapsApiKey: String
        get() = prefs.getString("maps_api_key", "") ?: ""
        set(value) { prefs.edit().putString("maps_api_key", value).apply(); notifyChanged() }

    var smartGeofencingEnabled: Boolean
        get() = prefs.getBoolean("smart_geofencing_enabled", true)
        set(value) { prefs.edit().putBoolean("smart_geofencing_enabled", value).apply(); notifyChanged() }

    var weatherProvider: String
        get() = prefs.getString("weather_provider", "Open-Meteo (Free)") ?: "Open-Meteo (Free)"
        set(value) { prefs.edit().putString("weather_provider", value).apply(); notifyChanged() }

    // -------------------------------------------------------------
    // BACKUP & RESTORE EXPORT LOGIC
    // -------------------------------------------------------------
    fun exportBackupJson(memories: List<String>, messagesCount: Int): String {
        val root = JSONObject()
        root.put("app", "Anu")
        root.put("version", "4.0.0")
        root.put("timestamp", System.currentTimeMillis())

        val memArray = JSONArray()
        memories.forEach { memArray.put(it) }
        root.put("memories", memArray)
        root.put("conversations_count", messagesCount)

        val sosArray = JSONArray()
        getSosContacts().forEach { sosArray.put(it) }
        root.put("sos_contacts", sosArray)

        val settingsObj = JSONObject()
        settingsObj.put("userName", userName)
        settingsObj.put("userGender", userGender)
        settingsObj.put("musicApp", musicApp)
        settingsObj.put("favoriteSong", favoriteSong)
        settingsObj.put("persona", persona)
        settingsObj.put("girlfriendMode", girlfriendMode)
        settingsObj.put("themePreset", themePreset)
        settingsObj.put("orbStyle", orbStyle)
        settingsObj.put("orbColorName", orbColorName)
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    companion object {
        @Volatile
        private var INSTANCE: AnuSettingsStore? = null

        fun getInstance(context: Context): AnuSettingsStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnuSettingsStore(context).also { INSTANCE = it }
            }
        }
    }
}
