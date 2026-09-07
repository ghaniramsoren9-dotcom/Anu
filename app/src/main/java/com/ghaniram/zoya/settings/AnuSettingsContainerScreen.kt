package com.ghaniram.zoya.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ZoyaLanguage
import com.ghaniram.zoya.ZoyaUiState

/**
 * Host container for the Anu Settings hierarchy.
 * Keeps the destination stack in saveable state so leaving Settings for Chat/Home
 * and returning later restores the exact settings sub-page instead of HOME.
 */
@Composable
fun AnuSettingsContainerScreen(
    state: ZoyaUiState,
    onSelectLanguage: (ZoyaLanguage) -> Unit,
    onClearMemory: () -> Unit,
    onOpenControlCenter: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { AnuSettingsStore.getInstance(context) }

    // Store stable enum names rather than enum instances so the stack survives
    // Compose disposal/recreation and the outer tab's SaveableStateHolder.
    var navNames by rememberSaveable { mutableStateOf(listOf(SettingsScreenDestination.HOME.name)) }
    val navStack = navNames.mapNotNull { name -> runCatching { SettingsScreenDestination.valueOf(name) }.getOrNull() }
        .ifEmpty { listOf(SettingsScreenDestination.HOME) }
    val currentScreen = navStack.last()

    fun navigateTo(dest: SettingsScreenDestination) {
        navNames = navNames + dest.name
    }

    fun navigateBack() {
        if (navNames.size > 1) navNames = navNames.dropLast(1)
    }

    BackHandler(enabled = navNames.size > 1) {
        navigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "settingsNavTransition"
        ) { target ->
            when (target) {
                SettingsScreenDestination.HOME -> AnuSettingsHomeScreen(
                    store = store,
                    onNavigate = { navigateTo(it) }
                )
                SettingsScreenDestination.PERSONAL -> AnuPersonalSettingsScreen(
                    store = store,
                    onBack = { navigateBack() }
                )
                SettingsScreenDestination.ASSISTANT_ANU -> AnuAssistantSettingsScreen(
                    store = store,
                    currentLanguage = state.language,
                    onSelectLanguage = onSelectLanguage,
                    onOpenVoicePicker = { navigateTo(SettingsScreenDestination.VOICE_PICKER) },
                    onBack = { navigateBack() }
                )
                SettingsScreenDestination.VOICE_PICKER -> AnuVoicePickerScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.SKILLS -> AnuSkillsSettingsScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.SUB_AGENTS -> AnuSubAgentsScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.EMAIL -> AnuEmailSettingsScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.WHATSAPP_REPORTS -> AnuWhatsAppReportsScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.SOCIAL_MEDIA -> AnuSocialMediaScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.CONNECTORS -> AnuConnectorsScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.BACKUP_RESTORE -> AnuBackupRestoreScreen(store = store, state = state, onBack = { navigateBack() })
                SettingsScreenDestination.ADVANCED -> AnuAdvancedSettingsScreen(store = store, onNavigate = { navigateTo(it) }, onBack = { navigateBack() })
                SettingsScreenDestination.THEME_CUSTOMIZATION -> AnuThemeSettingsScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.APPEARANCE_ORB -> AnuAppearanceScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.BEHAVIOUR -> AnuBehaviourScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.TYPING -> AnuTypingScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.VOICE_GUARDIAN -> AnuVoiceGuardianScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.EMERGENCY_SOS -> AnuEmergencySosScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.TOUCH_GUARD -> AnuTouchGuardScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.SCREEN_LOCK -> AnuScreenLockScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.PERMISSIONS -> AnuPermissionsSettingsScreen(onBack = { navigateBack() })
                SettingsScreenDestination.WHATSAPP_AUTO_REPLY -> AnuWhatsAppAutoReplyScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.EVENT_TRIGGERS -> AnuEventTriggersScreen(store = store, onBack = { navigateBack() })
                SettingsScreenDestination.OPTIONAL_PLACES -> AnuOptionalPlacesScreen(store = store, onBack = { navigateBack() })
            }
        }
    }
}
