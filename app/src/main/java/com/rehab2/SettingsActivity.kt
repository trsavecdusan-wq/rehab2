package com.rehab2

import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.rehab2.aac.AacAssistSettings
import com.rehab2.aac.AiObservationSettings
import com.rehab2.aac.AudioDuckingSettings
import com.rehab2.aac.AacAudioPlayer
import com.rehab2.aac.AacCommunicationContext
import com.rehab2.aac.AacCommunicationContextPrefs
import com.rehab2.aac.AacLanguageResolver
import com.rehab2.aac.AacGuidedFollowUpSettings
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacContentDiagnostics
import com.rehab2.aac.AacContentBootstrap
import com.rehab2.aac.AacEditorStorage
import com.rehab2.aac.AacKeywordMatcher
import com.rehab2.aac.AacKeywordListenerSettings
import com.rehab2.aac.AacLocalJsonLoader
import com.rehab2.aac.AacProfileStore
import com.rehab2.aac.AacSampleContentCreator
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.AacStarterContentV1
import com.rehab2.aac.AacSpeechApiConfig
import com.rehab2.aac.AacSpeechCache
import com.rehab2.aac.AacSpeechCoordinator
import com.rehab2.aac.AacSpeechLoudnessSettings
import com.rehab2.aac.AacSpeechTimingSettings
import com.rehab2.aac.AacUsageStats
import com.rehab2.aac.AacVendingScenario
import com.rehab2.aac.IconSource
import com.rehab2.aac.OpenAiAacSpeechApiClient
import com.rehab2.aac.PatientProfileSettings
import com.rehab2.aac.StatusOrientationSettings
import com.rehab2.aac.WeatherSource
import java.io.File
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class CoreAacAuditItem(
    val key: String,
    val label: String,
    val color: String,
    val emojiFallback: String,
    val speechTextSl: String,
    val filename: String,
    val ids: Set<String>,
    val labels: Set<String>,
    val starterIds: Set<String> = ids
)

private data class ImageQualityAudit(
    val checkedImages: Int,
    val smallImages: Int,
    val largeImages: Int
)

private data class CoreIconVisualAudit(
    val existingCount: Int,
    val smallCount: Int,
    val missingFilenames: List<String>
)

private data class PersonPhotoAuditItem(
    val itemId: String,
    val label: String,
    val filename: String
)

private data class PeoplePhotoAudit(
    val existingCount: Int,
    val missingFilenames: List<String>,
    val lines: List<String>
)

class SettingsActivity : AppCompatActivity() {
    private enum class SettingsSection {
        PATIENT,
        COMMUNICATOR,
        SPEECH,
        ORIENTATION,
        ADVANCED
    }

    private enum class SettingsDisplayMode {
        THERAPIST,
        ADVANCED
    }

    companion object {
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_POWER_MODE = "power_mode"
        private const val PREF_POWER_ALLOWED_UNPLUG_MINUTES = "power_allowed_unplug_minutes"
        private const val PREF_POWER_WARNING_GRACE_MINUTES = "power_warning_grace_minutes"
        private const val PREF_POWER_CRITICAL_BATTERY_PERCENT = "power_critical_battery_percent"
        private const val PREF_POWER_ADMIN_BYPASS_UNTIL = "power_admin_bypass_until"
        private const val PREF_KEEP_SCREEN_ON_WHILE_CHARGING = "keep_screen_on_while_charging"
        private const val PREF_DISTANCE_TODAY_METERS = "distance_today_meters"
        private const val PREF_DISTANCE_TOTAL_METERS = "distance_total_meters"
        private const val PREF_GPS_LAST_ACCURACY_METERS = "gps_last_accuracy_meters"
        private const val PREF_GPS_LAST_SIGNAL = "gps_last_signal"
        private const val PREF_GPS_LAST_IGNORED_REASON = "gps_last_ignored_reason"
        private const val PREF_GPS_RESET_BASELINE_REQUESTED = "gps_reset_baseline_requested"
        private const val PREF_ADMIN_PIN = "admin_pin"
        private const val PREF_ADVANCED_SETTINGS_PIN = "advanced_settings_pin"
        private const val PREF_DISTANCE_WEEK_METERS = "distance_week_meters"
        private const val PREF_DISTANCE_MONTH_METERS = "distance_month_meters"
        private const val PREF_DISTANCE_YEAR_METERS = "distance_year_meters"
        // Internal legacy name; visible UI calls this therapist PIN.
        private const val DEFAULT_ADMIN_PIN = "0416"
        private const val DEFAULT_ADVANCED_SETTINGS_PIN = "1604"
        private const val DEFAULT_PROGRAMMER_PIN = "1964"

        private const val POWER_MODE_ALWAYS_ON = "ALWAYS_ON"
        private const val POWER_MODE_BATTERY_SAVER = "BATTERY_SAVER"
        private const val POWER_MODE_POWER_SLEEP = "POWER_SLEEP"

        private const val DEFAULT_POWER_MODE = POWER_MODE_ALWAYS_ON
        private const val DEFAULT_ALLOWED_UNPLUG_MINUTES = 15
        private const val DEFAULT_WARNING_GRACE_MINUTES = 5
        private const val DEFAULT_CRITICAL_BATTERY_PERCENT = 20
        private const val DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING = true
        private const val REQUEST_IMPORT_SPEECH_API_KEY = 3001
        private val SPEECH_VOICE_OPTIONS = arrayOf(
            "marin",
            "alloy",
            "ash",
            "ballad",
            "coral",
            "echo",
            "fable",
            "nova",
            "onyx",
            "sage",
            "shimmer",
            "verse",
            "cedar"
        )
        private val SPEECH_SPEED_OPTIONS = arrayOf(
            "Počasno (0.75)" to 0.75,
            "Normalno (0.88)" to 0.88,
            "Hitro (1.10)" to 1.10
        )
        private val MAIN_ICON_DELAY_OPTIONS = arrayOf(
            "0 ms" to 0L,
            "100 ms" to 100L,
            "200 ms" to 200L,
            "300 ms" to 300L,
            "500 ms" to 500L,
            "700 ms" to 700L,
            "1000 ms" to 1000L
        )
        private val SUB_ICON_DELAY_OPTIONS = arrayOf(
            "0 ms" to 0L,
            "100 ms" to 100L,
            "200 ms" to 200L,
            "300 ms" to 300L,
            "500 ms" to 500L,
            "700 ms" to 700L,
            "1000 ms" to 1000L,
            "1500 ms" to 1500L,
            "2000 ms" to 2000L
        )
        private val AUTO_SENTENCE_DELAY_OPTIONS = arrayOf(
            "OFF" to null,
            "1000 ms" to 1000L,
            "1500 ms" to 1500L,
            "2000 ms" to 2000L,
            "3000 ms" to 3000L,
            "4000 ms" to 4000L,
            "5000 ms" to 5000L
        )
        private val PARTIAL_SENTENCE_AUTO_RETURN_OPTIONS = arrayOf(
            "5s" to 5000L,
            "10s" to 10000L,
            "15s" to 15000L,
            "20s" to 20000L,
            "30s" to 30000L
        )
        private const val PREF_AAC_GRID_SIZE = "aac_grid_size"
        private const val DEFAULT_AAC_GRID_SIZE = 3
        private val AAC_GRID_SIZE_OPTIONS = arrayOf(3, 4, 5)
        private const val PREF_AAC_PERSISTENT_TOP_ROW_ENABLED = "aac_persistent_top_row_enabled"
        private const val PREF_AAC_PERSISTENT_TOP_ROW_COUNT = "aac_persistent_top_row_count"
        private const val PREF_AAC_PERSISTENT_TOP_ROW_ITEM_IDS = "aac_persistent_top_row_item_ids"
        private const val DEFAULT_AAC_PERSISTENT_TOP_ROW_COUNT = 5
        private val AAC_PERSISTENT_TOP_ROW_COUNT_OPTIONS = arrayOf(3, 4, 5)
        private val DEFAULT_AAC_PERSISTENT_TOP_ROW_ITEM_IDS = listOf("no", "yes", "dont_understand", "thank_you", "sorry")
        private val CORE_AAC_AUDIT_ITEMS = listOf(
            CoreAacAuditItem("da", "DA", "#4CAF50", "✅", "Da.", "core_yes.png", setOf("yes", "quick_yes", "da"), setOf("DA"), setOf("yes")),
            CoreAacAuditItem("ne", "NE", "#F44336", "❌", "Ne.", "core_no.png", setOf("no", "quick_no", "ne"), setOf("NE"), setOf("no")),
            CoreAacAuditItem("ne_razumem", "NE RAZUMEM", "#FF9800", "😕", "Ne razumem.", "core_dont_understand.png", setOf("dont_understand", "ne_razumem"), setOf("NE RAZUMEM")),
            CoreAacAuditItem("hvala", "HVALA", "#9C27B0", "🙏", "Hvala.", "core_thank_you.png", setOf("thank_you", "hvala"), setOf("HVALA")),
            CoreAacAuditItem("pomoc", "POMOČ", "#D32F2F", "🆘", "Prosim, pomagajte mi.", "core_help.png", setOf("help", "pomoc"), setOf("POMOČ", "POMAGAJ MI")),
            CoreAacAuditItem("zejna", "ŽEJNA SEM", "#2196F3", "💧", "Žejna sem.", "core_thirsty.png", setOf("thirsty", "zejna"), setOf("ŽEJNA SEM", "ŽEJNA")),
            CoreAacAuditItem("lacna", "LAČNA SEM", "#FF5722", "🍽️", "Lačna sem.", "core_hungry.png", setOf("hungry", "lacna"), setOf("LAČNA SEM", "LAČNA")),
            CoreAacAuditItem("boli", "BOLI ME", "#E91E63", "🤕", "Boli me.", "core_pain.png", setOf("pain", "boli"), setOf("BOLI ME")),
            CoreAacAuditItem("wc", "WC", "#3F51B5", "🚽", "Moram na stranišče.", "core_wc.png", setOf("wc"), setOf("WC")),
            CoreAacAuditItem("utrujena", "UTRUJENA SEM", "#9E9E9E", "😴", "Utrujena sem.", "core_tired.png", setOf("tired", "utrujena"), setOf("UTRUJENA SEM", "UTRUJENA")),
            CoreAacAuditItem("rada_bi", "RADA BI", "#FFC107", "❤️", "Rada bi.", "core_i_want.png", setOf("i_want", "rada_bi"), setOf("RADA BI")),
            CoreAacAuditItem("nocem", "NOČEM", "#B71C1C", "🚫", "Nočem.", "core_dont_want.png", setOf("dont_want", "nocem"), setOf("NOČEM")),
            CoreAacAuditItem("druzina", "DRUŽINA", "#8BC34A", "👨‍👩‍👧", "Rada bi družino.", "core_family.png", setOf("family_group", "druzina"), setOf("DRUŽINA")),
            CoreAacAuditItem("poklici", "POKLIČI", "#00BCD4", "📞", "Prosim, pokličite.", "core_call.png", setOf("call", "poklici"), setOf("POKLIČI")),
            CoreAacAuditItem("sporocilo", "SPOROČILO", "#673AB7", "💬", "Rada bi poslati sporočilo.", "core_message.png", setOf("message", "sporocilo"), setOf("SPOROČILO")),
            CoreAacAuditItem("pijaca", "PIJAČA", "#03A9F4", "🥤", "Rada bi pijačo.", "core_drink.png", setOf("drink", "pijaca"), setOf("PIJAČA")),
            CoreAacAuditItem("hrana", "HRANA", "#FF9800", "🍲", "Rada bi hrano.", "core_food.png", setOf("food", "hrana"), setOf("HRANA")),
            CoreAacAuditItem("pocutje", "POČUTJE", "#FFEB3B", "😐", "Rada bi povedati, kako se počutim.", "core_feeling.png", setOf("feeling", "pocutje"), setOf("POČUTJE")),
            CoreAacAuditItem("nega", "NEGA", "#B2DFDB", "🧴", "Potrebujem pomoč pri negi.", "core_care.png", setOf("care", "nega"), setOf("NEGA")),
            CoreAacAuditItem("zdravje", "ZDRAVJE", "#F44336", "🏥", "Potrebujem zdravstveno pomoč.", "core_health.png", setOf("health", "zdravje"), setOf("ZDRAVJE"))
        )
        private val PEOPLE_PHOTO_AUDIT_ITEMS = listOf(
            PersonPhotoAuditItem("person_dusan", "DUŠAN", "person_dusan.jpg"),
            PersonPhotoAuditItem("person_zana", "ŽANA", "person_zana.jpg"),
            PersonPhotoAuditItem("person_franc", "FRANC", "person_franc.jpg"),
            PersonPhotoAuditItem("person_inna", "INNA", "person_inna.jpg"),
            PersonPhotoAuditItem("person_julija", "JULIJA", "person_julija.jpg"),
            PersonPhotoAuditItem("person_oksana", "OKSANA", "person_oksana.jpg"),
            PersonPhotoAuditItem("person_sergej", "SERGEJ", "person_sergej.jpg")
        )
        private val TIME_PACK_AUDIT_ITEMS = listOf(
            "today" to "DANES",
            "tomorrow" to "JUTRI",
            "yesterday" to "VČERAJ",
            "now" to "ZDAJ",
            "later" to "KASNEJE",
            "morning" to "ZJUTRAJ",
            "afternoon" to "POPOLDNE",
            "evening" to "ZVEČER",
            "night" to "PONOČI"
        )
        private val PLACE_PACK_AUDIT_ITEMS = listOf(
            "place_group" to "KRAJ",
            "room" to "SOBA",
            "terrace" to "TERASA",
            "bathroom" to "KOPALNICA",
            "dining_room" to "JEDILNICA",
            "home" to "DOMOV",
            "outside" to "ZUNAJ",
            "inside" to "NOTRI",
            "therapy" to "TERAPIJA"
        )
        private val ACTIVITY_PACK_AUDIT_ITEMS = listOf(
            "activity_group" to "KAJ BI DELALA?",
            "rest" to "POČITEK",
            "music" to "GLASBA",
            "tv" to "TELEVIZIJA",
            "walk" to "SPREHOD",
            "visit" to "OBISK",
            "therapy" to "TERAPIJA"
        )
        private val CARE_PACK_AUDIT_ITEMS = listOf(
            "care" to "NEGA",
            "washing_help" to "UMIVANJE",
            "dressing_help" to "PREOBLAČENJE",
            "bed" to "POSTELJA",
            "wheelchair" to "VOZIČEK",
            "blanket" to "ODEJA",
            "pillow" to "BLAZINA",
            "change_position" to "SPREMEMBA POLOŽAJA",
            "turn_left" to "OBRNI LEVO",
            "turn_right" to "OBRNI DESNO",
            "sit_up" to "DVIGNI ME",
            "lie_down" to "POLOŽI ME"
        )

        // Faza 1: najvec manjkajocih ikon, prikazanih v diagnostiki; ostalo se sesteje.
        private const val MAX_MISSING_ICONS_SHOWN = 15
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var btnPowerOff: Button
    private lateinit var btnPowerWarning: Button
    private lateinit var btnPowerSleep: Button
    private lateinit var btnKeepScreenOnWhileCharging: Button
    private lateinit var txtAllowedUnplugValue: TextView
    private lateinit var txtWarningGraceValue: TextView
    private lateinit var txtCriticalBatteryValue: TextView
    private lateinit var txtPowerStatusValue: TextView
    private lateinit var txtTodayDistanceValue: TextView
    private lateinit var txtTotalDistanceValue: TextView
    private lateinit var txtGpsSignalValue: TextView
    private lateinit var txtGpsAccuracyValue: TextView
    private lateinit var txtGpsIgnoredReasonValue: TextView
    private lateinit var btnResetGpsStatistics: Button
    private lateinit var txtSpeechApiStatus: TextView
    private lateinit var editSpeechApiKey: EditText
    private lateinit var editSpeechApiBaseUrl: EditText
    private lateinit var editSpeechApiModel: EditText
    private lateinit var editSpeechApiVoice: EditText
    private lateinit var editSpeechApiSpeed: EditText
    private lateinit var switchSingleIconSpeech: SwitchCompat
    private lateinit var editSingleIconDelay: EditText
    private lateinit var editSubIconDelay: EditText
    private lateinit var switchFastCompositionSkipLastIcon: SwitchCompat
    private lateinit var switchAutoSentenceSpeech: SwitchCompat
    private lateinit var editAutoSentenceDelay: EditText
    private lateinit var switchReturnToRootAfterSentence: SwitchCompat
    private lateinit var switchClearSentenceAfterSentence: SwitchCompat
    private lateinit var switchPartialSentenceAutoReturn: SwitchCompat
    private lateinit var editPartialSentenceAutoReturnDelay: EditText
    private lateinit var txtAacGridSizeStatus: TextView
    private lateinit var editAacGridSize: EditText
    private lateinit var switchPersistentTopRowEnabled: SwitchCompat
    private lateinit var txtPersistentTopRowStatus: TextView
    private lateinit var editPersistentTopRowCount: EditText
    private lateinit var txtAacPatientAuditStatus: TextView
    private lateinit var switchGuidedFollowUpEnabled: SwitchCompat
    private lateinit var switchVendingNumberDisplayEnabled: SwitchCompat
    private lateinit var switchSpeakDigitsSeparatelyEnabled: SwitchCompat
    private lateinit var txtActiveAacProfileStatus: TextView
    private lateinit var editActiveAacProfile: EditText
    private lateinit var txtAacCommunicationContextStatus: TextView
    private lateinit var editAacCommunicationContext: EditText
    private lateinit var switchRealWorldHelpersEnabled: SwitchCompat
    private lateinit var editVendingCodes: EditText
    private lateinit var txtAacAssistStatus: TextView
    private lateinit var switchAacAssistShowSuggestions: SwitchCompat
    private lateinit var editAacAssistInfo: EditText
    private lateinit var txtKeywordListenerStatus: TextView
    private lateinit var editKeywordListenerInfo: EditText
    private lateinit var editKeywordMatcherInput: EditText
    private lateinit var txtKeywordMatcherResult: TextView
    private lateinit var txtAiObservationStatus: TextView
    private lateinit var editAiObservationInfo: EditText
    private lateinit var txtAudioDuckingStatus: TextView
    private lateinit var switchAudioDuckingEnabled: SwitchCompat
    private lateinit var editAudioDuckingPercent: EditText
    private lateinit var txtAacSpeechLoudnessStatus: TextView
    private lateinit var editAacSpeechLoudnessGain: EditText
    private lateinit var txtStatusOrientationStatus: TextView
    private lateinit var switchStatusOrientationEnabled: SwitchCompat
    private lateinit var switchStatusOrientationGreeting: SwitchCompat
    private lateinit var switchStatusOrientationDate: SwitchCompat
    private lateinit var switchStatusOrientationTime: SwitchCompat
    private lateinit var switchStatusOrientationWeather: SwitchCompat
    private lateinit var editStatusWeatherSourceName: EditText
    private lateinit var editStatusWeatherSourceUrl: EditText
    private lateinit var editStatusOrientationInfo: EditText
    private lateinit var txtPatientProfileStatus: TextView
    private lateinit var editPatientFirstName: EditText
    private lateinit var editPatientLastName: EditText
    private lateinit var editPatientGender: EditText
    private lateinit var editPatientAge: EditText
    private lateinit var editPatientBirthDate: EditText
    private lateinit var editPatientHomeTown: EditText
    private lateinit var editPatientCountry: EditText
    private lateinit var editPatientMainLanguage: EditText
    private lateinit var editPatientCaregiverContact: EditText
    private lateinit var editPatientTherapistContact: EditText
    private lateinit var editPatientShortDescription: EditText
    private var speechApiTestPlayer: MediaPlayer? = null
    private var audioDuckingTestPlayer: AacAudioPlayer? = null
    private var speechLoudnessTestPlayer: AacAudioPlayer? = null
    private var settingsDisplayMode = SettingsDisplayMode.THERAPIST
    private var latestBatteryPercent: Int? = null
    private var latestPluggedIn = false
    private var isBatteryReceiverRegistered = false
    private var activeSettingsSection: SettingsSection? = null
    private val gpsDiagnosticsRefreshHandler = Handler(Looper.getMainLooper())
    private val powerFeedbackHandler = Handler(Looper.getMainLooper())
    private val gpsDiagnosticsRefreshRunnable = object : Runnable {
        override fun run() {
            refreshGpsDiagnosticsSection()
            refreshStatisticsSection()
            gpsDiagnosticsRefreshHandler.postDelayed(this, 1500L)
        }
    }
    private var pendingPowerStatusRefresh: Runnable? = null
    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatterySnapshot(intent)
            refreshPowerSection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)

        btnPowerOff = findViewById(R.id.btnPowerModeOff)
        btnPowerWarning = findViewById(R.id.btnPowerModeWarning)
        btnPowerSleep = findViewById(R.id.btnPowerModeSleep)
        btnKeepScreenOnWhileCharging = findViewById(R.id.btnKeepScreenOnWhileCharging)
        txtAllowedUnplugValue = findViewById(R.id.txtAllowedUnplugValue)
        txtWarningGraceValue = findViewById(R.id.txtWarningGraceValue)
        txtCriticalBatteryValue = findViewById(R.id.txtCriticalBatteryValue)
        txtPowerStatusValue = findViewById(R.id.txtPowerStatusValue)
        txtTodayDistanceValue = findViewById(R.id.txtTodayDistanceValue)
        txtTotalDistanceValue = findViewById(R.id.txtTotalDistanceValue)
        txtGpsSignalValue = findViewById(R.id.txtGpsSignalValue)
        txtGpsAccuracyValue = findViewById(R.id.txtGpsAccuracyValue)
        txtGpsIgnoredReasonValue = findViewById(R.id.txtGpsIgnoredReasonValue)
        btnResetGpsStatistics = findViewById(R.id.btnResetGpsStatistics)
        txtSpeechApiStatus = findViewById(R.id.txtSpeechApiStatus)
        editSpeechApiKey = findViewById(R.id.editSpeechApiKey)
        editSpeechApiBaseUrl = findViewById(R.id.editSpeechApiBaseUrl)
        editSpeechApiModel = findViewById(R.id.editSpeechApiModel)
        editSpeechApiVoice = findViewById(R.id.editSpeechApiVoice)
        editSpeechApiSpeed = findViewById(R.id.editSpeechApiSpeed)
        switchSingleIconSpeech = findViewById(R.id.switchSingleIconSpeech)
        editSingleIconDelay = findViewById(R.id.editSingleIconDelay)
        editSubIconDelay = findViewById(R.id.editSubIconDelay)
        switchFastCompositionSkipLastIcon = findViewById(R.id.switchFastCompositionSkipLastIcon)
        switchAutoSentenceSpeech = findViewById(R.id.switchAutoSentenceSpeech)
        editAutoSentenceDelay = findViewById(R.id.editAutoSentenceDelay)
        switchReturnToRootAfterSentence = findViewById(R.id.switchReturnToRootAfterSentence)
        switchClearSentenceAfterSentence = findViewById(R.id.switchClearSentenceAfterSentence)
        switchPartialSentenceAutoReturn = findViewById(R.id.switchPartialSentenceAutoReturn)
        editPartialSentenceAutoReturnDelay = findViewById(R.id.editPartialSentenceAutoReturnDelay)
        txtAacGridSizeStatus = findViewById(R.id.txtAacGridSizeStatus)
        editAacGridSize = findViewById(R.id.editAacGridSize)
        switchPersistentTopRowEnabled = findViewById(R.id.switchPersistentTopRowEnabled)
        txtPersistentTopRowStatus = findViewById(R.id.txtPersistentTopRowStatus)
        editPersistentTopRowCount = findViewById(R.id.editPersistentTopRowCount)
        txtAacPatientAuditStatus = findViewById(R.id.txtAacPatientAuditStatus)
        switchGuidedFollowUpEnabled = findViewById(R.id.switchGuidedFollowUpEnabled)
        switchVendingNumberDisplayEnabled = findViewById(R.id.switchVendingNumberDisplayEnabled)
        switchSpeakDigitsSeparatelyEnabled = findViewById(R.id.switchSpeakDigitsSeparatelyEnabled)
        txtActiveAacProfileStatus = findViewById(R.id.txtActiveAacProfileStatus)
        editActiveAacProfile = findViewById(R.id.editActiveAacProfile)
        txtAacCommunicationContextStatus = findViewById(R.id.txtAacCommunicationContextStatus)
        editAacCommunicationContext = findViewById(R.id.editAacCommunicationContext)
        switchRealWorldHelpersEnabled = findViewById(R.id.switchRealWorldHelpersEnabled)
        editVendingCodes = findViewById(R.id.editVendingCodes)
        txtAacAssistStatus = findViewById(R.id.txtAacAssistStatus)
        switchAacAssistShowSuggestions = findViewById(R.id.switchAacAssistShowSuggestions)
        editAacAssistInfo = findViewById(R.id.editAacAssistInfo)
        txtKeywordListenerStatus = findViewById(R.id.txtKeywordListenerStatus)
        editKeywordListenerInfo = findViewById(R.id.editKeywordListenerInfo)
        editKeywordMatcherInput = findViewById(R.id.editKeywordMatcherInput)
        txtKeywordMatcherResult = findViewById(R.id.txtKeywordMatcherResult)
        txtAiObservationStatus = findViewById(R.id.txtAiObservationStatus)
        editAiObservationInfo = findViewById(R.id.editAiObservationInfo)
        txtAudioDuckingStatus = findViewById(R.id.txtAudioDuckingStatus)
        switchAudioDuckingEnabled = findViewById(R.id.switchAudioDuckingEnabled)
        editAudioDuckingPercent = findViewById(R.id.editAudioDuckingPercent)
        txtAacSpeechLoudnessStatus = findViewById(R.id.txtAacSpeechLoudnessStatus)
        editAacSpeechLoudnessGain = findViewById(R.id.editAacSpeechLoudnessGain)
        txtStatusOrientationStatus = findViewById(R.id.txtStatusOrientationStatus)
        switchStatusOrientationEnabled = findViewById(R.id.switchStatusOrientationEnabled)
        switchStatusOrientationGreeting = findViewById(R.id.switchStatusOrientationGreeting)
        switchStatusOrientationDate = findViewById(R.id.switchStatusOrientationDate)
        switchStatusOrientationTime = findViewById(R.id.switchStatusOrientationTime)
        switchStatusOrientationWeather = findViewById(R.id.switchStatusOrientationWeather)
        editStatusWeatherSourceName = findViewById(R.id.editStatusWeatherSourceName)
        editStatusWeatherSourceUrl = findViewById(R.id.editStatusWeatherSourceUrl)
        editStatusOrientationInfo = findViewById(R.id.editStatusOrientationInfo)
        txtPatientProfileStatus = findViewById(R.id.txtPatientProfileStatus)
        editPatientFirstName = findViewById(R.id.editPatientFirstName)
        editPatientLastName = findViewById(R.id.editPatientLastName)
        editPatientGender = findViewById(R.id.editPatientGender)
        editPatientAge = findViewById(R.id.editPatientAge)
        editPatientBirthDate = findViewById(R.id.editPatientBirthDate)
        editPatientHomeTown = findViewById(R.id.editPatientHomeTown)
        editPatientCountry = findViewById(R.id.editPatientCountry)
        editPatientMainLanguage = findViewById(R.id.editPatientMainLanguage)
        editPatientCaregiverContact = findViewById(R.id.editPatientCaregiverContact)
        editPatientTherapistContact = findViewById(R.id.editPatientTherapistContact)
        editPatientShortDescription = findViewById(R.id.editPatientShortDescription)
        findViewById<Button>(R.id.btnBackSettings).setOnClickListener {
            if (activeSettingsSection == null) {
                finish()
            } else {
                showSettingsHub()
            }
        }
        findViewById<Button>(R.id.btnExitSettingsToMain).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            finish()
        }

        findViewById<Button>(R.id.btnSettingsModeTherapist).setOnClickListener {
            setSettingsDisplayMode(SettingsDisplayMode.THERAPIST)
        }

        findViewById<Button>(R.id.btnSettingsModeAdvanced).setOnClickListener {
            confirmAdvancedSettingsMode()
        }

        findViewById<Button>(R.id.btnSettingsHubPatient).setOnClickListener {
            showSettingsSection(SettingsSection.PATIENT)
        }

        findViewById<Button>(R.id.btnSettingsHubCommunicator).setOnClickListener {
            showSettingsSection(SettingsSection.COMMUNICATOR)
        }

        findViewById<Button>(R.id.btnSettingsHubIcons).setOnClickListener {
            startActivity(Intent(this, AacEditorActivity::class.java))
        }

        findViewById<Button>(R.id.btnSettingsHubSpeech).setOnClickListener {
            showSettingsSection(SettingsSection.SPEECH)
        }

        findViewById<Button>(R.id.btnSettingsHubVideoMessages).setOnClickListener {
            showModulePlaceholder("VIDEO IN SPOROČILA")
        }

        findViewById<Button>(R.id.btnSettingsHubOrientation).setOnClickListener {
            showSettingsSection(SettingsSection.ORIENTATION)
        }

        findViewById<Button>(R.id.btnSettingsHubBackup).setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSettingsHubAdvanced).setOnClickListener {
            showSettingsSection(SettingsSection.ADVANCED)
        }

        findViewById<Button>(R.id.btnRefreshAacPatientAudit).setOnClickListener {
            refreshAacPatientAuditPanel()
        }

        findViewById<Button>(R.id.btnOpenAacEditorFromAudit).setOnClickListener {
            startActivity(Intent(this, AacEditorActivity::class.java))
        }

        findViewById<Button>(R.id.btnAddMissingCoreAacIcons).setOnClickListener {
            confirmRepairMissingCoreAacIcons()
        }

        findViewById<Button>(R.id.btnShowCoreIconFilenames).setOnClickListener {
            showCoreIconFilenamesDialog()
        }

        findViewById<Button>(R.id.btnChangeAdvancedSettingsPin).setOnClickListener {
            showChangeAdvancedSettingsPinDialog()
        }

        findViewById<Button>(R.id.btnChangeAdminPin).setOnClickListener {
            showChangeAdminPinDialog()
        }

        findViewById<Button>(R.id.btnRadioSettings).setOnClickListener {
            startActivity(Intent(this, RadioSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnBackupSettings).setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAacPackSettings).setOnClickListener {
            startActivity(Intent(this, AacPackSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAacEditor).setOnClickListener {
            startActivity(Intent(this, AacEditorActivity::class.java))
        }

        findViewById<Button>(R.id.btnAacSettings).setOnClickListener {
            startActivity(Intent(this, AacPackSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnSettingsModuleVideoCalls).setOnClickListener {
            showModulePlaceholder("VIDEO KLICI")
        }

        findViewById<Button>(R.id.btnSettingsModuleMessages).setOnClickListener {
            showModulePlaceholder("SPOROČILA")
        }

        findViewById<Button>(R.id.btnSettingsModuleGallery).setOnClickListener {
            showModulePlaceholder("GALERIJA")
        }

        findViewById<Button>(R.id.btnSettingsModuleMirror).setOnClickListener {
            showModulePlaceholder("OGLEDALO")
        }

        findViewById<Button>(R.id.btnSettingsModuleSystem).setOnClickListener {
            scrollToSettingsSection(R.id.sectionSystemSettings)
        }

        findViewById<Button>(R.id.btnCreateSampleAacPack).setOnClickListener {
            createSampleAacPack()
        }

        findViewById<Button>(R.id.btnCheckAacFiles).setOnClickListener {
            showAacContentDiagnostics()
        }

        findViewById<Button>(R.id.btnStatusSettings).setOnClickListener {
            scrollToSettingsSection(R.id.sectionSpeechApiSettings)
        }

        findViewById<Button>(R.id.btnSaveSpeechApiSettings).setOnClickListener {
            saveSpeechApiSettings(showSavedToast = true)
        }

        findViewById<Button>(R.id.btnTestSpeechApi).setOnClickListener {
            if (saveSpeechApiSettings(showSavedToast = false)) {
                testSpeechApi()
            }
        }

        findViewById<Button>(R.id.btnImportSpeechApiKey).setOnClickListener {
            openSpeechApiKeyImport()
        }
        editSpeechApiVoice.setOnClickListener {
            showSpeechVoicePicker()
        }
        editSpeechApiSpeed.setOnClickListener {
            showSpeechSpeedPicker()
        }
        editSingleIconDelay.setOnClickListener {
            showMainIconDelayPicker()
        }
        editSubIconDelay.setOnClickListener {
            showSubIconDelayPicker()
        }
        editPatientGender.setOnClickListener {
            showPatientGenderPicker()
        }
        editAutoSentenceDelay.setOnClickListener {
            showAutoSentenceDelayPicker()
        }
        editPartialSentenceAutoReturnDelay.setOnClickListener {
            showPartialSentenceAutoReturnDelayPicker()
        }
        editAacGridSize.setOnClickListener {
            showAacGridSizePicker()
        }
        editPersistentTopRowCount.setOnClickListener {
            showPersistentTopRowCountPicker()
        }
        editActiveAacProfile.setOnClickListener {
            showAacProfilePicker()
        }
        editAacCommunicationContext.setOnClickListener {
            showAacProfilePicker()
        }
        editVendingCodes.setOnClickListener {
            showVendingCodePicker()
        }
        editAudioDuckingPercent.setOnClickListener {
            showAudioDuckingPercentPicker()
        }
        editAacSpeechLoudnessGain.setOnClickListener {
            showAacSpeechLoudnessPicker()
        }
        findViewById<Button>(R.id.btnTestAacSpeechLoudness).setOnClickListener {
            testAacSpeechLoudness()
        }
        findViewById<Button>(R.id.btnTestAudioDucking).setOnClickListener {
            testAudioDucking()
        }
        editStatusWeatherSourceName.setOnClickListener {
            showStatusWeatherSourcePicker()
        }
        findViewById<Button>(R.id.btnSaveStatusWeatherSource).setOnClickListener {
            saveStatusWeatherSource()
        }
        findViewById<Button>(R.id.btnSavePatientProfile).setOnClickListener {
            savePatientProfileSettings()
        }
        findViewById<Button>(R.id.btnPatientSetupWizard).setOnClickListener {
            startActivity(Intent(this, PatientSetupWizardActivity::class.java))
        }
        findViewById<Button>(R.id.btnKeywordMatcherTest).setOnClickListener {
            runKeywordMatcherTest()
        }
        bindSpeechTimingSwitchListeners()
        bindPersistentTopRowSwitchListener()

        btnPowerOff.setOnClickListener {
            setPowerMode(POWER_MODE_ALWAYS_ON)
        }

        btnPowerWarning.setOnClickListener {
            setPowerMode(POWER_MODE_BATTERY_SAVER)
        }

        btnPowerSleep.setOnClickListener {
            setPowerMode(POWER_MODE_POWER_SLEEP)
        }

        btnKeepScreenOnWhileCharging.setOnClickListener {
            val enabled = prefs.getBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING)
            prefs.edit().putBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, !enabled).apply()
            refreshPowerSection()
            applyKeepScreenOnWhileCharging()
        }

        findViewById<TextView>(R.id.btnAllowedUnplugMinus).setOnClickListener {
            adjustIntPreference(PREF_POWER_ALLOWED_UNPLUG_MINUTES, -1, 1, 180, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        }

        findViewById<TextView>(R.id.btnAllowedUnplugPlus).setOnClickListener {
            adjustIntPreference(PREF_POWER_ALLOWED_UNPLUG_MINUTES, 1, 1, 180, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        }

        findViewById<TextView>(R.id.btnWarningGraceMinus).setOnClickListener {
            adjustIntPreference(PREF_POWER_WARNING_GRACE_MINUTES, -1, 1, 60, DEFAULT_WARNING_GRACE_MINUTES)
        }

        findViewById<TextView>(R.id.btnWarningGracePlus).setOnClickListener {
            adjustIntPreference(PREF_POWER_WARNING_GRACE_MINUTES, 1, 1, 60, DEFAULT_WARNING_GRACE_MINUTES)
        }

        findViewById<TextView>(R.id.btnCriticalBatteryMinus).setOnClickListener {
            adjustIntPreference(PREF_POWER_CRITICAL_BATTERY_PERCENT, -1, 5, 50, DEFAULT_CRITICAL_BATTERY_PERCENT)
        }

        findViewById<TextView>(R.id.btnCriticalBatteryPlus).setOnClickListener {
            adjustIntPreference(PREF_POWER_CRITICAL_BATTERY_PERCENT, 1, 5, 50, DEFAULT_CRITICAL_BATTERY_PERCENT)
        }

        btnResetGpsStatistics.setOnClickListener {
            showAdminPinDialog {
                prefs.edit()
                    .putLong(PREF_DISTANCE_TODAY_METERS, 0L)
                    .putLong(PREF_DISTANCE_TOTAL_METERS, 0L)
                    .putBoolean(PREF_GPS_RESET_BASELINE_REQUESTED, true)
                    .apply()
                refreshStatisticsSection()
                refreshGpsDiagnosticsSection()
                Toast.makeText(this, R.string.gps_statistics_reset_done, Toast.LENGTH_SHORT).show()
            }
        }

        refreshBatterySnapshot()
        refreshPowerSection()
        refreshStatisticsSection()
        refreshGpsDiagnosticsSection()
        refreshSpeechApiSection()
        refreshSpeechTimingSection()
        refreshAacPatientAuditPanel()
        refreshAacGridSizeSection()
        refreshPersistentTopRowSection()
        refreshGuidedFollowUpSection()
        refreshAacCommunicationContextSection()
        refreshVendingCodesSection()
        refreshAacAssistSection()
        refreshKeywordListenerSection()
        refreshAiObservationSection()
        refreshAudioDuckingSection()
        refreshAacSpeechLoudnessSection()
        refreshStatusOrientationSection()
        refreshPatientProfileSection()
        bindAacAssistSwitchListeners()
        bindAudioDuckingSwitchListener()
        bindStatusOrientationSwitchListeners()
        applyKeepScreenOnWhileCharging()
        showSettingsHub()
    }

    override fun onBackPressed() {
        if (activeSettingsSection != null) {
            showSettingsHub()
            return
        }
        super.onBackPressed()
    }

    private fun showSettingsHub() {
        activeSettingsSection = null
        val hubIds = mutableSetOf(
            R.id.txtSettingsTitle,
            R.id.btnExitSettingsToMain,
            R.id.btnSettingsModeTherapist,
            R.id.btnSettingsModeAdvanced,
            R.id.sectionHub,
            R.id.btnSettingsHubPatient,
            R.id.btnSettingsHubCommunicator,
            R.id.btnSettingsHubIcons,
            R.id.btnSettingsHubSpeech,
            R.id.btnSettingsHubVideoMessages,
            R.id.btnSettingsHubOrientation,
            R.id.btnSettingsHubBackup
        )
        if (settingsDisplayMode == SettingsDisplayMode.ADVANCED) {
            hubIds += R.id.btnSettingsHubAdvanced
        }
        settingsContentView()?.let { content ->
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index)
                child.visibility = if (child.id in hubIds) View.VISIBLE else View.GONE
            }
        }
        findViewById<TextView>(R.id.txtSettingsSectionHelper).text = ""
        updateSettingsModeButtons()
        scrollSettingsToTop()
    }

    private fun showSettingsSection(section: SettingsSection) {
        if (section == SettingsSection.ADVANCED && settingsDisplayMode != SettingsDisplayMode.ADVANCED) {
            showSettingsHub()
            return
        }
        activeSettingsSection = section
        val content = settingsContentView() ?: return
        for (index in 0 until content.childCount) {
            content.getChildAt(index).visibility = View.GONE
        }

        setSettingsChildVisible(R.id.txtSettingsTitle)
        setSettingsChildVisible(R.id.btnBackSettings)
        setSettingsChildVisible(R.id.btnExitSettingsToMain)
        setSettingsChildVisible(R.id.txtSettingsSectionHelper)
        setSettingsChildVisible(R.id.btnSettingsModeTherapist)
        setSettingsChildVisible(R.id.btnSettingsModeAdvanced)
        findViewById<Button>(R.id.btnBackSettings).text = "NAZAJ NA NASTAVITVE"
        findViewById<TextView>(R.id.txtSettingsSectionHelper).text = when (section) {
            SettingsSection.PATIENT -> "Tukaj nastavite podatke pacientke."
            SettingsSection.COMMUNICATOR -> "Tukaj nastavite, kako se prikazujejo in obnašajo komunikacijske ikone."
            SettingsSection.SPEECH -> "Tukaj nastavite, kako glasno in hitro aplikacija govori."
            SettingsSection.ORIENTATION -> "Tukaj nastavite, kaj aplikacija pove ob pritisku na datum."
            SettingsSection.ADVANCED -> "Ta del je za skrbnika ali razvijalca."
        }

        when (section) {
            SettingsSection.PATIENT -> {
                setSettingsRangeVisible(content, R.id.txtPatientProfileTitle, R.id.btnSavePatientProfile)
            }
            SettingsSection.COMMUNICATOR -> {
                setSettingsRangeVisible(content, R.id.subgroupCommunicatorAudit, R.id.switchSpeakDigitsSeparatelyEnabled)
                setSettingsRangeVisible(content, R.id.subgroupCommunicatorRealWorld, R.id.editVendingCodes)
                setSettingsRangeVisible(content, R.id.subgroupCommunicatorProfile, R.id.switchRealWorldHelpersEnabled)
            }
            SettingsSection.SPEECH -> {
                setSettingsRangeVisible(content, R.id.sectionSpeechApiSettings, R.id.editPartialSentenceAutoReturnDelay)
                setSettingsRangeVisible(content, R.id.subgroupSpeechVolume, R.id.btnTestAacSpeechLoudness)
                setSettingsChildVisible(R.id.btnSaveSpeechApiSettings)
                setSettingsChildVisible(R.id.btnTestSpeechApi)
                setSettingsChildVisible(R.id.btnImportSpeechApiKey)
            }
            SettingsSection.ORIENTATION -> {
                setSettingsRangeVisible(content, R.id.txtStatusOrientationTitle, R.id.editStatusOrientationInfo)
            }
            SettingsSection.ADVANCED -> {
                setSettingsRangeVisible(content, R.id.sectionAdvancedSettings, R.id.btnCheckAacFiles)
                setSettingsRangeVisible(content, R.id.subgroupAdvancedAi, R.id.editAiObservationInfo)
                setSettingsRangeVisible(content, R.id.sectionSystemSettings, R.id.btnResetGpsStatistics)
            }
        }
        applyTherapistModeVisibility()
        updateSettingsModeButtons()
        scrollSettingsToTop()
    }

    private fun setSettingsDisplayMode(mode: SettingsDisplayMode) {
        settingsDisplayMode = mode
        val section = activeSettingsSection
        if (section == null || (section == SettingsSection.ADVANCED && mode != SettingsDisplayMode.ADVANCED)) {
            showSettingsHub()
        } else {
            showSettingsSection(section)
        }
    }

    private fun confirmAdvancedSettingsMode() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = "PIN"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        AlertDialog.Builder(this)
            .setTitle("PIN ZA NAPREDNE NASTAVITVE")
            .setMessage("Ta del je namenjen skrbniku ali razvijalcu.")
            .setView(input)
            .setPositiveButton("NADALJUJ") { _, _ ->
                val enteredPin = input.text?.toString().orEmpty()
                if (enteredPin == currentAdvancedSettingsPin() || isProgrammerPin(enteredPin)) {
                    setSettingsDisplayMode(SettingsDisplayMode.ADVANCED)
                } else {
                    setSettingsDisplayMode(SettingsDisplayMode.THERAPIST)
                    Toast.makeText(this, "Napačen PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("PREKLIČI") { _, _ ->
                setSettingsDisplayMode(SettingsDisplayMode.THERAPIST)
            }
            .show()
    }

    private fun currentAdvancedSettingsPin(): String {
        return prefs.getString(PREF_ADVANCED_SETTINGS_PIN, DEFAULT_ADVANCED_SETTINGS_PIN)
            .orEmpty()
            .ifBlank { DEFAULT_ADVANCED_SETTINGS_PIN }
    }

    private fun currentAdminPin(): String {
        return prefs.getString(PREF_ADMIN_PIN, DEFAULT_ADMIN_PIN)
            .orEmpty()
            .ifBlank { DEFAULT_ADMIN_PIN }
    }

    private fun showChangeAdvancedSettingsPinDialog() {
        showAdvancedPinInputDialog(
            title = "TRENUTNI PIN",
            message = "Vpišite trenutni PIN za napredni način."
        ) { currentPin ->
            if (currentPin != currentAdvancedSettingsPin() && !isProgrammerPin(currentPin)) {
                Toast.makeText(this, "Napačen trenutni PIN", Toast.LENGTH_SHORT).show()
                return@showAdvancedPinInputDialog
            }
            askNewAdvancedSettingsPin()
        }
    }

    private fun askNewAdvancedSettingsPin() {
        showAdvancedPinInputDialog(
            title = "NOV PIN",
            message = "Vpišite nov PIN. Dovoljene so 4 do 8 številk."
        ) { newPin ->
            if (!isValidAdvancedSettingsPin(newPin)) {
                Toast.makeText(this, "PIN mora imeti 4 do 8 številk", Toast.LENGTH_SHORT).show()
                return@showAdvancedPinInputDialog
            }
            askRepeatAdvancedSettingsPin(newPin)
        }
    }

    private fun askRepeatAdvancedSettingsPin(newPin: String) {
        showAdvancedPinInputDialog(
            title = "PONOVITE PIN",
            message = "Še enkrat vpišite nov PIN."
        ) { repeatedPin ->
            if (newPin != repeatedPin) {
                Toast.makeText(this, "PIN se ne ujema", Toast.LENGTH_SHORT).show()
                return@showAdvancedPinInputDialog
            }
            prefs.edit().putString(PREF_ADVANCED_SETTINGS_PIN, newPin).apply()
            Toast.makeText(this, "PIN za napredni način je shranjen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAdvancedPinInputDialog(title: String, message: String, onPinEntered: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = "PIN"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton("NADALJUJ") { _, _ ->
                onPinEntered(input.text?.toString().orEmpty())
            }
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun isValidAdvancedSettingsPin(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    private fun showChangeAdminPinDialog() {
        showAdvancedPinInputDialog(
            title = "TRENUTNI TERAPEVTSKI PIN",
            message = "Vpišite trenutni terapevtski PIN."
        ) { currentPin ->
            if (currentPin != currentAdminPin() && !isProgrammerPin(currentPin)) {
                Toast.makeText(this, "Napačen trenutni terapevtski PIN", Toast.LENGTH_SHORT).show()
                return@showAdvancedPinInputDialog
            }
            askNewAdminPin()
        }
    }

    private fun askNewAdminPin() {
        showAdvancedPinInputDialog(
            title = "NOV TERAPEVTSKI PIN",
            message = "Vpišite nov terapevtski PIN. Dovoljene so 4 do 8 številk."
        ) { newPin ->
            if (!isValidAdvancedSettingsPin(newPin)) {
                Toast.makeText(this, "Terapevtski PIN mora imeti 4 do 8 številk", Toast.LENGTH_SHORT).show()
                return@showAdvancedPinInputDialog
            }
            askRepeatAdminPin(newPin)
        }
    }

    private fun askRepeatAdminPin(newPin: String) {
        showAdvancedPinInputDialog(
            title = "PONOVITE TERAPEVTSKI PIN",
            message = "Še enkrat vpišite nov terapevtski PIN."
        ) { repeatedPin ->
            if (newPin != repeatedPin) {
                Toast.makeText(this, "Terapevtski PIN se ne ujema", Toast.LENGTH_SHORT).show()
                return@showAdvancedPinInputDialog
            }
            prefs.edit().putString(PREF_ADMIN_PIN, newPin).apply()
            Toast.makeText(this, "Terapevtski PIN je shranjen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isProgrammerPin(pin: String): Boolean {
        return pin == DEFAULT_PROGRAMMER_PIN
    }

    private fun updateSettingsModeButtons() {
        val therapistSelected = settingsDisplayMode == SettingsDisplayMode.THERAPIST
        findViewById<Button>(R.id.btnSettingsModeTherapist).backgroundTintList = ColorStateList.valueOf(
            if (therapistSelected) 0xFF2F5F9E.toInt() else 0xFF3A3F45.toInt()
        )
        findViewById<Button>(R.id.btnSettingsModeAdvanced).backgroundTintList = ColorStateList.valueOf(
            if (therapistSelected) 0xFF3A3F45.toInt() else 0xFF2F5F9E.toInt()
        )
    }

    private fun applyTherapistModeVisibility() {
        val speechHelper = findViewById<TextView>(R.id.helperSpeechApi)
        if (settingsDisplayMode == SettingsDisplayMode.ADVANCED) {
            speechHelper?.text = "Tukaj nastavite glas, hitrost in ključ za govor."
            return
        }

        speechHelper?.text = "Tukaj nastavite glas in hitrost govora."
        val hiddenIds = listOf(
            R.id.txtSpeechApiStatus,
            R.id.editSpeechApiKey,
            R.id.editSpeechApiBaseUrl,
            R.id.editSpeechApiModel,
            R.id.btnImportSpeechApiKey,
            R.id.txtStatusWeatherSourceUrlLabel,
            R.id.editStatusWeatherSourceUrl,
            R.id.btnSaveStatusWeatherSource,
            R.id.sectionAdvancedSettings,
            R.id.helperAdvancedTop,
            R.id.subgroupAdvancedTools,
            R.id.helperAdvancedTools,
            R.id.btnChangeAdvancedSettingsPin,
            R.id.btnChangeAdminPin,
            R.id.btnRadioSettings,
            R.id.btnAacPackSettings,
            R.id.btnCreateSampleAacPack,
            R.id.btnCheckAacFiles,
            R.id.subgroupAdvancedAi,
            R.id.helperAdvancedAi,
            R.id.txtAacAssistTitle,
            R.id.txtAacAssistStatus,
            R.id.switchAacAssistShowSuggestions,
            R.id.editAacAssistInfo,
            R.id.txtKeywordListenerTitle,
            R.id.txtKeywordListenerStatus,
            R.id.editKeywordListenerInfo,
            R.id.subgroupAdvancedTests,
            R.id.helperAdvancedTests,
            R.id.txtKeywordMatcherTitle,
            R.id.editKeywordMatcherInput,
            R.id.btnKeywordMatcherTest,
            R.id.txtKeywordMatcherResult,
            R.id.txtAiObservationTitle,
            R.id.txtAiObservationStatus,
            R.id.editAiObservationInfo,
            R.id.sectionSystemSettings,
            R.id.subgroupAdvancedSystem,
            R.id.helperAdvancedSystem,
            R.id.btnPowerModeOff,
            R.id.btnPowerModeWarning,
            R.id.btnPowerModeSleep,
            R.id.btnKeepScreenOnWhileCharging,
            R.id.btnAllowedUnplugMinus,
            R.id.btnAllowedUnplugPlus,
            R.id.btnCriticalBatteryMinus,
            R.id.btnCriticalBatteryPlus,
            R.id.btnWarningGraceMinus,
            R.id.btnWarningGracePlus,
            R.id.btnResetGpsStatistics
        )
        hiddenIds.forEach { id ->
            findViewById<View>(id)?.visibility = View.GONE
        }
    }

    private fun settingsContentView(): ViewGroup? {
        val scrollView = findViewById<ScrollView>(R.id.settingsScrollView)
        return scrollView.getChildAt(0) as? ViewGroup
    }

    private fun setSettingsChildVisible(viewId: Int) {
        findViewById<View>(viewId)?.visibility = View.VISIBLE
    }

    private fun setSettingsRangeVisible(content: ViewGroup, startId: Int, endId: Int) {
        var isInRange = false
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.id == startId) {
                isInRange = true
            }
            if (isInRange) {
                child.visibility = View.VISIBLE
            }
            if (child.id == endId && isInRange) {
                return
            }
        }
    }

    private fun scrollSettingsToTop() {
        findViewById<ScrollView>(R.id.settingsScrollView).post {
            findViewById<ScrollView>(R.id.settingsScrollView).smoothScrollTo(0, 0)
        }
    }

    private fun scrollToSettingsSection(targetId: Int) {
        val scrollView = findViewById<ScrollView>(R.id.settingsScrollView)
        val target = findViewById<View>(targetId)
        scrollView.post {
            scrollView.smoothScrollTo(0, target.top)
        }
    }

    private fun showModulePlaceholder(moduleName: String) {
        Toast.makeText(this, "$moduleName bo dodan v naslednji fazi.", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        registerBatteryReceiver()
        refreshBatterySnapshot()
        refreshPowerSection()
        refreshStatisticsSection()
        refreshGpsDiagnosticsSection()
        refreshSpeechApiSection()
        refreshSpeechTimingSection()
        refreshAacPatientAuditPanel()
        refreshAacGridSizeSection()
        refreshPersistentTopRowSection()
        refreshGuidedFollowUpSection()
        refreshAacCommunicationContextSection()
        refreshAacAssistSection()
        refreshKeywordListenerSection()
        refreshAiObservationSection()
        refreshAudioDuckingSection()
        refreshStatusOrientationSection()
        refreshPatientProfileSection()
        applyKeepScreenOnWhileCharging()
        startGpsDiagnosticsRefresh()
    }

    override fun onPause() {
        stopGpsDiagnosticsRefresh()
        unregisterBatteryReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        releaseSpeechApiTestPlayer()
        releaseAudioDuckingTestPlayer()
        releaseSpeechLoudnessTestPlayer()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_SPEECH_API_KEY && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importSpeechApiKey(uri)
        }
    }

    private fun setPowerMode(mode: String) {
        prefs.edit().putString(PREF_POWER_MODE, mode).apply()
        showPowerClickFeedback(mode)
        updateModeButtonStyles(mode)
    }

    private fun adjustIntPreference(
        key: String,
        delta: Int,
        min: Int,
        max: Int,
        defaultValue: Int
    ) {
        val current = prefs.getInt(key, defaultValue)
        val updated = (current + delta).coerceIn(min, max)
        prefs.edit().putInt(key, updated).apply()
        refreshPowerSection()
    }

    private fun refreshPowerSection() {
        val powerMode = prefs.getString(PREF_POWER_MODE, DEFAULT_POWER_MODE).orEmpty().ifBlank { DEFAULT_POWER_MODE }
        val allowedUnplugMinutes = prefs.getInt(PREF_POWER_ALLOWED_UNPLUG_MINUTES, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        val warningGraceMinutes = prefs.getInt(PREF_POWER_WARNING_GRACE_MINUTES, DEFAULT_WARNING_GRACE_MINUTES)
        val criticalBatteryPercent = prefs.getInt(PREF_POWER_CRITICAL_BATTERY_PERCENT, DEFAULT_CRITICAL_BATTERY_PERCENT)
        val keepScreenOnWhileCharging = prefs.getBoolean(
            PREF_KEEP_SCREEN_ON_WHILE_CHARGING,
            DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING
        )

        txtAllowedUnplugValue.text = "$allowedUnplugMinutes MIN"
        txtWarningGraceValue.text = "$warningGraceMinutes MIN"
        txtCriticalBatteryValue.text = "$criticalBatteryPercent %"
        txtPowerStatusValue.text = buildPowerStatus(powerMode)
        btnKeepScreenOnWhileCharging.text = if (keepScreenOnWhileCharging) {
            getString(R.string.keep_screen_on_charging_enabled)
        } else {
            getString(R.string.keep_screen_on_charging_disabled)
        }
        btnKeepScreenOnWhileCharging.backgroundTintList = ColorStateList.valueOf(
            if (keepScreenOnWhileCharging) 0xFF2E8B57.toInt() else 0xFF3A3F45.toInt()
        )

        updateModeButtonStyles(powerMode)
    }

    private fun refreshStatisticsSection() {
        val todayMeters = prefs.getLong(PREF_DISTANCE_TODAY_METERS, 0L)
        val totalMeters = prefs.getLong(PREF_DISTANCE_TOTAL_METERS, 0L)
        txtTodayDistanceValue.text = formatDistance(todayMeters)
        txtTotalDistanceValue.text = formatDistance(totalMeters)
    }

    private fun refreshGpsDiagnosticsSection() {
        val signal = prefs.getString(PREF_GPS_LAST_SIGNAL, "WEAK").orEmpty().ifBlank { "WEAK" }
        val accuracyMeters = prefs.getFloat(PREF_GPS_LAST_ACCURACY_METERS, -1f)
        val ignoredReason = prefs.getString(PREF_GPS_LAST_IGNORED_REASON, "NONE").orEmpty().ifBlank { "NONE" }
        txtGpsSignalValue.text = signal
        txtGpsAccuracyValue.text = if (accuracyMeters >= 0f) {
            String.format(Locale.ROOT, "%.1f M", accuracyMeters)
        } else {
            getString(R.string.gps_no_accuracy)
        }
        txtGpsIgnoredReasonValue.text = ignoredReason
    }

    private fun refreshSpeechApiSection() {
        val config = AacSpeechApiConfig.read(this)
        txtSpeechApiStatus.text = if (config.apiKey.isBlank()) {
            "Ključ za govor: ni nastavljen"
        } else {
            "Ključ za govor: nastavljen (${maskApiKey(config.apiKey)})"
        }

        editSpeechApiKey.setText("")
        editSpeechApiKey.hint = if (config.apiKey.isBlank()) {
            "Prilepi ključ za govor"
        } else {
            "Nov ključ ali pusti prazno"
        }
        editSpeechApiBaseUrl.setText(config.baseUrl.ifBlank { AacSpeechApiConfig.DEFAULT_BASE_URL })
        editSpeechApiModel.setText(config.model.ifBlank { AacSpeechApiConfig.DEFAULT_MODEL })
        editSpeechApiVoice.setText(config.voiceId.ifBlank { AacSpeechApiConfig.DEFAULT_VOICE_ID })
        editSpeechApiSpeed.setText(formatSpeechSpeedLabel(config.speed.takeIf { it > 0.0 } ?: AacSpeechApiConfig.DEFAULT_SPEED))
    }

    private fun refreshSpeechTimingSection() {
        val settings = AacSpeechTimingSettings.read(this)
        switchSingleIconSpeech.setOnCheckedChangeListener(null)
        switchFastCompositionSkipLastIcon.setOnCheckedChangeListener(null)
        switchAutoSentenceSpeech.setOnCheckedChangeListener(null)
        switchReturnToRootAfterSentence.setOnCheckedChangeListener(null)
        switchClearSentenceAfterSentence.setOnCheckedChangeListener(null)
        switchPartialSentenceAutoReturn.setOnCheckedChangeListener(null)
        switchSingleIconSpeech.isChecked = settings.speakSingleIconEnabled
        switchFastCompositionSkipLastIcon.isChecked = settings.fastCompositionSkipLastIconEnabled
        switchAutoSentenceSpeech.isChecked = settings.autoSpeakSentenceEnabled
        switchReturnToRootAfterSentence.isChecked = settings.returnToRootAfterSentenceEnabled
        switchClearSentenceAfterSentence.isChecked = settings.clearSentenceAfterSentenceEnabled
        switchPartialSentenceAutoReturn.isChecked = settings.partialSentenceAutoReturnEnabled
        editSingleIconDelay.setText("${settings.mainIconSpeakDelayMs} ms")
        editSubIconDelay.setText("${settings.subIconSpeakDelayMs} ms")
        editAutoSentenceDelay.setText(
            if (settings.autoSpeakSentenceEnabled) {
                "${settings.autoSpeakSentenceDelayMs} ms"
            } else {
                "OFF"
            }
        )
        editPartialSentenceAutoReturnDelay.setText("${settings.partialSentenceAutoReturnMs / 1000L}s")
        editSingleIconDelay.isEnabled = settings.speakSingleIconEnabled
        editSubIconDelay.isEnabled = settings.speakSingleIconEnabled
        editAutoSentenceDelay.isEnabled = settings.autoSpeakSentenceEnabled
        editPartialSentenceAutoReturnDelay.isEnabled = settings.partialSentenceAutoReturnEnabled
        bindSpeechTimingSwitchListeners()
    }

    private fun refreshPersistentTopRowSection() {
        val enabled = prefs.getBoolean(PREF_AAC_PERSISTENT_TOP_ROW_ENABLED, true)
        val count = getPersistentTopRowCount()
        val itemIds = getPersistentTopRowItemIds().take(count)
        switchPersistentTopRowEnabled.setOnCheckedChangeListener(null)
        switchPersistentTopRowEnabled.isChecked = enabled
        txtPersistentTopRowStatus.text = buildString {
            append("Stalna zgornja vrstica: ")
            append(if (enabled) "VKLOP" else "IZKLOP")
            append("\nIkone: ")
            append(itemIds.joinToString(", ") { persistentTopRowLabel(it) })
        }
        editPersistentTopRowCount.setText("$count ikon")
        editPersistentTopRowCount.isEnabled = enabled
        bindPersistentTopRowSwitchListener()
    }

    private fun refreshGuidedFollowUpSection() {
        val settings = AacGuidedFollowUpSettings.read(this)
        val activeProfile = AacProfileStore.getActiveAacProfile(this)
        val guidedAllowed = activeProfile.context != AacCommunicationContext.VIDEO_CALL_COMMUNICATION
        switchGuidedFollowUpEnabled.setOnCheckedChangeListener(null)
        switchVendingNumberDisplayEnabled.setOnCheckedChangeListener(null)
        switchSpeakDigitsSeparatelyEnabled.setOnCheckedChangeListener(null)
        switchGuidedFollowUpEnabled.isChecked = settings.guidedFollowUpEnabled
        switchVendingNumberDisplayEnabled.isChecked = settings.vendingNumberDisplayEnabled
        switchSpeakDigitsSeparatelyEnabled.isChecked = settings.speakDigitsSeparatelyEnabled
        switchGuidedFollowUpEnabled.isEnabled = guidedAllowed
        switchVendingNumberDisplayEnabled.isEnabled = guidedAllowed && settings.guidedFollowUpEnabled
        switchSpeakDigitsSeparatelyEnabled.isEnabled =
            guidedAllowed && settings.guidedFollowUpEnabled && settings.vendingNumberDisplayEnabled
        bindGuidedFollowUpSwitchListeners()
    }

    private fun refreshAacCommunicationContextSection() {
        AacProfileStore.applyProfileDefaultsIfNeeded(this)
        val activeProfile = AacProfileStore.getActiveAacProfile(this)
        val contextMode = AacProfileStore.getActiveAacContext(this)
        val realWorldHelpersEnabled = AacCommunicationContextPrefs.areRealWorldHelpersEnabled(this)
        txtActiveAacProfileStatus.text = "Aktivni AAC profil: ${activeProfile.displayName}"
        editActiveAacProfile.setText(activeProfile.displayName)
        txtAacCommunicationContextStatus.text = "Način komunikacije: ${aacCommunicationContextLabel(contextMode)}"
        editAacCommunicationContext.setText(aacCommunicationContextLabel(contextMode))
        switchRealWorldHelpersEnabled.setOnCheckedChangeListener(null)
        switchRealWorldHelpersEnabled.isChecked = realWorldHelpersEnabled
        switchRealWorldHelpersEnabled.isEnabled = contextMode != AacCommunicationContext.VIDEO_CALL_COMMUNICATION
        bindAacCommunicationContextSwitchListener()
    }

    private fun refreshAacGridSizeSection() {
        val gridSize = getAacGridSize()
        txtAacGridSizeStatus.text = "Velikost mreže komunikatorja: $gridSize x $gridSize"
        editAacGridSize.setText("$gridSize x $gridSize")
    }

    private fun refreshAacPatientAuditPanel() {
        txtAacPatientAuditStatus.text = buildAacPatientAuditText()
    }

    private fun confirmRepairMissingCoreAacIcons() {
        AlertDialog.Builder(this)
            .setTitle("DODAJ OSNOVNE IKONE")
            .setMessage("Aplikacija bo dodala samo manjkajoče osnovne ikone. Obstoječih ikon ne bo izbrisala ali prepisala.")
            .setPositiveButton("DODAJ") { _, _ -> repairMissingCoreAacIcons() }
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun repairMissingCoreAacIcons() {
        try {
            val currentItems = AacEditorStorage.loadItems(this)
            val missingCoreItems = missingCoreAacRepairItems(currentItems)
            if (missingCoreItems.isEmpty()) {
                Toast.makeText(this, "Osnovne ikone so že pripravljene.", Toast.LENGTH_SHORT).show()
                refreshAacPatientAuditPanel()
                return
            }

            val addedCount = AacEditorStorage.addMissingCoreStarterItems(this, missingCoreItems)
            if (addedCount > 0) {
                Toast.makeText(this, "Manjkajoče osnovne ikone so dodane.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Osnovnih ikon ni bilo mogoče dodati.", Toast.LENGTH_SHORT).show()
            }
            refreshAacPatientAuditPanel()
        } catch (_: Exception) {
            Toast.makeText(this, "Osnovnih ikon ni bilo mogoče dodati.", Toast.LENGTH_SHORT).show()
            refreshAacPatientAuditPanel()
        }
    }

    private fun showCoreIconFilenamesDialog() {
        val message = CORE_AAC_AUDIT_ITEMS.joinToString("\n") { core ->
            "${core.label}: NovaRehab/icons/system/core/${core.filename}"
        }
        AlertDialog.Builder(this)
            .setTitle("IMENA DATOTEK")
            .setMessage(message)
            .setPositiveButton("V REDU", null)
            .show()
    }

    private fun buildAacPatientAuditText(): String {
        return try {
            val items = AacEditorStorage.loadItems(this)
            val gridSize = getAacGridSize()
            val fixedItems = items
                .filter { (it.fixedTopRowPosition ?: 0) in 1..5 }
                .sortedBy { it.fixedTopRowPosition ?: Int.MAX_VALUE }
            val firstPageItems = items
                .filter { item -> item.placements.any { it.pageId == "page_1" && it.position5x5 in 1..25 } }
                .filterNot { it.isHiddenUntilParent }
                .sortedBy { item -> item.placements.firstOrNull { it.pageId == "page_1" }?.position5x5 ?: Int.MAX_VALUE }
            val fixedCount = fixedItems.size
            val firstPageCount = firstPageItems.size
            val minimumFirstPageCount = if (gridSize == 5) 15 else (gridSize * gridSize - fixedCount).coerceAtLeast(0)
            val blankItems = items.count { item ->
                item.labelSl.trim().isBlank() && !hasUsableImage(item.imagePath, item.iconSource)
            }
            val imageQuality = auditImageQuality(items)
            val coreIconVisualAudit = auditCoreIconVisualQuality()
            val peoplePhotoAudit = auditPeoplePhotoStatus()
            val backCorrections = AacUsageStats.backCorrectionCount(this)
            val partialAutoReturns = AacUsageStats.autoReturnAfterPartialCount(this)
            val emptySpeechItems = items.filter { explicitSlSpeechText(it).isBlank() }
            val missingCore = missingCoreAacLabels(items)
            val itemIds = items.map { it.id.trim() }.toSet()
            val painSideOk = listOf("pain_left", "pain_right", "pain_both").all { it in itemIds }
            val painTimeOk = listOf(
                "pain_since_today",
                "pain_since_yesterday",
                "pain_since_morning",
                "pain_since_evening"
            ).all { it in itemIds }
            val missingTimeLabels = TIME_PACK_AUDIT_ITEMS
                .filter { (itemId, _) -> itemId !in itemIds }
                .map { (_, label) -> label }
            val missingPlaceLabels = PLACE_PACK_AUDIT_ITEMS
                .filter { (itemId, _) -> itemId !in itemIds }
                .map { (_, label) -> label }
            val missingActivityLabels = ACTIVITY_PACK_AUDIT_ITEMS
                .filter { (itemId, _) -> itemId !in itemIds }
                .map { (_, label) -> label }
            val missingCareLabels = CARE_PACK_AUDIT_ITEMS
                .filter { (itemId, _) -> itemId !in itemIds }
                .map { (_, label) -> label }
            val coreWithoutSpeech = emptySpeechItems.filter { item ->
                CORE_AAC_AUDIT_ITEMS.any { core ->
                    matchesCoreAacItem(item, core)
                }
            }
            val warnings = mutableListOf<String>()
            if (fixedCount < 3) warnings += "fiksna zgornja vrstica ima premalo ikon"
            if (firstPageCount < minimumFirstPageCount) warnings += "prva stran ima premalo ikon"
            if (blankItems > 0) warnings += "nekaj ikon je praznih"
            if (imageQuality.smallImages > 0) warnings += "nekaj slik ikon je premajhnih"
            if (coreIconVisualAudit.missingFilenames.isNotEmpty()) warnings += "manjkajo kakovostne osnovne slike ikon"
            if (coreIconVisualAudit.smallCount > 0) warnings += "nekaj osnovnih slik ikon je premajhnih"
            if (peoplePhotoAudit.missingFilenames.isNotEmpty()) warnings += "manjkajo fotografije oseb"
            if (missingCore.isNotEmpty()) warnings += "manjkajo osnovne ikone"
            if (missingTimeLabels.isNotEmpty()) warnings += "manjkajo časovne ikone"
            if (missingPlaceLabels.isNotEmpty()) warnings += "manjkajo ikone za kraje"
            if (missingActivityLabels.isNotEmpty()) warnings += "manjkajo ikone za dejavnosti"
            if (missingCareLabels.isNotEmpty()) warnings += "manjkajo ikone za nego"
            if (!painSideOk) warnings += "manjkajo ikone za stran bolečine"
            if (!painTimeOk) warnings += "manjkajo ikone za čas bolečine"
            if (coreWithoutSpeech.isNotEmpty()) warnings += "osnovne ikone nimajo govora"
            val overall = if (warnings.isEmpty()) "PRIPRAVLJENO ZA TEST" else "POTREBNA DOPOLNITEV"
            val activeProfile = AacProfileStore.getActiveAacProfile(this).displayName.ifBlank { "DOM" }

            listOf(
                "PREGLED KOMUNIKATORJA",
                "",
                "Aktivni profil: $activeProfile",
                "Velikost mreže: ${gridSize}x$gridSize",
                statusLine("Fiksna zgornja vrstica", "$fixedCount ikon", fixedCount >= 3),
                statusLine("Prva stran", "$firstPageCount ikon", firstPageCount >= minimumFirstPageCount),
                statusLine("Prazne ikone", "$blankItems", blankItems == 0),
                statusLine(
                    "Ikone - kakovost slik",
                    "${imageQuality.checkedImages} preverjenih, ${imageQuality.smallImages} manjših od 256x256, ${imageQuality.largeImages} vsaj 512x512",
                    imageQuality.smallImages == 0
                ),
                "Priporočeno: PNG 512x512 px, prozorno ozadje, brez teksta na ikoni.",
                statusLine(
                    "Osnovne slike ikon",
                    "${coreIconVisualAudit.existingCount}/20 najdenih, ${coreIconVisualAudit.smallCount} premajhnih",
                    coreIconVisualAudit.missingFilenames.isEmpty() && coreIconVisualAudit.smallCount == 0
                ),
                if (coreIconVisualAudit.missingFilenames.isEmpty()) {
                    "Manjkajoče osnovne slike: nič."
                } else {
                    "Manjkajoče osnovne slike: ${coreIconVisualAudit.missingFilenames.joinToString(", ")}"
                },
                "Ikone kopirajte v NovaRehab/icons/system/core/ kot PNG 512x512.",
                "",
                "PEOPLE PHOTO STATUS",
                statusLine(
                    "Fotografije oseb",
                    "${peoplePhotoAudit.existingCount}/${PEOPLE_PHOTO_AUDIT_ITEMS.size} najdenih",
                    peoplePhotoAudit.missingFilenames.isEmpty()
                ),
                if (peoplePhotoAudit.missingFilenames.isEmpty()) {
                    "Manjkajoče fotografije oseb: nič."
                } else {
                    "Manjkajoče fotografije oseb: ${peoplePhotoAudit.missingFilenames.joinToString(", ")}"
                },
                "Pričakovana mapa: NovaRehab/icons/patient/",
                peoplePhotoAudit.lines.joinToString("\n"),
                "",
                "Manjkajoči družinski člani za kasnejši vnos: svak, nečak 1, nečak 2.",
                "Dodajte jih šele, ko imate ime in fotografijo.",
                statusLine(
                    "ČAS",
                    if (missingTimeLabels.isEmpty()) "DA" else "manjka: ${missingTimeLabels.joinToString(", ")}",
                    missingTimeLabels.isEmpty()
                ),
                statusLine(
                    "KRAJ",
                    if (missingPlaceLabels.isEmpty()) "DA" else "manjka: ${missingPlaceLabels.joinToString(", ")}",
                    missingPlaceLabels.isEmpty()
                ),
                statusLine(
                    "DEJAVNOSTI",
                    if (missingActivityLabels.isEmpty()) "DA" else "manjka: ${missingActivityLabels.joinToString(", ")}",
                    missingActivityLabels.isEmpty()
                ),
                statusLine(
                    "NEGA",
                    if (missingCareLabels.isEmpty()) "DA" else "manjka: ${missingCareLabels.joinToString(", ")}",
                    missingCareLabels.isEmpty()
                ),
                "BOLEČINA: stran telesa ${if (painSideOk) "DA" else "NE"}",
                "BOLEČINA: čas ${if (painTimeOk) "DA" else "NE"}",
                statusLine(
                    "Osnovne ikone",
                    if (missingCore.isEmpty()) "vse najdene" else "manjka: ${missingCore.joinToString(", ")}",
                    missingCore.isEmpty()
                ),
                statusLine("Govor", "${emptySpeechItems.size} ikon brez vpisanega govora", coreWithoutSpeech.isEmpty()),
                "Popravki z NAZAJ: $backCorrections",
                "Samodejni povratki po delnem stavku: $partialAutoReturns",
                "Fotografija: ${if (patientProfilePhotoExists()) "DA" else "NE"}",
                "",
                "Skupno stanje: $overall"
            ).joinToString("\n")
        } catch (_: Exception) {
            "PREGLED KOMUNIKATORJA\n\nPOZOR: Pregleda trenutno ni mogoče pripraviti.\nSkupno stanje: POTREBNA DOPOLNITEV"
        }
    }

    private fun statusLine(label: String, value: String, ok: Boolean): String {
        return "${if (ok) "OK" else "POZOR"} - $label: $value"
    }

    private fun explicitSlSpeechText(item: AacItem): String {
        return item.speechTextByLanguage["sl"]?.trim().orEmpty()
            .ifBlank { item.speakTextSl?.trim().orEmpty() }
            .ifBlank { item.speechText?.trim().orEmpty() }
    }

    private fun hasUsableImage(imagePath: String, iconSource: IconSource): Boolean {
        val resolved = AacStoragePaths.resolveIconFile(this, imagePath, iconSource)
        return resolved?.exists() == true && resolved.isFile && resolved.length() > 0L
    }

    private fun auditImageQuality(items: List<AacItem>): ImageQualityAudit {
        var checkedImages = 0
        var smallImages = 0
        var largeImages = 0
        items.forEach { item ->
            val file = AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
                ?.takeIf { it.exists() && it.isFile && it.length() > 0L }
                ?: return@forEach
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return@forEach
            checkedImages += 1
            if (width < 256 || height < 256) smallImages += 1
            if (width >= 512 && height >= 512) largeImages += 1
        }
        return ImageQualityAudit(
            checkedImages = checkedImages,
            smallImages = smallImages,
            largeImages = largeImages
        )
    }

    private fun auditCoreIconVisualQuality(): CoreIconVisualAudit {
        var existingCount = 0
        var smallCount = 0
        val missingFilenames = mutableListOf<String>()
        CORE_AAC_AUDIT_ITEMS.forEach { core ->
            val file = AacStoragePaths.resolveIconFile(this, coreIconImagePath(core), IconSource.SYSTEM)
            if (file?.exists() != true || !file.isFile || file.length() <= 0L) {
                missingFilenames += core.filename
                return@forEach
            }
            existingCount += 1
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0 || width < 256 || height < 256) {
                smallCount += 1
            }
        }
        return CoreIconVisualAudit(
            existingCount = existingCount,
            smallCount = smallCount,
            missingFilenames = missingFilenames
        )
    }

    private fun auditPeoplePhotoStatus(): PeoplePhotoAudit {
        val patientDir = AacStoragePaths.getIconsPatientDir(this)
        var existingCount = 0
        val missingFilenames = mutableListOf<String>()
        val lines = PEOPLE_PHOTO_AUDIT_ITEMS.map { person ->
            val file = patientDir?.let { File(it, person.filename) }
            val exists = file?.exists() == true && file.isFile && file.length() > 0L
            if (exists) {
                existingCount += 1
            } else {
                missingFilenames += person.filename
            }
            "${person.label}: ${if (exists) "DA" else "NE"} (${person.filename})"
        }
        return PeoplePhotoAudit(
            existingCount = existingCount,
            missingFilenames = missingFilenames,
            lines = lines
        )
    }

    private fun missingCoreAacLabels(items: List<AacItem>): List<String> {
        return CORE_AAC_AUDIT_ITEMS
            .filterNot { core ->
                items.any { item ->
                    matchesCoreAacItem(item, core)
                }
            }
            .map { it.label }
    }

    private fun missingCoreAacRepairItems(items: List<AacItem>): List<AacEditorStorage.CoreRepairItem> {
        return CORE_AAC_AUDIT_ITEMS
            .filterNot { core ->
                items.any { item ->
                    matchesCoreAacItem(item, core)
                }
            }
            .flatMap { core ->
                core.starterIds.map { itemId ->
                    AacEditorStorage.CoreRepairItem(
                        id = itemId,
                        labelSl = core.label,
                        speechTextSl = core.speechTextSl,
                        imagePath = coreIconImagePath(core).takeIf(::coreIconFileExists).orEmpty()
                    )
                }
            }
            .distinctBy { it.id }
    }

    private fun matchesCoreAacItem(item: AacItem, core: CoreAacAuditItem): Boolean {
        return item.id.trim() in core.ids || normalizedCoreLabel(item.labelSl) in core.labels.map(::normalizedCoreLabel)
    }

    private fun normalizedCoreLabel(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .uppercase(Locale("sl", "SI"))
            .replace("\\s+".toRegex(), " ")
    }

    private fun coreIconImagePath(core: CoreAacAuditItem): String {
        return "system/core/${core.filename}"
    }

    private fun coreIconFileExists(imagePath: String): Boolean {
        val file = AacStoragePaths.resolveIconFile(this, imagePath, IconSource.SYSTEM)
        return file?.exists() == true && file.isFile && file.length() > 0L
    }

    private fun refreshVendingCodesSection() {
        val location = AacVendingScenario.activeLocation(this)
        editVendingCodes.setText(
            buildString {
                appendLine(location.labelSl)
                location.machines.forEachIndexed { index, machine ->
                    if (index > 0) appendLine()
                    appendLine(machine.labelSl)
                    AacVendingScenario.productsForMachine(machine.id).forEach { product ->
                        val code = machine.codes[product.itemId].orEmpty().ifBlank { "brez kode" }
                        appendLine("${product.label}: $code")
                    }
                }
            }
        )
    }

    private fun refreshAudioDuckingSection() {
        val settings = AudioDuckingSettings.load(this)
        val settingsPath = AudioDuckingSettings.settingsFile(this)?.absolutePath.orEmpty().ifBlank { "ni poti" }
        switchAudioDuckingEnabled.setOnCheckedChangeListener(null)
        switchAudioDuckingEnabled.isChecked = settings.enabled
        txtAudioDuckingStatus.text = if (settings.enabled && settings.duckingPercent > 0) {
            "Radio med govorom: utišan (${settings.duckingPercent} %)"
        } else {
            "Radio med govorom: ne utiša se"
        }
        editAudioDuckingPercent.setText("${settings.duckingPercent} %\n$settingsPath")
        bindAudioDuckingSwitchListener()
    }

    private fun refreshAacSpeechLoudnessSection() {
        val settings = AacSpeechLoudnessSettings.load(this)
        val settingsPath = AacSpeechLoudnessSettings.settingsFile(this)?.absolutePath.orEmpty().ifBlank { "ni poti" }
        txtAacSpeechLoudnessStatus.text = "Jakost AAC govora: ${settings.label()}"
        editAacSpeechLoudnessGain.setText("${settings.label()}\n$settingsPath")
    }

    private fun refreshPatientProfileSection() {
        val profile = PatientProfileSettings.load(this)
        txtPatientProfileStatus.text = buildPatientSummaryText(profile)
        editPatientFirstName.setText(profile.firstName)
        editPatientLastName.setText(profile.lastName)
        editPatientGender.setText(PatientProfileSettings.genderDisplayLabel(profile.gender))
        editPatientAge.setText(profile.age)
        editPatientBirthDate.setText(profile.birthDate)
        editPatientHomeTown.setText(profile.homeTown)
        editPatientCountry.setText(profile.country)
        editPatientMainLanguage.setText(profile.mainLanguage)
        editPatientCaregiverContact.setText(profile.caregiverContact)
        editPatientTherapistContact.setText(profile.therapistContact)
        editPatientShortDescription.setText(profile.shortDescription)
    }

    private fun buildPatientSummaryText(profile: PatientProfileSettings): String {
        val patientName = listOf(profile.firstName, profile.lastName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Ni vpisano" }
        val language = profile.mainLanguage.trim().ifBlank { "Ni nastavljen" }
        val gender = PatientProfileSettings.genderDisplayLabel(profile.gender)
        val gridSize = getAacGridSize()
        val photoStatus = if (patientProfilePhotoExists()) "DA" else "NE"
        val activeProfile = AacProfileStore.getActiveAacProfile(this).displayName.ifBlank { "DOM" }
        val lastUpdated = patientProfileLastUpdatedLabel()

        return listOf(
            "PACIENTKA",
            patientName,
            "",
            "Jezik: $language",
            "Spol: $gender",
            "Mreža: ${gridSize}x$gridSize",
            "Fotografija: $photoStatus",
            "AAC profil: $activeProfile",
            "Zadnja sprememba: $lastUpdated"
        ).joinToString("\n")
    }

    private fun patientProfilePhotoExists(): Boolean {
        val patientDir = AacStoragePaths.getIconsPatientDir(this) ?: return false
        return patientProfilePhotoFiles(patientDir).any { it.exists() && it.isFile && it.length() > 0L }
    }

    private fun patientProfileLastUpdatedLabel(): String {
        val profileFile = AacStoragePaths.getPatientProfileFile(this)
            ?.takeIf { it.exists() && it.isFile && it.lastModified() > 0L }
            ?: return "Ni podatka"
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("sl", "SI"))
            .format(Date(profileFile.lastModified()))
    }

    private fun patientProfilePhotoFiles(patientDir: File): List<File> {
        return listOf("patient_profile_photo.jpg", "patient_profile_photo.png", "patient_profile_photo.webp")
            .map { File(patientDir, it) }
    }

    private fun savePatientProfileSettings() {
        val profile = PatientProfileSettings(
            firstName = editPatientFirstName.text?.toString().orEmpty(),
            lastName = editPatientLastName.text?.toString().orEmpty(),
            gender = PatientProfileSettings.genderFromDisplayLabel(editPatientGender.text?.toString().orEmpty()),
            age = editPatientAge.text?.toString().orEmpty(),
            birthDate = editPatientBirthDate.text?.toString().orEmpty(),
            homeTown = editPatientHomeTown.text?.toString().orEmpty(),
            country = editPatientCountry.text?.toString().orEmpty(),
            mainLanguage = editPatientMainLanguage.text?.toString().orEmpty(),
            caregiverContact = editPatientCaregiverContact.text?.toString().orEmpty(),
            therapistContact = editPatientTherapistContact.text?.toString().orEmpty(),
            shortDescription = editPatientShortDescription.text?.toString().orEmpty()
        )
        val saved = PatientProfileSettings.save(this, profile)
        val synced = if (saved) syncPatientProfileAacSpeech(profile) else false
        Toast.makeText(
            this,
            when {
                saved && synced -> "Podatki o pacientu so shranjeni."
                saved -> "Podatki so shranjeni. AAC odgovori bodo osve\u017eeni ob naslednjem zagonu."
                else -> "Podatkov o pacientu ni bilo mogo\u010de shraniti."
            },
            Toast.LENGTH_SHORT
        ).show()
        refreshPatientProfileSection()
    }

    private fun syncPatientProfileAacSpeech(profile: PatientProfileSettings): Boolean {
        return try {
            AacContentBootstrap.ensurePatientStartupContent(this, AacStarterContentV1.items())
            val linked = ensurePatientProfileAacLink()
            profile.speechByItemId().map { (itemId, speechText) ->
                AacEditorStorage.updateSpeechTextSl(this, itemId, speechText)
            }.all { it } && linked
        } catch (_: Exception) {
            false
        }
    }

    private fun ensurePatientProfileAacLink(): Boolean {
        val people = AacEditorStorage.loadItems(this).firstOrNull { it.id == "people" } ?: return false
        if ("about_me" in people.children) return true
        return AacEditorStorage.updateChildren(this, "people", people.children + "about_me")
    }

    private fun bindAudioDuckingSwitchListener() {
        switchAudioDuckingEnabled.setOnCheckedChangeListener { _, isChecked ->
            val current = AudioDuckingSettings.load(this)
            val saved = AudioDuckingSettings.save(
                this,
                current.copy(
                    enabled = isChecked,
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )
            if (!saved) {
                Toast.makeText(this, "Nastavitve radia med govorom niso bile shranjene.", Toast.LENGTH_SHORT).show()
            }
            refreshAudioDuckingSection()
        }
    }

    private fun showAudioDuckingPercentPicker() {
        val options = intArrayOf(0, 25, 50, 70, 75, 90)
        val current = AudioDuckingSettings.load(this).duckingPercent
        val labels = options.map { percent ->
            if (percent == 0) "0 % - radio ostane enako glasen" else "$percent %"
        }.toTypedArray()
        val selectedIndex = options.indexOf(current).takeIf { it >= 0 } ?: options.indexOf(AudioDuckingSettings.DEFAULT_DUCKING_PERCENT)
        AlertDialog.Builder(this)
            .setTitle("Stopnja zni\u017eanja radia")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val currentSettings = AudioDuckingSettings.load(this)
                val saved = AudioDuckingSettings.save(
                    this,
                    currentSettings.copy(
                        duckingPercent = options[which],
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
                if (!saved) {
                    Toast.makeText(this, "Nastavitve radia med govorom niso bile shranjene.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
                refreshAudioDuckingSection()
            }
            .show()
    }

    private fun showAacSpeechLoudnessPicker() {
        val gains = AacSpeechLoudnessSettings.ALLOWED_GAINS
        val current = AacSpeechLoudnessSettings.load(this).gain
        val labels = gains.map { gain ->
            AacSpeechLoudnessSettings(gain = gain).label()
        }.toTypedArray()
        val selectedIndex = gains.indexOfFirst { kotlin.math.abs(it - current) < 0.001 }
            .takeIf { it >= 0 }
            ?: gains.indexOfFirst { kotlin.math.abs(it - AacSpeechLoudnessSettings.DEFAULT_GAIN) < 0.001 }
        AlertDialog.Builder(this)
            .setTitle("Jakost AAC govora")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val saved = AacSpeechLoudnessSettings.save(
                    this,
                    AacSpeechLoudnessSettings(
                        gain = gains[which],
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
                if (!saved) {
                    Toast.makeText(this, "Nastavitve glasnosti govora niso bile shranjene.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
                refreshAacSpeechLoudnessSection()
            }
            .show()
    }

    private fun testAacSpeechLoudness() {
        releaseSpeechLoudnessTestPlayer()
        speechLoudnessTestPlayer = AacAudioPlayer(this).apply {
            setSpeechListener(object : AacAudioPlayer.SpeechListener {
                override fun onSpeechStarted() = Unit
                override fun onSpeechCompleted() {
                    releaseSpeechLoudnessTestPlayer()
                }

                override fun onSpeechCancelled() {
                    releaseSpeechLoudnessTestPlayer()
                }

                override fun onSpeechError() {
                    releaseSpeechLoudnessTestPlayer()
                }
            })
            speakText("To je test glasnosti govora.")
        }
    }

    private fun testAudioDucking() {
        releaseAudioDuckingTestPlayer()
        audioDuckingTestPlayer = AacAudioPlayer(this).apply {
            setSpeechListener(object : AacAudioPlayer.SpeechListener {
                override fun onSpeechStarted() = Unit
                override fun onSpeechCompleted() {
                    releaseAudioDuckingTestPlayer()
                }

                override fun onSpeechCancelled() {
                    releaseAudioDuckingTestPlayer()
                }

                override fun onSpeechError() {
                    releaseAudioDuckingTestPlayer()
                }
            })
            speakText("To je test govora med radiem.")
        }
        Toast.makeText(this, "Test govora. \u010ce radio igra, se za\u010dasno zni\u017ea.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatusOrientationSection() {
        val settings = StatusOrientationSettings.load(this)
        val settingsPath = StatusOrientationSettings.settingsFile(this)?.absolutePath.orEmpty().ifBlank { "ni poti" }
        switchStatusOrientationEnabled.setOnCheckedChangeListener(null)
        switchStatusOrientationGreeting.setOnCheckedChangeListener(null)
        switchStatusOrientationDate.setOnCheckedChangeListener(null)
        switchStatusOrientationTime.setOnCheckedChangeListener(null)
        switchStatusOrientationWeather.setOnCheckedChangeListener(null)
        switchStatusOrientationEnabled.isChecked = settings.enabled
        switchStatusOrientationGreeting.isChecked = settings.speakGreeting
        switchStatusOrientationDate.isChecked = settings.speakDate
        switchStatusOrientationTime.isChecked = settings.speakTime
        switchStatusOrientationWeather.isChecked = settings.speakWeather
        switchStatusOrientationGreeting.isEnabled = settings.enabled
        switchStatusOrientationDate.isEnabled = settings.enabled
        switchStatusOrientationTime.isEnabled = settings.enabled
        switchStatusOrientationWeather.isEnabled = settings.enabled && settings.weatherSourceUrl.isNotBlank()
        editStatusWeatherSourceName.setText(settings.selectedWeatherSourceName.ifBlank { "Ni izbranega vira" })
        editStatusWeatherSourceUrl.setText(settings.weatherSourceUrl)
        txtStatusOrientationStatus.text = if (settings.enabled) {
            "Statusni govor: VKLOPLJEN"
        } else {
            "Statusni govor: IZKLOPLJEN"
        }
        editStatusOrientationInfo.setText(
            buildString {
                appendLine("Klik na datum v statusu pove orientacijski stavek.")
                appendLine("Pozdrav glede na uro: ${if (settings.speakGreeting) "DA" else "NE"}")
                appendLine("Dan in datum: ${if (settings.speakDate) "DA" else "NE"}")
                appendLine("Ura: ${if (settings.speakTime) "DA" else "NE"}")
                appendLine("Vreme: ${if (settings.speakWeather) "VKLOPLJENO" else "IZKLOPLJENO"}")
                appendLine("Internet: samo za izbrani vremenski vir")
                appendLine("Lokacija: NE")
                append("Lokalna datoteka: $settingsPath")
            }
        )
        bindStatusOrientationSwitchListeners()
    }

    private fun bindStatusOrientationSwitchListeners() {
        switchStatusOrientationEnabled.setOnCheckedChangeListener { _, isChecked ->
            saveStatusOrientationSettings { current ->
                current.copy(enabled = isChecked)
            }
        }
        switchStatusOrientationGreeting.setOnCheckedChangeListener { _, isChecked ->
            saveStatusOrientationSettings { current ->
                current.copy(speakGreeting = isChecked)
            }
        }
        switchStatusOrientationDate.setOnCheckedChangeListener { _, isChecked ->
            saveStatusOrientationSettings { current ->
                current.copy(speakDate = isChecked)
            }
        }
        switchStatusOrientationTime.setOnCheckedChangeListener { _, isChecked ->
            saveStatusOrientationSettings { current ->
                current.copy(speakTime = isChecked)
            }
        }
        switchStatusOrientationWeather.setOnCheckedChangeListener { _, isChecked ->
            saveStatusOrientationSettings { current ->
                current.copy(speakWeather = isChecked && current.weatherSourceUrl.isNotBlank())
            }
        }
    }

    private fun saveStatusOrientationSettings(update: (StatusOrientationSettings) -> StatusOrientationSettings) {
        val current = StatusOrientationSettings.load(this)
        val saved = StatusOrientationSettings.save(
            this,
            update(current).copy(
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
        if (!saved) {
            Toast.makeText(this, "Nastavitve statusnega govora niso bile shranjene.", Toast.LENGTH_SHORT).show()
        }
        refreshStatusOrientationSection()
    }

    private fun showStatusWeatherSourcePicker() {
        val sources = WeatherSource.PREDEFINED
        val labels = sources.map { source -> source.name }.toTypedArray()
        val currentName = StatusOrientationSettings.load(this).selectedWeatherSourceName
        val selectedIndex = sources.indexOfFirst { it.name == currentName }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Vir vremena")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val selected = sources[which]
                StatusOrientationSettings.save(
                    this,
                    StatusOrientationSettings.load(this).copy(
                        selectedWeatherSourceName = selected.name,
                        weatherSourceUrl = selected.url,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                )
                dialog.dismiss()
                refreshStatusOrientationSection()
            }
            .show()
    }

    private fun saveStatusWeatherSource() {
        val sourceName = editStatusWeatherSourceName.text?.toString().orEmpty().trim()
        val sourceUrl = editStatusWeatherSourceUrl.text?.toString().orEmpty().trim()
        val saved = StatusOrientationSettings.save(
            this,
            StatusOrientationSettings.load(this).copy(
                selectedWeatherSourceName = sourceName.ifBlank { "Ro\u010dni vir" },
                weatherSourceUrl = sourceUrl,
                speakWeather = sourceUrl.isNotBlank() && switchStatusOrientationWeather.isChecked,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
        if (!saved) {
            Toast.makeText(this, "Vir vremena ni bil shranjen.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Vir vremena shranjen", Toast.LENGTH_SHORT).show()
        }
        refreshStatusOrientationSection()
    }

    private fun refreshAacAssistSection() {
        val settings = AacAssistSettings.load(this)
        val contextSuggestionsReady = settings.enabled && settings.showSuggestions
        val stateLabel = if (contextSuggestionsReady) "TESTNO" else "IZKLOPLJENO"
        val settingsPath = AacAssistSettings.settingsFile(this)?.absolutePath.orEmpty().ifBlank { "ni poti" }
        switchAacAssistShowSuggestions.setOnCheckedChangeListener(null)
        switchAacAssistShowSuggestions.isChecked = contextSuggestionsReady
        txtAacAssistStatus.text = "AI kontekstni predlogi: pripravljeni, trenutno izklopljeni"
        editAacAssistInfo.setText(
            buildString {
                appendLine("AI predlogi so pripravljeni, vendar še niso aktivni.")
                appendLine("Stanje: $stateLabel")
                appendLine("Način: ${settings.mode}")
                appendLine("Predlogi na zaslonu: ${if (settings.showSuggestions) "DA" else "NE"}")
                appendLine("Kontekstni predlagalnik: ${if (contextSuggestionsReady) "TESTNO" else "IZKLOPLJEN"}")
                appendLine("Mikrofon: IZKLOPLJEN")
                appendLine("Internet: IZKLOPLJEN")
                appendLine("Poslušanje: NE")
                append("Lokalna datoteka: $settingsPath")
            }
        )
        bindAacAssistSwitchListeners()
    }

    private fun bindAacAssistSwitchListeners() {
        switchAacAssistShowSuggestions.setOnCheckedChangeListener { _, isChecked ->
            val current = AacAssistSettings.load(this)
            val saved = AacAssistSettings.save(
                this,
                current.copy(
                    enabled = isChecked,
                    mode = if (isChecked) AacAssistSettings.MODE_TEST else AacAssistSettings.MODE_OFF,
                    showSuggestions = isChecked,
                    allowMicrophone = false,
                    allowNetwork = false,
                    lastUpdatedAt = System.currentTimeMillis()
                )
            )
            if (!saved) {
                Toast.makeText(this, "AI nastavitve niso bile shranjene.", Toast.LENGTH_SHORT).show()
            }
            refreshAacAssistSection()
        }
    }

    private fun refreshKeywordListenerSection() {
        val settings = AacKeywordListenerSettings.load(this)
        val settingsPath = AacKeywordListenerSettings.settingsFile(this)?.absolutePath.orEmpty().ifBlank { "ni poti" }
        txtKeywordListenerStatus.text = "Poslu\u0161alec klju\u010dnih besed: IZKLOPLJENO"
        editKeywordListenerInfo.setText(
            buildString {
                appendLine("Funkcija je pripravljena za kasnej\u0161e testiranje.")
                appendLine("Trenutno ne poslu\u0161a, ne snema in ne uporablja interneta.")
                appendLine("Stanje: ${if (settings.enabled) "TESTNO" else "IZKLOPLJENO"}")
                appendLine("Na\u010din: ${settings.mode}")
                appendLine("Mikrofon: IZKLOPLJEN")
                appendLine("Poslu\u0161anje v ozadju: IZKLOPLJENO")
                appendLine("Internet: IZKLOPLJEN")
                appendLine("Klju\u010dne besede: ${settings.keywords.joinToString(", ")}")
                append("Lokalna datoteka: $settingsPath")
            }
        )
    }

    private fun runKeywordMatcherTest() {
        val input = editKeywordMatcherInput.text?.toString().orEmpty().trim()
        if (input.isBlank()) {
            txtKeywordMatcherResult.text = "Ni vnosa."
            return
        }

        val matches = AacKeywordMatcher.matchToAacItems(input)
        txtKeywordMatcherResult.text = if (matches.isEmpty()) {
            "Ni predlogov."
        } else {
            formatKeywordMatcherResults(matches)
        }
    }

    private fun formatKeywordMatcherResults(itemIds: List<String>): String {
        val labelsById = AacLocalJsonLoader.loadItems(this, AacStarterContentV1.items())
            .associate { item -> item.id to item.labelSl.trim() }
        return itemIds.joinToString(separator = "\n") { itemId ->
            val label = labelsById[itemId].orEmpty()
            if (label.isBlank()) itemId else "$label ($itemId)"
        }
    }

    private fun refreshAiObservationSection() {
        val settings = AiObservationSettings.load(this)
        val settingsPath = AiObservationSettings.settingsFile(this)?.absolutePath.orEmpty().ifBlank { "ni poti" }
        txtAiObservationStatus.text = "AI opazovanje: PRIPRAVA, IZKLOPLJENO"
        editAiObservationInfo.setText(
            buildString {
                appendLine("Funkcija je pripravljena, vendar trenutno ne uporablja mikrofona ali kamere.")
                appendLine("Mikrofon: ${if (settings.allowMicrophoneAnalysis) "VKLOPLJEN" else "IZKLOPLJEN"}")
                appendLine("Kamera: ${if (settings.allowCameraAnalysis) "VKLOPLJENA" else "IZKLOPLJENA"}")
                appendLine("Ucenje mimike: ${if (settings.allowMimicLearning) "VKLOPLJENO" else "IZKLOPLJENO"}")
                appendLine("Dnevno ucenje: ${if (settings.allowDailyLearning) "VKLOPLJENO" else "IZKLOPLJENO"}")
                appendLine("AI dru\u017eabnik: ${if (settings.allowCompanionSuggestions) "VKLOPLJEN" else "IZKLOPLJEN"}")
                appendLine("Potrjevanje DA/NE: ${if (settings.requireYesNoConfirmation) "OBVEZNO" else "NEOBVEZNO"}")
                append("Lokalna datoteka: $settingsPath")
            }
        )
    }

    private fun saveSpeechApiSettings(showSavedToast: Boolean): Boolean {
        val currentConfig = AacSpeechApiConfig.read(this)
        val enteredKey = editSpeechApiKey.text?.toString().orEmpty().trim()
        val keyToSave = enteredKey.ifBlank { currentConfig.apiKey }

        if (keyToSave.isBlank()) {
            txtSpeechApiStatus.text = "Ključ za govor: ni nastavljen"
            Toast.makeText(this, "Ključ za govor ni nastavljen.", Toast.LENGTH_SHORT).show()
            return false
        }

        val saved = AacSpeechApiConfig.saveOpenAiConfig(
            context = this,
            apiKey = keyToSave,
            enabled = true,
            baseUrl = editSpeechApiBaseUrl.text?.toString().orEmpty(),
            model = editSpeechApiModel.text?.toString().orEmpty(),
            voiceId = editSpeechApiVoice.text?.toString().orEmpty(),
            responseFormat = AacSpeechApiConfig.DEFAULT_RESPONSE_FORMAT,
            speed = selectedSpeechSpeed()
        )

        if (!saved) {
            Toast.makeText(this, "Nastavitev govora ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
            return false
        }

        refreshSpeechApiSection()
        if (showSavedToast) {
            Toast.makeText(this, "Nastavitve govora so shranjene.", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun testSpeechApi() {
        txtSpeechApiStatus.text = "Preverjam govor..."
        Thread {
            val file = AacSpeechCoordinator(
                speechCache = AacSpeechCache(this),
                apiClient = OpenAiAacSpeechApiClient(this),
                voiceIdProvider = { AacSpeechApiConfig.read(this).normalizedVoiceId() },
                speedProvider = { AacSpeechApiConfig.read(this).normalizedSpeed() }
            ).getOrGenerateSpeechFile(
                text = "To je test govora.",
                languageCode = AacLanguageResolver.DEFAULT_LANGUAGE_CODE
            )

            runOnUiThread {
                if (file != null && playSpeechApiTestFile(file.absolutePath)) {
                    txtSpeechApiStatus.text = "Govor deluje."
                    Toast.makeText(this, "Govor deluje.", Toast.LENGTH_SHORT).show()
                } else {
                    txtSpeechApiStatus.text = "Govor trenutno ne deluje."
                    Toast.makeText(this, "Govor trenutno ne deluje.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun playSpeechApiTestFile(path: String): Boolean {
        releaseSpeechApiTestPlayer()
        return try {
            speechApiTestPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    releaseSpeechApiTestPlayer()
                }
                prepare()
                start()
            }
            true
        } catch (_: Exception) {
            releaseSpeechApiTestPlayer()
            false
        }
    }

    private fun openSpeechApiKeyImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/json", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_SPEECH_API_KEY)
    }

    private fun showSpeechVoicePicker() {
        val current = editSpeechApiVoice.text?.toString().orEmpty()
        val selectedIndex = SPEECH_VOICE_OPTIONS.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Izberi glas")
            .setSingleChoiceItems(SPEECH_VOICE_OPTIONS, selectedIndex) { dialog, which ->
                editSpeechApiVoice.setText(SPEECH_VOICE_OPTIONS[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showPersistentTopRowCountPicker() {
        val gridSize = getAacGridSize()
        val currentCount = getPersistentTopRowCount()
        val allowedCounts = AAC_PERSISTENT_TOP_ROW_COUNT_OPTIONS.filter { it <= gridSize }.toTypedArray()
        val labels = allowedCounts.map { "$it ikone" }.toTypedArray()
        val selectedIndex = allowedCounts
            .indexOf(currentCount)
            .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Stalna zgornja vrstica")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                prefs.edit()
                    .putBoolean(PREF_AAC_PERSISTENT_TOP_ROW_ENABLED, true)
                    .putInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, allowedCounts[which])
                    .putString(
                        PREF_AAC_PERSISTENT_TOP_ROW_ITEM_IDS,
                        DEFAULT_AAC_PERSISTENT_TOP_ROW_ITEM_IDS.joinToString(",")
                    )
                    .apply()
                refreshPersistentTopRowSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAacGridSizePicker() {
        val currentGridSize = getAacGridSize()
        val labels = AAC_GRID_SIZE_OPTIONS.map { "$it x $it" }.toTypedArray()
        val selectedIndex = AAC_GRID_SIZE_OPTIONS
            .indexOf(currentGridSize)
            .coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Velikost mreže komunikatorja")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val gridSize = AAC_GRID_SIZE_OPTIONS[which]
                val normalizedTopRowCount = normalizePersistentTopRowCount(getPersistentTopRowCount(), gridSize)
                prefs.edit()
                    .putInt(PREF_AAC_GRID_SIZE, gridSize)
                    .putInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, normalizedTopRowCount)
                    .apply()
                refreshAacGridSizeSection()
                refreshPersistentTopRowSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSpeechSpeedPicker() {
        val currentSpeed = selectedSpeechSpeed()
        val selectedIndex = SPEECH_SPEED_OPTIONS
            .indexOfFirst { (_, value) -> value == currentSpeed }
            .coerceAtLeast(1)
        val labels = SPEECH_SPEED_OPTIONS.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Izberi hitrost govora")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                editSpeechApiSpeed.setText(SPEECH_SPEED_OPTIONS[which].first)
                dialog.dismiss()
            }
            .show()
    }

    private fun showMainIconDelayPicker() {
        val settings = AacSpeechTimingSettings.read(this)
        val labels = MAIN_ICON_DELAY_OPTIONS.map { it.first }.toTypedArray()
        val selectedIndex = MAIN_ICON_DELAY_OPTIONS
            .indexOfFirst { it.second == settings.mainIconSpeakDelayMs }
        AlertDialog.Builder(this)
            .setTitle("Pavza glavnih ikon")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val delayMs = MAIN_ICON_DELAY_OPTIONS[which].second
                prefs.edit()
                    .putLong(AacSpeechTimingSettings.PREF_MAIN_ICON_SPEAK_DELAY_MS, delayMs)
                    .apply()
                refreshSpeechTimingSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showSubIconDelayPicker() {
        val settings = AacSpeechTimingSettings.read(this)
        val labels = SUB_ICON_DELAY_OPTIONS.map { it.first }.toTypedArray()
        val selectedIndex = SUB_ICON_DELAY_OPTIONS
            .indexOfFirst { it.second == settings.subIconSpeakDelayMs }
            .coerceAtLeast(5)
        AlertDialog.Builder(this)
            .setTitle("Pavza podikon")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val delayMs = SUB_ICON_DELAY_OPTIONS[which].second
                prefs.edit()
                    .putLong(AacSpeechTimingSettings.PREF_SUB_ICON_SPEAK_DELAY_MS, delayMs)
                    .putLong(AacSpeechTimingSettings.PREF_SINGLE_ICON_SPEAK_DELAY_MS, delayMs)
                    .apply()
                refreshSpeechTimingSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showPatientGenderPicker() {
        val options = arrayOf("\u017denska", "Mo\u0161ki")
        val currentGender = PatientProfileSettings.genderFromDisplayLabel(editPatientGender.text?.toString().orEmpty())
        val selectedIndex = if (currentGender == PatientProfileSettings.GENDER_MALE) 1 else 0
        AlertDialog.Builder(this)
            .setTitle("Spol pacientke")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                editPatientGender.setText(options[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showAutoSentenceDelayPicker() {
        val settings = AacSpeechTimingSettings.read(this)
        val labels = AUTO_SENTENCE_DELAY_OPTIONS.map { it.first }.toTypedArray()
        val selectedIndex = if (!settings.autoSpeakSentenceEnabled) {
            0
        } else {
            AUTO_SENTENCE_DELAY_OPTIONS
                .indexOfFirst { it.second == settings.autoSpeakSentenceDelayMs }
                .coerceAtLeast(4)
        }
        AlertDialog.Builder(this)
            .setTitle("Samodejni govor stavka")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val delayMs = AUTO_SENTENCE_DELAY_OPTIONS[which].second
                prefs.edit().apply {
                    putBoolean(
                        AacSpeechTimingSettings.PREF_AUTO_SPEAK_SENTENCE_ENABLED,
                        delayMs != null
                    )
                    if (delayMs != null) {
                        putLong(AacSpeechTimingSettings.PREF_AUTO_SPEAK_SENTENCE_DELAY_MS, delayMs)
                    }
                }.apply()
                refreshSpeechTimingSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showPartialSentenceAutoReturnDelayPicker() {
        val settings = AacSpeechTimingSettings.read(this)
        val labels = PARTIAL_SENTENCE_AUTO_RETURN_OPTIONS.map { it.first }.toTypedArray()
        val selectedIndex = PARTIAL_SENTENCE_AUTO_RETURN_OPTIONS
            .indexOfFirst { it.second == settings.partialSentenceAutoReturnMs }
            .coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle("Čas povratka po delnem stavku")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val delayMs = PARTIAL_SENTENCE_AUTO_RETURN_OPTIONS[which].second
                prefs.edit()
                    .putLong(AacSpeechTimingSettings.PREF_PARTIAL_SENTENCE_AUTO_RETURN_MS, delayMs)
                    .apply()
                refreshSpeechTimingSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAacProfilePicker() {
        val options = AacProfileStore.loadProfilesFromStorage(this)
        val labels = options.map { it.displayName }.toTypedArray()
        val current = AacProfileStore.getActiveAacProfile(this)
        val selectedIndex = options.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Aktivni AAC profil")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                prefs.edit()
                    .putString(AacProfileStore.PREF_ACTIVE_PROFILE_ID, options[which].id)
                    .apply()
                AacProfileStore.applyProfileDefaultsIfNeeded(this)
                refreshGuidedFollowUpSection()
                refreshAacCommunicationContextSection()
                refreshVendingCodesSection()
                dialog.dismiss()
            }
            .show()
    }

    private fun showVendingCodePicker() {
        val location = AacVendingScenario.activeLocation(this)
        val editableItems = location.machines.flatMap { machine ->
            AacVendingScenario.productsForMachine(machine.id).map { product ->
                machine to product
            }
        }
        val labels = editableItems.map { (machine, product) ->
            val code = machine.codes[product.itemId].orEmpty().ifBlank { "brez kode" }
            "${machine.labelSl} – ${product.label}: $code"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("AVTOMAT – KODE PO LOKACIJI")
            .setItems(labels) { _, which ->
                val (machine, product) = editableItems[which]
                showVendingCodeEditor(machine, product)
            }
            .show()
    }

    private fun showVendingCodeEditor(
        machine: AacVendingScenario.Machine,
        product: AacVendingScenario.Product
    ) {
        val currentCode = machine.codes[product.itemId].orEmpty()
        val input = EditText(this).apply {
            setText(currentCode)
            hint = "npr. B07"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSelectAllOnFocus(true)
            setPadding(40, 24, 40, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("${machine.labelSl} – ${product.label}")
            .setView(input)
            .setPositiveButton("Shrani") { _, _ ->
                val saved = AacVendingScenario.saveCode(
                    this,
                    machine.id,
                    product.itemId,
                    input.text?.toString().orEmpty()
                )
                refreshVendingCodesSection()
                Toast.makeText(
                    this,
                    if (saved) "Koda shranjena lokalno." else "Kode ni bilo mogoče shraniti.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun bindSpeechTimingSwitchListeners() {
        switchSingleIconSpeech.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacSpeechTimingSettings.PREF_SPEAK_SINGLE_ICON_ENABLED, isChecked)
                .apply()
            refreshSpeechTimingSection()
        }
        switchFastCompositionSkipLastIcon.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacSpeechTimingSettings.PREF_FAST_COMPOSITION_SKIP_LAST_ICON_ENABLED, isChecked)
                .apply()
            refreshSpeechTimingSection()
        }
        switchAutoSentenceSpeech.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacSpeechTimingSettings.PREF_AUTO_SPEAK_SENTENCE_ENABLED, isChecked)
                .apply()
            refreshSpeechTimingSection()
        }
        switchReturnToRootAfterSentence.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacSpeechTimingSettings.PREF_RETURN_TO_ROOT_AFTER_SENTENCE_ENABLED, isChecked)
                .apply()
            refreshSpeechTimingSection()
        }
        switchClearSentenceAfterSentence.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacSpeechTimingSettings.PREF_CLEAR_SENTENCE_AFTER_SENTENCE_ENABLED, isChecked)
                .apply()
            refreshSpeechTimingSection()
        }
        switchPartialSentenceAutoReturn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacSpeechTimingSettings.PREF_PARTIAL_SENTENCE_AUTO_RETURN_ENABLED, isChecked)
                .apply()
            refreshSpeechTimingSection()
        }
    }

    private fun bindPersistentTopRowSwitchListener() {
        switchPersistentTopRowEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(PREF_AAC_PERSISTENT_TOP_ROW_ENABLED, isChecked)
                .apply()
            refreshPersistentTopRowSection()
        }
    }

    private fun bindGuidedFollowUpSwitchListeners() {
        switchGuidedFollowUpEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacGuidedFollowUpSettings.PREF_GUIDED_FOLLOW_UP_ENABLED, isChecked)
                .apply()
            refreshGuidedFollowUpSection()
        }
        switchVendingNumberDisplayEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacGuidedFollowUpSettings.PREF_VENDING_NUMBER_DISPLAY_ENABLED, isChecked)
                .apply()
            refreshGuidedFollowUpSection()
        }
        switchSpeakDigitsSeparatelyEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacGuidedFollowUpSettings.PREF_SPEAK_DIGITS_SEPARATELY_ENABLED, isChecked)
                .apply()
            refreshGuidedFollowUpSection()
        }
    }

    private fun bindAacCommunicationContextSwitchListener() {
        switchRealWorldHelpersEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(AacCommunicationContextPrefs.PREF_AAC_REAL_WORLD_HELPERS_ENABLED, isChecked)
                .apply()
            refreshAacCommunicationContextSection()
        }
    }

    private fun aacCommunicationContextLabel(contextMode: AacCommunicationContext): String {
        return when (contextMode) {
            AacCommunicationContext.NORMAL_COMMUNICATION -> "Normalna komunikacija"
            AacCommunicationContext.VIDEO_CALL_COMMUNICATION -> "Video klic"
            AacCommunicationContext.REAL_WORLD_ASSISTANT -> "Pomoč v realnem svetu"
        }
    }

    private fun importSpeechApiKey(uri: Uri) {
        val raw = try {
            contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
        } catch (_: Exception) {
            Toast.makeText(this, "Datoteke s ključem za govor ni bilo mogoče prebrati.", Toast.LENGTH_SHORT).show()
            return
        }

        val importedKey = parseImportedApiKey(raw)
        if (importedKey.isBlank()) {
            Toast.makeText(this, "Ključ za govor ni bil najden.", Toast.LENGTH_SHORT).show()
            return
        }

        editSpeechApiKey.setText(importedKey)
        saveSpeechApiSettings(showSavedToast = true)
    }

    private fun parseImportedApiKey(raw: String): String {
        return raw.replace("\uFEFF", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim().trim(',', '"', '\'') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .firstNotNullOfOrNull { line ->
                val value = valueAfterSeparator(line)
                when {
                    line.contains("apiKey", ignoreCase = true) -> value
                    line.contains("api_key", ignoreCase = true) -> value
                    line.contains("key", ignoreCase = true) -> value
                    line.contains("token", ignoreCase = true) -> value
                    line.startsWith("Bearer ", ignoreCase = true) -> line.removePrefix("Bearer ").trim()
                    !line.startsWith("http", ignoreCase = true) && line.length >= 12 -> line
                    else -> null
                }
            }.orEmpty()
    }

    private fun valueAfterSeparator(line: String): String {
        val cleaned = line.trim().trim(',', '"', '\'')
        val separatorIndex = listOf(cleaned.indexOf('='), cleaned.indexOf(':'))
            .filter { it >= 0 }
            .minOrNull()
        val value = if (separatorIndex != null && !cleaned.startsWith("http", ignoreCase = true)) {
            cleaned.substring(separatorIndex + 1)
        } else {
            cleaned
        }
        return value.trim()
            .trim(',', '"', '\'')
            .removePrefix("Bearer ")
            .trim()
    }

    private fun maskApiKey(apiKey: String): String {
        val trimmed = apiKey.trim()
        return if (trimmed.length <= 4) "****" else "****${trimmed.takeLast(4)}"
    }

    private fun selectedSpeechSpeed(): Double {
        val current = editSpeechApiSpeed.text?.toString().orEmpty()
        return SPEECH_SPEED_OPTIONS.firstOrNull { (label, _) -> label == current }?.second
            ?: current.trim().toDoubleOrNull()
            ?: AacSpeechApiConfig.DEFAULT_SPEED
    }

    private fun formatSpeechSpeedLabel(speed: Double): String {
        val normalized = String.format(Locale.ROOT, "%.2f", speed.coerceIn(0.25, 4.0)).toDouble()
        return SPEECH_SPEED_OPTIONS.firstOrNull { (_, value) -> value == normalized }?.first
            ?: String.format(Locale.ROOT, "%.2f", normalized)
    }

    private fun getPersistentTopRowCount(): Int {
        val gridSize = getAacGridSize()
        return prefs.getInt(
            PREF_AAC_PERSISTENT_TOP_ROW_COUNT,
            DEFAULT_AAC_PERSISTENT_TOP_ROW_COUNT
        ).let { normalizePersistentTopRowCount(it, gridSize) }
    }

    private fun getPersistentTopRowItemIds(): List<String> {
        return prefs.getString(
            PREF_AAC_PERSISTENT_TOP_ROW_ITEM_IDS,
            DEFAULT_AAC_PERSISTENT_TOP_ROW_ITEM_IDS.joinToString(",")
        )
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { DEFAULT_AAC_PERSISTENT_TOP_ROW_ITEM_IDS }
    }

    private fun persistentTopRowLabel(itemId: String): String {
        return when (itemId) {
            "no" -> "NE"
            "yes" -> "DA"
            "dont_understand", "no_understand" -> "NE RAZUMEM"
            "thank_you" -> "HVALA"
            "sorry" -> "OPROSTI"
            "help" -> "POMOČ"
            "pain" -> "BOLI"
            "stop" -> "STOP"
            "wc" -> "WC"
            else -> itemId.uppercase(Locale.ROOT)
        }
    }

    private fun getAacGridSize(): Int {
        return normalizeAacGridSize(prefs.getInt(PREF_AAC_GRID_SIZE, DEFAULT_AAC_GRID_SIZE))
    }

    private fun normalizeAacGridSize(value: Int): Int {
        return when (value) {
            3, 4, 5 -> value
            else -> DEFAULT_AAC_GRID_SIZE
        }
    }

    private fun normalizePersistentTopRowCount(value: Int, gridSize: Int): Int {
        return value.coerceIn(AAC_PERSISTENT_TOP_ROW_COUNT_OPTIONS.first(), gridSize.coerceIn(3, 5))
    }

    private fun createSampleAacPack() {
        val result = AacSampleContentCreator.createIfMissing(this)
        val message = when {
            result.failed -> "Testnega AAC paketa ni bilo mogoče ustvariti."
            result.createdFiles.isNotEmpty() && result.skippedFiles.isEmpty() ->
                "Testni AAC paket je ustvarjen."
            result.createdFiles.isNotEmpty() ->
                "Del testnega AAC paketa je ustvarjen."
            result.skippedFiles.isNotEmpty() ->
                "Testni AAC paket že obstaja."
            else ->
                "Ni sprememb."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showAacContentDiagnostics() {
        val report = AacContentDiagnostics.inspect(this)
        val message = buildAacDiagnosticsMessage(report)

        AlertDialog.Builder(this)
            .setTitle("AAC diagnostika")
            .setMessage(message)
            .setPositiveButton("V redu", null)
            .show()
    }

    // Faza 1: terapevtu razumljivo poročilo o lokalnem AAC paketu.
    // Brez Android terminologije, brez razvijalskih napak (npr. JSONException).
    private fun buildAacDiagnosticsMessage(report: AacContentDiagnostics.Report): String {
        if (report.storageUnavailable) {
            return "Shramba tablice trenutno ni dosegljiva.\n" +
                "Komunikator deluje s SYSTEM vsebino."
        }

        return buildString {
            append("SKUPNA OCENA: ")
            append(aacSeverityLabel(report.severity))
            append("\n\n")

            if (report.severity == AacContentDiagnostics.Severity.NO_PACKAGE) {
                append("Lokalni AAC paket še ni nameščen.\n")
                append("Uporablja se SYSTEM vsebina. To ni napaka.\n\n")
            }

            append("── AAC ELEMENTI ──\n")
            when (report.itemsJsonState) {
                AacContentDiagnostics.JsonState.OK ->
                    append("Najdenih elementov: ${report.itemCount}\n")
                AacContentDiagnostics.JsonState.MISSING ->
                    append("Seznam elementov še ni nameščen.\n")
                AacContentDiagnostics.JsonState.CORRUPT ->
                    append("POKVARJEN: seznam elementov (aac_items.json)\n")
            }
            append("\n")

            append("── PROFILI ──\n")
            if (report.validProfileCount > 0) {
                append("Najdenih profilov: ${report.validProfileCount}\n")
                append(report.validProfileFileNames.joinToString("\n") { "- ${profileDisplayName(it)}" })
                append("\n")
            } else {
                append("Noben profil še ni nameščen.\n")
            }
            if (report.corruptProfileFileNames.isNotEmpty()) {
                append("POKVARJENI profili:\n")
                append(report.corruptProfileFileNames.joinToString("\n") { "- ${profileDisplayName(it)}" })
                append("\n")
            }
            append("\n")

            append("── IKONE ──\n")
            append("Lastne slike: ${report.customIconFileCount}\n")
            append("Soča slike: ${report.socaIconFileCount}\n")
            append("ARASAAC slike: ${report.arasaacIconFileCount}\n")
            when {
                report.iconCheckSkipped ->
                    append("Manjkajočih slik ni bilo mogoče preveriti,\n" +
                        "ker seznam elementov ni berljiv.\n")
                report.missingIconPaths.isEmpty() ->
                    append("Manjkajoče slike: nobena\n")
                else -> {
                    append("MANJKAJOČE SLIKE (${report.missingIconPaths.size}):\n")
                    val shown = report.missingIconPaths.take(MAX_MISSING_ICONS_SHOWN)
                    append(shown.joinToString("\n") { "- $it" })
                    append("\n")
                    val remaining = report.missingIconPaths.size - shown.size
                    if (remaining > 0) {
                        append("... in še $remaining\n")
                    }
                }
            }
            append("\n")

            if (report.packageTotalBytes > 0L) {
                append("── PAKET ──\n")
                append("Velikost: ${formatPackageSize(report.packageTotalBytes)}\n")
                if (report.packageLastModified > 0L) {
                    append("Zadnja sprememba: ${formatPackageDate(report.packageLastModified)}\n")
                }
                append("\n")
            }

            append("── SYSTEM VSEBINA ──\n")
            append("SYSTEM vsebina je na voljo.\n")
            append("Komunikator deluje tudi brez lokalnega paketa.")
        }.trimEnd()
    }

    private fun aacSeverityLabel(severity: AacContentDiagnostics.Severity): String {
        return when (severity) {
            AacContentDiagnostics.Severity.OK -> "V REDU"
            AacContentDiagnostics.Severity.WARNING -> "OPOZORILO"
            AacContentDiagnostics.Severity.ERROR -> "NAPAKA"
            AacContentDiagnostics.Severity.NO_PACKAGE -> "NI PAKETA"
        }
    }

    // Iz imena datoteke (npr. "dom.json") naredi berljiv naziv ("DOM").
    private fun profileDisplayName(fileName: String): String {
        return fileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .trim()
            .uppercase(Locale.ROOT)
            .ifBlank { fileName }
    }

    private fun formatPackageSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatPackageDate(epochMs: Long): String {
        return java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT)
            .format(java.util.Date(epochMs))
    }

    private fun releaseSpeechApiTestPlayer() {
        speechApiTestPlayer?.release()
        speechApiTestPlayer = null
    }

    private fun releaseAudioDuckingTestPlayer() {
        audioDuckingTestPlayer?.setSpeechListener(null)
        audioDuckingTestPlayer?.release()
        audioDuckingTestPlayer = null
    }

    private fun releaseSpeechLoudnessTestPlayer() {
        speechLoudnessTestPlayer?.setSpeechListener(null)
        speechLoudnessTestPlayer?.release()
        speechLoudnessTestPlayer = null
    }

    private fun updateModeButtonStyles(powerMode: String) {
        val activeColor = 0xFF3AAE63.toInt()
        val inactiveColor = 0xFF3A3F45.toInt()

        btnPowerOff.backgroundTintList =
            ColorStateList.valueOf(if (powerMode == POWER_MODE_ALWAYS_ON) activeColor else inactiveColor)
        btnPowerWarning.backgroundTintList =
            ColorStateList.valueOf(if (powerMode == POWER_MODE_BATTERY_SAVER) activeColor else inactiveColor)
        btnPowerSleep.backgroundTintList =
            ColorStateList.valueOf(if (powerMode == POWER_MODE_POWER_SLEEP) activeColor else inactiveColor)
    }

    private fun buildPowerStatus(powerMode: String): String {
        val selectedModeText = getString(R.string.power_selected_mode_format, powerModeLabel(powerMode))
        val descriptionText = powerModeDescription(powerMode)

        val powerSourceText = if (latestPluggedIn) {
            getString(R.string.power_status_plugged)
        } else {
            getString(R.string.power_status_on_battery)
        }
        return "$selectedModeText\n$descriptionText\n$powerSourceText\n${batteryStatusLine()}"
    }

    private fun readBatteryPercent(batteryIntent: Intent?): Int? {
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
    }

    private fun formatDistance(meters: Long): String {
        return if (meters >= 1000L) {
            String.format(Locale.ROOT, "%.1f KM", meters / 1000f)
        } else {
            "$meters M"
        }
    }

    private fun applyKeepScreenOnWhileCharging() {
        val keepScreenOn = isCurrentlyPluggedIn() &&
            prefs.getBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING)
        if (keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun isCurrentlyPluggedIn(): Boolean {
        return latestPluggedIn
    }

    private fun registerBatteryReceiver() {
        if (isBatteryReceiverRegistered) {
            return
        }
        registerReceiver(batteryStatusReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        isBatteryReceiverRegistered = true
    }

    private fun unregisterBatteryReceiver() {
        if (!isBatteryReceiverRegistered) {
            return
        }
        try {
            unregisterReceiver(batteryStatusReceiver)
        } catch (_: IllegalArgumentException) {
        } finally {
            isBatteryReceiverRegistered = false
        }
    }

    private fun refreshBatterySnapshot() {
        updateBatterySnapshot(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
    }

    private fun updateBatterySnapshot(batteryIntent: Intent?) {
        latestPluggedIn = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val freshPercent = readBatteryPercent(batteryIntent)
        if (freshPercent != null) {
            latestBatteryPercent = freshPercent
        }
    }

    private fun startGpsDiagnosticsRefresh() {
        gpsDiagnosticsRefreshHandler.removeCallbacks(gpsDiagnosticsRefreshRunnable)
        gpsDiagnosticsRefreshHandler.post(gpsDiagnosticsRefreshRunnable)
    }

    private fun stopGpsDiagnosticsRefresh() {
        gpsDiagnosticsRefreshHandler.removeCallbacks(gpsDiagnosticsRefreshRunnable)
    }

    private fun showPowerClickFeedback(mode: String) {
        txtPowerStatusValue.text = buildSavedPowerStatus(mode)
        pendingPowerStatusRefresh?.let { powerFeedbackHandler.removeCallbacks(it) }
        val refreshRunnable = Runnable {
            refreshPowerSection()
            pendingPowerStatusRefresh = null
        }
        pendingPowerStatusRefresh = refreshRunnable
        powerFeedbackHandler.postDelayed(refreshRunnable, 450L)
    }

    private fun powerModeLabel(mode: String): String {
        return when (mode) {
            POWER_MODE_BATTERY_SAVER -> getString(R.string.power_mode_warning_label)
            POWER_MODE_POWER_SLEEP -> getString(R.string.power_mode_sleep_label)
            else -> getString(R.string.power_mode_off_label)
        }
    }

    private fun powerModeDescription(mode: String): String {
        return when (mode) {
            POWER_MODE_BATTERY_SAVER -> getString(R.string.power_mode_warning_description)
            POWER_MODE_POWER_SLEEP -> getString(R.string.power_mode_sleep_description)
            else -> getString(R.string.power_mode_off_description)
        }
    }

    private fun buildSavedPowerStatus(mode: String): String {
        return buildString {
            append(getString(R.string.power_mode_saved))
            append('\n')
            append(getString(R.string.power_selected_mode_format, powerModeLabel(mode)))
            append('\n')
            append(powerModeDescription(mode))
        }
    }

    private fun batteryStatusLine(): String {
        return latestBatteryPercent?.let {
            getString(R.string.power_status_battery_percent_format, it)
        } ?: getString(R.string.power_status_battery_unknown)
    }

    private fun showAdminPinDialog(onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = getString(R.string.admin_pin_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_pin_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val enteredPin = input.text?.toString().orEmpty()
                val expectedPin = currentAdminPin()
                if (enteredPin == expectedPin || isProgrammerPin(enteredPin)) {
                    onSuccess()
                } else {
                    Toast.makeText(this, getString(R.string.wrong_pin), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
