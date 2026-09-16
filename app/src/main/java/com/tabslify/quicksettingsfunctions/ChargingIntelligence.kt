package com.tabslify.quicksettingsfunctions

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.tabslify.R
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.objects.Config.DEF_GEMINI
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.PrefsCleanup
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.tNotify
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.UUID

@Serializable
data class ChargingPercentEvent(
    val timestamp: Long,
    val level: Int,
    val temp: Float,
    val voltage: Int,
    val plugType: Int,
    val brightness: Int? = null,
    val screenOn: Boolean? = null
)

@Serializable
data class ChargingSession(
    val id: String = UUID.randomUUID().toString(),
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val startLevel: Int,
    val endLevel: Int? = null,
    val events: List<ChargingPercentEvent> = emptyList(),
    val weekday: Int,
    val startHour: Int
)

@Suppress("ObjectPropertyName")
@SuppressLint("StaticFieldLeak")
object ChargeSessionRepository {
    private const val MAX_SESSIONS = 200

    private lateinit var context: Context
    private val json = Json { ignoreUnknownKeys = true }
    internal val _currentSession = MutableStateFlow<ChargingSession?>(null)
    val currentSession = _currentSession.asStateFlow()
    internal val _allSessions = MutableStateFlow<List<ChargingSession>>(emptyList())

    fun init(ctx: Context) {
        context = ctx.applicationContext
        appScope.launch {
            val loaded = loadSessions()
            val capped = PrefsCleanup.capToLast(loaded, MAX_SESSIONS)
            _allSessions.value = capped
            if (capped.size != loaded.size) persistSessions(capped)
            _currentSession.value = loadCurrentSession()
        }
    }

    fun onChargingStarted(event: ChargingPercentEvent) {
        val cal = Calendar.getInstance()
        val session = ChargingSession(
            startTimestamp = event.timestamp,
            startLevel = event.level,
            events = listOf(event),
            weekday = cal.get(Calendar.DAY_OF_WEEK),
            startHour = cal.get(Calendar.HOUR_OF_DAY)
        )
        _currentSession.value = session
        persistCurrentSession(session)
    }

    fun onChargingPercent(event: ChargingPercentEvent) {
        val session = _currentSession.value ?: return
        if (session.events.lastOrNull()?.level == event.level) return
        val updated = session.copy(events = session.events + event)
        _currentSession.value = updated
        persistCurrentSession(updated)
    }

    fun onChargingStopped(event: ChargingPercentEvent) {
        val session = _currentSession.value ?: return
        val completed = session.copy(endTimestamp = event.timestamp, endLevel = event.level)
        val updated = PrefsCleanup.capToLast(_allSessions.value + completed, MAX_SESSIONS)
        _allSessions.value = updated
        persistSessions(updated)
        _currentSession.value = null
        context.getSharedPreferences("charge_session", Context.MODE_PRIVATE)
            .edit { remove("current") }
    }

    private fun persistCurrentSession(session: ChargingSession) = appScope.launch(Dispatchers.IO) {
        runCatching {
            context.getSharedPreferences("charge_session", Context.MODE_PRIVATE)
                .edit { putString("current", json.encodeToString(session)) }
        }
    }

    private suspend fun loadCurrentSession(): ChargingSession? = withContext(Dispatchers.IO) {
        runCatching {
            context.getSharedPreferences("charge_session", Context.MODE_PRIVATE)
                .getString("current", null)?.let { json.decodeFromString<ChargingSession>(it) }
        }.getOrNull()
    }

    private fun persistSessions(sessions: List<ChargingSession>) = appScope.launch(Dispatchers.IO) {
        runCatching {
            context.getSharedPreferences("charge_sessions", Context.MODE_PRIVATE)
                .edit { putString("all", json.encodeToString(sessions)) }
            syncSessionsToSupabase(sessions)
        }
    }

    private suspend fun loadSessions(): List<ChargingSession> = withContext(Dispatchers.IO) {
        runCatching {
            if (!prvt()) {
                Toast.makeText(context, context.getString(R.string.forbidden), Toast.LENGTH_SHORT)
                    .show()
                return@withContext emptyList()
            }
            val local = context.getSharedPreferences("charge_sessions", Context.MODE_PRIVATE)
                .getString("all", null)?.let { json.decodeFromString<List<ChargingSession>>(it) }
                ?: emptyList()
            if (local.isNotEmpty()) return@withContext local
            val remote = client.from("Tabslify").select().decodeSingle<JsonObject>()
                .let { it["charging_sessions"]?.jsonPrimitive?.content }
                ?.let { json.decodeFromString<List<ChargingSession>>(it) }
                ?: emptyList()
            remote
        }.getOrElse { emptyList() }
    }

    private fun syncSessionsToSupabase(sessions: List<ChargingSession>) =
        appScope.launch(Dispatchers.IO) {
            runCatching {
                if (!prvt()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.forbidden),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@runCatching
                }
                client.from("Tabslify").upsert(buildJsonObject {
                    put("id", 1)
                    put("charging_sessions", json.encodeToString(sessions))
                })
            }
        }

    fun getSessionsForWeekdayHour(weekday: Int, hour: Int) =
        _allSessions.value.filter { it.weekday == weekday && it.startHour == hour && it.endLevel != null }

    fun getRecentChargeRates(): List<Float> =
        _allSessions.value.takeLast(15).mapNotNull { s ->
            val gain = (s.endLevel ?: return@mapNotNull null) - s.startLevel
            val durMin = ((s.endTimestamp ?: return@mapNotNull null) - s.startTimestamp) / 60000f
            if (durMin > 0 && gain > 0) gain / durMin else null
        }
}

class ChargingTrackerService : Service() {
    private var detailedLogging = false
    private var batteryRegistered = false
    private var allowRegistered = false

    private val allowReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_ALLOW) return
            detailedLogging = true
            getSystemService(NotificationManager::class.java).cancel(NOTIF_PERMISSION_ID)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val level =
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it >= 0 } ?: return
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).takeIf { it != -1 }
                ?.div(10f) ?: return
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val screenOn: Boolean?
            val brightness: Int?
            if (detailedLogging) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                screenOn = pm.isInteractive
                brightness = runCatching {
                    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
                        .takeIf { it >= 0 }
                }.getOrNull()
            } else {
                screenOn = null
                brightness = null
            }

            val event = ChargingPercentEvent(
                System.currentTimeMillis(),
                level,
                temp,
                voltage,
                plugged,
                brightness,
                screenOn
            )

            if (isCharging) {
                if (ChargeSessionRepository.currentSession.value == null) ChargeSessionRepository.onChargingStarted(
                    event
                )
                else ChargeSessionRepository.onChargingPercent(event)
            } else if (ChargeSessionRepository.currentSession.value != null) {
                ChargeSessionRepository.onChargingStopped(event)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("services_master", true) || !prefs.getBoolean(
                "service_charge",
                false
            )
        ) {
            stopSelf()
            return
        }
        ChargeSessionRepository.init(this)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryRegistered = true
        registerReceiver(allowReceiver, IntentFilter(ACTION_ALLOW))
        allowRegistered = true

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                "charging_tracker",
                getString(R.string.ladetracking),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        startForeground(
            9823, NotificationCompat.Builder(this, "charging_tracker")
                .setContentTitle(getString(R.string.ladetracking_aktiv))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        )

        val allowBase = Intent(ACTION_ALLOW)
        allowBase.setPackage(packageName)
        val allowPi = PendingIntent.getBroadcast(
            this, 0, allowBase,
            PendingIntent.FLAG_IMMUTABLE
        )
        tNotify(
            this, NOTIF_PERMISSION_ID, NotificationCompat.Builder(this, "charging_tracker")
                .setContentTitle(getString(R.string.detailliertes_laden_loggen))
                .setContentText(getString(R.string.helligkeit_bildschirmstatus_pro_prozent_aufzeichnen))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .addAction(0, getString(R.string.erlauben), allowPi)
                .setAutoCancel(true)
                .build()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (batteryRegistered) {
            runCatching { unregisterReceiver(batteryReceiver) }
            batteryRegistered = false
        }
        if (allowRegistered) {
            runCatching { unregisterReceiver(allowReceiver) }
            allowRegistered = false
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_PERMISSION_ID)
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val ACTION_ALLOW = "com.tabslify.CHARGING_LOG_ALLOW"
        private const val NOTIF_PERMISSION_ID = 9824
    }
}

// Manifest-Receiver für Strom angeschlossen/getrennt
class ChargingPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                context.startForegroundService(Intent(context, ChargingTrackerService::class.java))

            Intent.ACTION_POWER_DISCONNECTED ->
                context.stopService(Intent(context, ChargingTrackerService::class.java))
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun predictChargingTime(context: Context): Int? {
    val sample = readBatterySample(context) ?: return null
    if (sample.level >= 85) return 0
    val levelsNeeded = 85 - sample.level

    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val plugType = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val plugTypeStr = when (plugType) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC/Netzteil (schnell)"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB (langsam)"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kabellos"
        else -> "Unbekannt"
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isScreenOn = powerManager.isInteractive
    val brightness = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }.getOrDefault(128)

    val recentSamples = BatteryDataRepository.recentSamples(15 * 60 * 1000L)
    val liveRate: Float? = if (recentSamples.size >= 2) {
        val levelDiff = recentSamples.last().level - recentSamples.first().level
        val timeDiff = (recentSamples.last().timestamp - recentSamples.first().timestamp) / 60000f
        if (timeDiff > 0 && levelDiff > 0) levelDiff / timeDiff else null
    } else null

    val recentRates = ChargeSessionRepository.getRecentChargeRates()
    val avgRate = recentRates.takeIf { it.isNotEmpty() }?.average()

    val cal = Calendar.getInstance()
    val similarSessions = ChargeSessionRepository.getSessionsForWeekdayHour(
        cal.get(Calendar.DAY_OF_WEEK), cal.get(Calendar.HOUR_OF_DAY)
    )
    val similarAvgRate = similarSessions.mapNotNull { s ->
        val gain = (s.endLevel ?: return@mapNotNull null) - s.startLevel
        val dur = ((s.endTimestamp ?: return@mapNotNull null) - s.startTimestamp) / 60000f
        if (dur > 0 && gain > 0) gain / dur else null
    }.takeIf { it.isNotEmpty() }?.average()

    val perPercentTimes: String = run {
        val current = ChargeSessionRepository.currentSession.value
        if (current != null && current.events.size >= 2) {
            val last5 = current.events.takeLast(5)
            last5.zipWithNext { a, b ->
                val dt = (b.timestamp - a.timestamp) / 1000f
                "${b.level}%: ${dt.toInt()}s"
            }.joinToString(", ")
        } else "Keine laufende Session"
    }

    val usageStats = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        if (context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED) {
            return@runCatching "Berechtigung fehlt (PACKAGE_USAGE_STATS)"
        } else {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, now - 28L * 86400000, now)
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(5)
                .joinToString(", ") { "${it.packageName.substringAfterLast('.')}: ${it.totalTimeInForeground / 60000}min" }
        }
    }.getOrElse { "Berechtigung fehlt (PACKAGE_USAGE_STATS)" }

    val prompt = buildString {
        appendLine("You are a battery charge-time prediction engine. Output ONLY a single integer: estimated minutes to reach 85%. No explanation, no units, no text.")
        appendLine()
        appendLine("## DATA")
        appendLine("Current: ${sample.level}% → Target: 85% (${levelsNeeded}% remaining)")
        appendLine("Temp: ${sample.temperature}°C | Voltage: ${sample.voltage}mV | Charger: $plugTypeStr")
        appendLine("Screen: ${if (isScreenOn) "ON (brightness $brightness/255)" else "OFF"}")
        appendLine()
        appendLine("## CHARGE RATE SIGNALS (prioritize in this order)")
        appendLine("1. LIVE last 15 min: ${liveRate?.let { "%.4f%%/min".format(it) } ?: "unavailable"}")
        appendLine("2. Per-% intervals (current session): $perPercentTimes")
        appendLine("3. Historical avg (last 15 sessions): ${avgRate?.let { "%.4f%%/min".format(it) } ?: "unavailable"}")
        appendLine(
            "4. Context-matched avg (weekday/hour, n=${similarSessions.size}): ${
                similarAvgRate?.let {
                    "%.4f%%/min".format(
                        it
                    )
                } ?: "unavailable"
            }")
        appendLine()
        appendLine("## ADJUSTMENT FACTORS")
        appendLine("- Screen ON with high brightness (>150) typically adds 3–8 min")
        appendLine("- Temperature above 38°C typically slows charge rate by 10–20%")
        appendLine("- USB charging is ~3× slower than AC; wireless is ~2× slower than AC")
        appendLine("- Charge rate typically slows above 80% (taper phase)")
        appendLine("- Typical apps active now: $usageStats")
        appendLine()
        appendLine("## REASONING GUIDE")
        appendLine("Best estimate = $levelsNeeded / best_available_rate, adjusted for above factors.")
        appendLine("If live rate is available and consistent with per-% intervals, weight it 80%.")
        appendLine("If live rate is unavailable, use historical rates, preferring context-matched.")
        appendLine()
        appendLine("Reply with a single integer only.")
    }

    return try {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(DEF_GEMINI)
        val response = model.generateContent(prompt)
        response.text?.trim()?.toIntOrNull()
    } catch (e: Exception) {
        Log.d("CLOUDSA", "crashed: ${e.message}")
        liveRate?.let { if (it > 0) (levelsNeeded / it).toInt() else null }
            ?: avgRate?.let { if (it > 0) (levelsNeeded / it).toInt() else null }
    }
}
