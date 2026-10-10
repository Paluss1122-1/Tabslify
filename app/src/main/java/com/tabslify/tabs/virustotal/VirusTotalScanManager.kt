package com.tabslify.tabs.virustotal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.activities.Tabslify
import com.tabslify.tabs.aitab.isAppInForeground
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

var pendingVirusTotalReport: String? by mutableStateOf(null)

data class VirusTotalScanJob(
    val id: String,
    val mode: VirusTotalMode,
    val label: String,
    val target: String,
    val state: VirusTotalState,
    val startedAt: Long,
    val finishedAt: Long = 0L,
    val hiddenInBar: Boolean = false
)

object VirusTotalScanManager {
    private const val PREFS_NAME = "virustotal_reports"
    private const val HISTORY_LIMIT = 50
    private const val SCAN_TIMEOUT_MS = 300_000L
    private const val MISSING_API_KEY_MESSAGE = "Kein VirusTotal API-Key konfiguriert. Bitte VIRUSTOTAL_API_KEY in local.properties eintragen."

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    private val repository by lazy { VirusTotalRepository(httpClient) }

    private val _jobs = MutableStateFlow<List<VirusTotalScanJob>>(emptyList())
    val jobs: StateFlow<List<VirusTotalScanJob>> = _jobs.asStateFlow()

    var vtTabVisible: Boolean = false
    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            _jobs.value = loadJobsFromPrefs(context.applicationContext)
            initialized = true
        }
    }

    fun ensureLoaded(context: Context) = ensureInitialized(context)

    fun startUrl(context: Context, url: String) {
        if (url.isBlank()) return
        startScan(context, VirusTotalMode.URL, url, url.take(30)) { repository.scanUrl(url.trim()) }
    }

    fun startHash(context: Context, hash: String) {
        if (hash.isBlank()) return
        startScan(context, VirusTotalMode.HASH, hash, hash.take(20)) { repository.lookupHash(hash.trim()) }
    }

    fun startFile(context: Context, fileName: String, bytes: ByteArray) {
        startScan(context, VirusTotalMode.FILE, fileName, fileName.take(30)) { repository.scanFile(fileName, bytes) }
    }

    private fun startScan(
        context: Context,
        mode: VirusTotalMode,
        rawInput: String,
        label: String,
        scanAction: suspend () -> VirusTotalState
    ) {
        ensureInitialized(context)
        if (_jobs.value.any {
                it.state is VirusTotalState.Loading && it.mode == mode && it.target == rawInput
            }
        ) {
            return
        }
        val appContext = context.applicationContext
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        if (!repository.hasApiKey) {
            val job = VirusTotalScanJob(
                id = id,
                mode = mode,
                label = label,
                target = rawInput,
                state = VirusTotalState.Error(MISSING_API_KEY_MESSAGE),
                startedAt = now,
                finishedAt = now
            )
            _jobs.value += job
            persistJobs(appContext)
            return
        }

        val initialJob = VirusTotalScanJob(
            id = id,
            mode = mode,
            label = label,
            target = rawInput,
            state = VirusTotalState.Loading,
            startedAt = now
        )
        _jobs.value += initialJob
        persistJobs(appContext)

        Tabslify.serviceScope.launch {
            val result = try {
                withTimeout(SCAN_TIMEOUT_MS.milliseconds) { scanAction() }
            } catch (_: TimeoutCancellationException) {
                VirusTotalState.Error("Scan-Zeitüberschreitung (5 Min)")
            }
            val updatedJob = initialJob.copy(state = result, finishedAt = System.currentTimeMillis())
            updateJob(id, result, appContext)
            onJobComplete(appContext, updatedJob)
        }
    }

    private fun updateJob(id: String, newState: VirusTotalState, context: Context) {
        ensureInitialized(context)
        val currentList = _jobs.value
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val newList = currentList.toMutableList()
            newList[index] = newList[index].copy(state = newState, finishedAt = System.currentTimeMillis())
            _jobs.value = newList
            persistJobs(context)
        }
    }

    private fun onJobComplete(context: Context, job: VirusTotalScanJob) {
        if (!vtTabVisible || !isAppInForeground()) {
            sendVirusTotalScanNotification(context, job)
        }
    }

    fun dismiss(context: Context, id: String) {
        ensureInitialized(context)
        _jobs.value = _jobs.value.map { if (it.id == id) it.copy(hiddenInBar = true) else it }
        persistJobs(context)
    }

    fun deleteReport(context: Context, id: String) {
        ensureInitialized(context)
        _jobs.value = _jobs.value.filter { it.id != id }
        persistJobs(context)
    }

    fun clearHistory(context: Context) {
        ensureInitialized(context)
        _jobs.value = _jobs.value.filter { it.state is VirusTotalState.Loading }
        persistJobs(context)
    }

    private fun persistJobs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        if (_jobs.value.size > HISTORY_LIMIT) {
            val keep = _jobs.value.sortedByDescending { it.startedAt }.take(HISTORY_LIMIT).map { it.id }.toSet()
            _jobs.value = _jobs.value.filter { it.id in keep }
        }
        val recentJobs = _jobs.value.sortedByDescending { it.startedAt }

        for ((id, mode, label, target, state, startedAt, finishedAt, _) in recentJobs) {
            val jsonObj = JSONObject().apply {
                put("id", id)
                put("mode", mode.name)
                put("label", label)
                put("target", target)
                put("startedAt", startedAt)
                put("finishedAt", finishedAt)

                when (state) {
                    is VirusTotalState.Loading -> {
                        put("type", "loading")
                    }
                    is VirusTotalState.Error -> {
                        put("type", "error")
                        put("errorMessage", state.message)
                    }
                    is VirusTotalState.Result -> {
                        put("type", "result")
                        put("malicious", state.stats.malicious)
                        put("suspicious", state.stats.suspicious)
                        put("harmless", state.stats.harmless)
                        put("undetected", state.stats.undetected)
                        put("timeout", state.stats.timeout)
                        put("permalink", state.permalink)
                    }
                    is VirusTotalState.Idle -> {
                        put("type", "idle")
                    }
                }
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit { putString("jobs", jsonArray.toString()) }
    }

    private fun loadJobsFromPrefs(context: Context): List<VirusTotalScanJob> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("jobs", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VirusTotalScanJob>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val id = jsonObj.optString("id")
                val modeStr = jsonObj.optString("mode")
                val label = jsonObj.optString("label")
                val target = jsonObj.optString("target", label)
                val startedAt = jsonObj.optLong("startedAt")
                val finishedAt = jsonObj.optLong("finishedAt")
                val type = jsonObj.optString("type")

                if (id.isEmpty() || modeStr.isEmpty()) continue

                val mode = try { VirusTotalMode.valueOf(modeStr) } catch (_: Exception) { continue }

                val state = when (type) {
                    "loading" -> VirusTotalState.Error(context.getString(R.string.virustotal_scan_abgebrochen))
                    "error" -> VirusTotalState.Error(jsonObj.optString("errorMessage"))
                    "result" -> VirusTotalState.Result(
                        stats = VirusTotalStats(
                            malicious = jsonObj.optInt("malicious"),
                            suspicious = jsonObj.optInt("suspicious"),
                            undetected = jsonObj.optInt("undetected"),
                            harmless = jsonObj.optInt("harmless"),
                            timeout = jsonObj.optInt("timeout")
                        ),
                        permalink = jsonObj.optString("permalink")
                    )
                    else -> VirusTotalState.Idle
                }

                list.add(
                    VirusTotalScanJob(
                        id = id,
                        mode = mode,
                        label = label,
                        target = target,
                        state = state,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        hiddenInBar = true
                    )
                )
            }
            list.sortedBy { it.startedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
