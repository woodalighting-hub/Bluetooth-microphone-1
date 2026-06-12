package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LanguageManager {
    private const val PREFS_NAME = "earspy_prefs"
    private const val KEY_LANG = "key_app_language"

    private val _currentLanguage = MutableStateFlow("uk") // default to Ukrainian, or auto-detect
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs = prefs
        // Auto-detect Ukrainian system locale or fallback to Ukrainian as requested, otherwise English
        val defaultLang = if (context.resources.configuration.locales[0].language == "uk") "uk" else "en"
        val savedLang = prefs.getString(KEY_LANG, defaultLang) ?: defaultLang
        _currentLanguage.value = savedLang
    }

    fun setLanguage(language: String) {
        if (language != "en" && language != "uk") return
        _currentLanguage.value = language
        sharedPrefs?.edit()?.putString(KEY_LANG, language)?.apply()
    }

    // Get the active strings
    val strings: AppStrings
        get() = if (_currentLanguage.value == "uk") UkrainianStrings else EnglishStrings
}

interface AppStrings {
    val appName: String
    val appTagline: String
    val permissionsTitle: String
    val permissionsDesc: String
    val grantAccess: String
    val secureHeadsetActive: String
    val bluetoothPairing: String
    val bluetoothDisconnected: String
    val scoChannelsUnavailable: String
    val interceptEarFeedback: String
    val stopMonitoring: String
    val startHearMode: String
    val recordInterception: String
    val recordingLabel: String
    val startRecord: String
    val amplifierGainBoost: String
    val interceptionsLog: String
    val totalSuffix: String
    val noInterceptionsSaved: String
    val emptyLogTip: String
    val systemWarning: String
    val renameInterception: String
    val enterCustomName: String
    val save: String
    val cancel: String
    val playingBack: String
    val settingsTitle: String
    val appLanguage: String
    val selectLanguageDesc: String
    val languageEn: String
    val languageUk: String
    val close: String
    val activeRecordingNotif: String
    val activeRecNotifOnly: String
    val activeListNotifOnly: String
    val standbyNotif: String
    val activeNotifTitle: String
    val fileNotFound: String
    val playbackFailed: String
    val micFailed: String
    val micUnavail: String
}

object EnglishStrings : AppStrings {
    override val appName = "Ear Spy"
    override val appTagline = "EAR SPY // MONITOR"
    override val permissionsTitle = "ACCESS PERMISSIONS"
    override val permissionsDesc = "Ear Spy intercepts ambient sound from your Bluetooth headset microphone. To work, the app requires audio recording, local Bluetooth connectivity, and notification privileges."
    override val grantAccess = "GRANT ACCESS"
    override val secureHeadsetActive = "SECURE HEADSET ACTIVE"
    override val bluetoothPairing = "BLUETOOTH PAIRING..."
    override val bluetoothDisconnected = "BLUETOOTH SCANNING/DISCONNECTED"
    override val scoChannelsUnavailable = "SCO CHANNELS UNAVAILABLE"
    override val interceptEarFeedback = "INTERCEPT EAR FEEDBACK"
    override val stopMonitoring = "STOP MONITORING"
    override val startHearMode = "START HEAR MODE"
    override val recordInterception = "RECORD INTERCEPTION"
    override val recordingLabel = "RECORDING... %s"
    override val startRecord = "START RECORD"
    override val amplifierGainBoost = "AMPLIFIER GAIN BOOST"
    override val interceptionsLog = "INTERCEPTIONS LOG"
    override val totalSuffix = "%d TOTAL"
    override val noInterceptionsSaved = "NO INTERCEPTIONS SAVED"
    override val emptyLogTip = "Use the red Record Interception pad above to capture and persist high-gain audio feeds locally."
    override val systemWarning = "SYSTEM WARNING"
    override val renameInterception = "RENAME INTERCEPTION"
    override val enterCustomName = "Enter custom identification name:"
    override val save = "SAVE"
    override val cancel = "CANCEL"
    override val playingBack = "PLAYING BACK"
    override val settingsTitle = "SETTINGS"
    override val appLanguage = "App Language"
    override val selectLanguageDesc = "Select language interface:"
    override val languageEn = "English"
    override val languageUk = "Ukrainian (Українська)"
    override val close = "CLOSE"
    override val activeRecordingNotif = "Listening & Recording audio from headset mic..."
    override val activeRecNotifOnly = "Recording background audio from headset mic..."
    override val activeListNotifOnly = "Listening to ambient sound in real-time..."
    override val standbyNotif = "Ear Spy background service stands by..."
    override val activeNotifTitle = "Ear Spy Active"
    override val fileNotFound = "File not found on disk"
    override val playbackFailed = "Failed to play recording: %s"
    override val micFailed = "Failed to start Microphone: %s"
    override val micUnavail = "Microphone already in use or unavailable: %s"
}

object UkrainianStrings : AppStrings {
    override val appName = "Вушний Шпигун"
    override val appTagline = "ВУШНИЙ ШПИГУН // МОНІТОР"
    override val permissionsTitle = "ДОСТУП ДО ДОЗВОЛІВ"
    override val permissionsDesc = "Вушний Шпигун перехоплює навколишній звук з мікрофона вашої Bluetooth-гарнітури. Для роботи додатку потрібні дозволи на запис аудіо, підключення Bluetooth та відображення сповіщень."
    override val grantAccess = "НАДАТИ ДОСТУП"
    override val secureHeadsetActive = "БЕЗПЕЧНА ГАРНІТУРА АКТИВНА"
    override val bluetoothPairing = "ПІДКЛЮЧЕННЯ BLUETOOTH..."
    override val bluetoothDisconnected = "BLUETOOTH СКАНИР./ВІДКЛЮЧЕНО"
    override val scoChannelsUnavailable = "SCO КАНАЛИ НЕДОСТУПНІ"
    override val interceptEarFeedback = "ЗВОРОТНИЙ ЗВ'ЯЗОК У ВУХО"
    override val stopMonitoring = "ЗУПИНИТИ МОНІТОРИНГ"
    override val startHearMode = "УВІМКНУТИ СЛУХАННЯ"
    override val recordInterception = "ЗАПИСАТИ ПЕРЕХОПЛЕННЯ"
    override val recordingLabel = "ЗАПИСУЄТЬСЯ... %s"
    override val startRecord = "ПОЧАТИ ЗАПИС"
    override val amplifierGainBoost = "ПІДСИЛЕННЯ ЗВУКУ (GAIN)"
    override val interceptionsLog = "ЖУРНАЛ ПЕРЕХОПЛЕНЬ"
    override val totalSuffix = "ВСЬОГО ПЕРЕХОПЛЕНЬ: %d"
    override val noInterceptionsSaved = "НЕМАЄ ЗБЕРЕЖЕНИХ ПЕРЕХОПЛЕНЬ"
    override val emptyLogTip = "Використовуйте червону кнопку запису вище, щоб захопити та зберегти аудіо високої чутливості локально."
    override val systemWarning = "СИСТЕМНЕ ПОПЕРЕДЖЕННЯ"
    override val renameInterception = "ПЕРЕІМЕНУВАТИ ПЕРЕХОПЛЕННЯ"
    override val enterCustomName = "Введіть нову назву:"
    override val save = "ЗБЕРЕГТИ"
    override val cancel = "СКАСУВАТИ"
    override val playingBack = "ВІДТВОРЕННЯ"
    override val settingsTitle = "НАЛАШТУВАННЯ"
    override val appLanguage = "Мова Додатка"
    override val selectLanguageDesc = "Оберіть мову інтерфейсу:"
    override val languageEn = "Англійська (English)"
    override val languageUk = "Українська"
    override val close = "ЗАКРИТИ"
    override val activeRecordingNotif = "Прослуховування та запис звуку з гарнітури..."
    override val activeRecNotifOnly = "Запис фонового звуку з мікрофона гарнітури..."
    override val activeListNotifOnly = "Режим реального часу прослуховування..."
    override val standbyNotif = "Служба Вушний Шпигун працює у фоновому режимі..."
    override val activeNotifTitle = "Вушний Шпигун Активний"
    override val fileNotFound = "Файл не знайдено на диску"
    override val playbackFailed = "Не вдалося відтворити запис: %s"
    override val micFailed = "Не вдалося запустити мікрофон: %s"
    override val micUnavail = "Мікрофон вже використовується або недоступний: %s"
}
