package com.tabslify.tabs.exploretab

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.tabslify.core.objects.Config
import com.tabslify.quiethoursnotificationhelper.getHomeWifiStatus
import com.tabslify.quiethoursnotificationhelper.isLaptopConnected
import com.tabslify.quiethoursnotificationhelper.stopAllSyncServices
import com.tabslify.quiethoursnotificationhelper.syncTodosWithLaptop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class ExploreTrackerInfo(
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastUpdate: String? = null,
    val distanceToHomeMeters: Float? = null,
    val geofenceRegistered: Boolean = false,
    val geofenceError: String? = null,
    val lastWifiHome: Boolean? = null,
    val isNight: Boolean = false,
    val isEnabled: Boolean = false,
)

data class ExploreActivityInfo(
    val mode: String = "UNKNOWN",
    val confidence: Int = 0,
    val held: Boolean = false,
)

class ExploreGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                ExploreLocationTracker.stop(context)
                ExploreLocationTracker.onArrivedHome(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                ExploreLocationTracker.start(context)
                ExploreLocationTracker.onLeftHome(context)
            }
        }
    }
}

class ExploreActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        ExploreLocationTracker.onActivityRecognitionResult(context, result)
    }
}

object ExploreLocationTracker {

    private val _trackerStatus = MutableStateFlow("Inaktiv")
    val trackerStatus: StateFlow<String> = _trackerStatus.asStateFlow()

    private val _trackerInfo = MutableStateFlow(ExploreTrackerInfo())
    val trackerInfo: StateFlow<ExploreTrackerInfo> = _trackerInfo.asStateFlow()

    private val _currentActivity = MutableStateFlow(ExploreActivityInfo())
    val currentActivity: StateFlow<ExploreActivityInfo> = _currentActivity.asStateFlow()

    private const val GEOFENCE_ID = "HOME"
    private const val GEOFENCE_RADIUS = 100f
    const val HOME_WIFI_SSID = "FRITZ!Box 5590 XO"
    private const val NIGHT_START_HOUR = 0
    private const val NIGHT_END_HOUR = 5

    private const val MAX_ACCURACY_METERS = 50f
    private const val MAX_VERTICAL_ACCURACY_METERS = 15f
    private const val UNIVERSAL_SPEED_CEILING_MPS = 55f
    private const val AR_UPDATE_INTERVAL_MS = 30_000L
    private const val AR_CONFIDENCE_FLOOR = 50
    private const val AR_CEILING_CONFIDENCE_FLOOR = 75

    private const val AR_MODE_HOLD_MS = 3 * 60_000L
    private const val MIN_IMPLIED_SPEED_INTERVAL_S = 2.0

    private const val AR_LOG_CONFIDENCE_DELTA = 15
    private const val AR_LOG_MAX_SILENCE_MS = 5 * 60_000L

    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var currentMode: String = "UNKNOWN"
    private var currentProfile: String = requestProfile("UNKNOWN")
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastConfidentMode = "UNKNOWN"
    private var lastConfidentAt = 0L

    private var lastLoggedActivityMode: String? = null
    private var lastLoggedActivityConfidence = 0
    private var lastLoggedActivityAt = 0L

    @Volatile
    private var isEnabled = false

    private fun isNightTime(): Boolean {
        val hour = Instant.now().atZone(java.time.ZoneId.systemDefault()).hour
        return hour in NIGHT_START_HOUR until NIGHT_END_HOUR
    }

    private fun distanceToHome(lat: Double, lng: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat, lng, Config.LAT, Config.LON, results)
        return results[0]
    }

    fun onArrivedHome(context: Context) {
        syncTodosWithLaptop(context.applicationContext)
    }

    fun onLeftHome(context: Context) {
        if (isLaptopConnected) {
            stopAllSyncServices(context.applicationContext)
        }
    }

    private fun getClient(context: Context) =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExploreGeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun activityRecognitionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExploreActivityRecognitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerGeofence(context: Context) {
        val homeLat = Config.LAT
        val homeLng = Config.LON

        _trackerInfo.value = _trackerInfo.value.copy(
            homeLat = homeLat,
            homeLng = homeLng,
        )

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(homeLat, homeLng, GEOFENCE_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or
                    GeofencingRequest.INITIAL_TRIGGER_EXIT
            )
            .addGeofence(geofence)
            .build()

        LocationServices.getGeofencingClient(context)
            .addGeofences(request, geofencePendingIntent(context))
            .addOnSuccessListener {
                _trackerInfo.value = _trackerInfo.value.copy(
                    geofenceRegistered = true,
                    geofenceError = null,
                )
            }
            .addOnFailureListener { e ->
                _trackerInfo.value = _trackerInfo.value.copy(
                    geofenceRegistered = false,
                    geofenceError = e.message,
                )
            }
    }

    fun start(context: Context) {
        val appCtx = context.applicationContext

        if (isNightTime()) {
            _trackerStatus.value = "Pausiert (Nacht)"
            _trackerInfo.value = _trackerInfo.value.copy(isNight = true, isEnabled = false)
            ExploreNightRestartWorker.schedule(appCtx)
            return
        }
        ExploreNightRestartWorker.cancel(appCtx)
        if (isEnabled) {
            return
        }
        isEnabled = true
        _trackerStatus.value = "Startet..."
        _trackerInfo.value = _trackerInfo.value.copy(isNight = false, isEnabled = true)

        try {
            registerGeofence(appCtx)
        } catch (_: Exception) {
        }

        _trackerStatus.value = "Warte auf Geofence"
        _trackerInfo.value = _trackerInfo.value.copy(
            isNight = false,
            isEnabled = true,
            lastWifiHome = null,
        )
        evaluateCurrentLocation(appCtx)

        getHomeWifiStatus(appCtx, HOME_WIFI_SSID) { isHomeWifi ->
            _trackerInfo.value = _trackerInfo.value.copy(lastWifiHome = isHomeWifi)
            if (isHomeWifi) {
                _trackerStatus.value = "Gestoppt (Zuhause)"
                stop(appCtx)
                onArrivedHome(appCtx)
                return@getHomeWifiStatus
            }
            if (isEnabled && !isNightTime() &&
                (ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            ) {
                ContextCompat.startForegroundService(appCtx, Intent(appCtx, ExploreForegroundService::class.java))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun evaluateCurrentLocation(context: Context) {
        if (Config.LAT == 0.0 && Config.LON == 0.0) {
            _trackerStatus.value = "Warte auf Geofence"
            return
        }

        scope.launch {
            try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(30_000L)
                    .setDurationMillis(5_000L)
                    .build()

                val location = Tasks.await(getClient(context).getCurrentLocation(request, null))
                if (location == null) {
                    _trackerStatus.value = "Warte auf Geofence"
                    return@launch
                }

                val distanceHome = distanceToHome(location.latitude, location.longitude)
                _trackerInfo.value = _trackerInfo.value.copy(
                    lastLat = location.latitude,
                    lastLng = location.longitude,
                    lastUpdate = Instant.now().toString(),
                    distanceToHomeMeters = distanceHome,
                )

                if (distanceHome <= GEOFENCE_RADIUS) {
                    _trackerStatus.value = "Zuhause"
                    onArrivedHome(context)
                } else {
                    _trackerStatus.value = "Außerhalb"
                    onLeftHome(context)
                }
            } catch (_: Exception) {
                _trackerStatus.value = "Warte auf Geofence"
            }
        }
    }

    private fun locationRequestFor(mode: String): LocationRequest = when (mode) {
        "STILL" -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 120_000L)
            .setMinUpdateIntervalMillis(60_000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        "WALKING", "ON_FOOT" -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(8_000L)
            .setMinUpdateDistanceMeters(10f)
            .setWaitForAccurateLocation(false)
            .build()

        "ON_BICYCLE" -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(4_000L)
            .setMinUpdateDistanceMeters(15f)
            .setWaitForAccurateLocation(false)
            .build()

        "IN_VEHICLE" -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(4_000L)
            .setMinUpdateDistanceMeters(30f)
            .setWaitForAccurateLocation(false)
            .build()

        else -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMinUpdateDistanceMeters(20f)
            .setWaitForAccurateLocation(false)
            .build()
    }

    private fun modeSpeedCeiling(mode: String): Float = when (mode) {
        "STILL" -> 2f
        "WALKING", "ON_FOOT" -> 3f
        "ON_BICYCLE" -> 20f
        "IN_VEHICLE" -> 60f
        else -> UNIVERSAL_SPEED_CEILING_MPS
    }

    private fun requestProfile(mode: String): String = when (mode) {
        "STILL" -> "STILL"
        "WALKING", "ON_FOOT" -> "FOOT"
        "ON_BICYCLE" -> "BICYCLE"
        "IN_VEHICLE" -> "VEHICLE"
        else -> "DEFAULT"
    }

    private fun usableAltitude(location: Location): Double? {
        if (!location.hasAltitude()) return null
        if (location.hasVerticalAccuracy() && location.verticalAccuracyMeters > MAX_VERTICAL_ACCURACY_METERS) return null
        return location.altitude
    }

    private fun activityTypeToMode(type: Int): String = when (type) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        else -> "UNKNOWN"
    }

    private fun logEvent(context: Context, type: String, mode: String, confidence: Int, detail: String?, timestamp: Long = System.currentTimeMillis()) {
        val appCtx = context.applicationContext
        scope.launch {
            try {
                ExploreRepository(appCtx).logTrackerEvent(
                    TrackerEvent(
                        timestamp = timestamp,
                        type = type,
                        mode = mode,
                        confidence = confidence,
                        detail = detail
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    fun tuningSnapshot(): Map<String, Any> {
        val modes = listOf("STILL", "WALKING", "ON_FOOT", "ON_BICYCLE", "IN_VEHICLE", "UNKNOWN")
        return mapOf(
            "maxAccuracyMeters" to MAX_ACCURACY_METERS,
            "maxVerticalAccuracyMeters" to MAX_VERTICAL_ACCURACY_METERS,
            "universalSpeedCeilingMps" to UNIVERSAL_SPEED_CEILING_MPS,
            "arUpdateIntervalMs" to AR_UPDATE_INTERVAL_MS,
            "arConfidenceFloor" to AR_CONFIDENCE_FLOOR,
            "arCeilingConfidenceFloor" to AR_CEILING_CONFIDENCE_FLOOR,
            "arModeHoldMs" to AR_MODE_HOLD_MS,
            "minImpliedSpeedIntervalSeconds" to MIN_IMPLIED_SPEED_INTERVAL_S,
            "geofenceRadiusMeters" to GEOFENCE_RADIUS,
            "nightStartHour" to NIGHT_START_HOUR,
            "nightEndHour" to NIGHT_END_HOUR,
            "speedCeilingsMps" to modes.associateWith { modeSpeedCeiling(it) },
            "requestProfiles" to modes.associateWith { requestProfile(it) },
            "locationRequests" to modes.associateWith { mode ->
                val request = locationRequestFor(mode)
                mapOf(
                    "intervalMs" to request.intervalMillis,
                    "minIntervalMs" to request.minUpdateIntervalMillis,
                    "minDistanceMeters" to request.minUpdateDistanceMeters,
                    "priority" to request.priority
                )
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun applyLocationRequest(context: Context, mode: String) {
        val callback = locationCallback ?: return
        currentMode = mode
        currentProfile = requestProfile(mode)
        try {
            val client = getClient(context)
            client.removeLocationUpdates(callback)
            client.requestLocationUpdates(locationRequestFor(mode), callback, Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    fun onActivityRecognitionResult(context: Context, result: ActivityRecognitionResult) {
        val appCtx = context.applicationContext
        val most = result.mostProbableActivity
        val now = System.currentTimeMillis()

        val detected = activityTypeToMode(most.type)
        val confident = most.confidence >= AR_CONFIDENCE_FLOOR && detected != "UNKNOWN"
        var held = false
        val mode = when {
            confident -> {
                lastConfidentMode = detected
                lastConfidentAt = now
                detected
            }

            lastConfidentMode != "UNKNOWN" && now - lastConfidentAt <= AR_MODE_HOLD_MS -> {
                held = true
                lastConfidentMode
            }

            else -> {
                lastConfidentMode = "UNKNOWN"
                "UNKNOWN"
            }
        }
        _currentActivity.value = ExploreActivityInfo(mode, most.confidence, held)

        val worthLogging = mode != lastLoggedActivityMode ||
            kotlin.math.abs(most.confidence - lastLoggedActivityConfidence) >= AR_LOG_CONFIDENCE_DELTA ||
            now - lastLoggedActivityAt >= AR_LOG_MAX_SILENCE_MS
        if (worthLogging) {
            lastLoggedActivityMode = mode
            lastLoggedActivityConfidence = most.confidence
            lastLoggedActivityAt = now
            val candidates = result.probableActivities.joinToString(" ") {
                "${activityTypeToMode(it.type)}:${it.confidence}"
            }
            val suffix = if (held) " gehalten" else ""
            logEvent(appCtx, "AR", mode, most.confidence, candidates + suffix, result.time)
        }

        val profile = requestProfile(mode)
        currentMode = mode
        if (locationCallback != null && profile != currentProfile) {
            logEvent(appCtx, "MODE_SWITCH", mode, most.confidence, "profil=$currentProfile->$profile")
            applyLocationRequest(appCtx, mode)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startActivityRecognitionUpdates(context: Context) {
        try {
            ActivityRecognition.getClient(context)
                .requestActivityUpdates(AR_UPDATE_INTERVAL_MS, activityRecognitionPendingIntent(context))
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopActivityRecognitionUpdates(context: Context) {
        try {
            ActivityRecognition.getClient(context)
                .removeActivityUpdates(activityRecognitionPendingIntent(context))
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context, repo: ExploreRepository) {
        if (locationCallback != null) {
            return
        }

        val client = getClient(context)

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isEnabled || isNightTime()) {
                    stop(context)
                    return
                }
                val loc = result.lastLocation ?: return

                val distanceHome = distanceToHome(loc.latitude, loc.longitude)
                _trackerInfo.value = _trackerInfo.value.copy(
                    lastLat = loc.latitude,
                    lastLng = loc.longitude,
                    lastUpdate = Instant.now().toString(),
                    distanceToHomeMeters = distanceHome,
                )

                val activityInfo = _currentActivity.value

                if (loc.accuracy > MAX_ACCURACY_METERS) {
                    logEvent(
                        context, "REJECT_ACCURACY", activityInfo.mode, activityInfo.confidence,
                        "acc=%.1f limit=%.1f".format(loc.accuracy, MAX_ACCURACY_METERS)
                    )
                    return
                }

                val previous = lastLocation
                val deltaSeconds = if (previous != null) (loc.time - previous.time) / 1000.0 else 0.0
                val distance = previous?.distanceTo(loc) ?: 0f

                val measuredSpeed = if (loc.hasSpeed()) loc.speed.toDouble() else null
                val impliedSpeed = if (deltaSeconds >= MIN_IMPLIED_SPEED_INTERVAL_S) distance / deltaSeconds else null
                val speedSource = if (measuredSpeed != null) "doppler" else "implied"
                val speed = measuredSpeed ?: impliedSpeed

                if (speed != null) {
                    val ceiling = if (activityInfo.confidence >= AR_CEILING_CONFIDENCE_FLOOR && !activityInfo.held) {
                        modeSpeedCeiling(activityInfo.mode)
                    } else {
                        UNIVERSAL_SPEED_CEILING_MPS
                    }
                    if (speed > ceiling) {
                        logEvent(
                            context, "REJECT_SPEED", activityInfo.mode, activityInfo.confidence,
                            "v=%.1f quelle=%s ceiling=%.1f dt=%.1f dist=%.1f acc=%.1f".format(
                                speed, speedSource, ceiling, deltaSeconds, distance, loc.accuracy
                            )
                        )
                        return
                    }
                }

                lastLocation = loc
                scope.launch {
                    try {
                        repo.ingest(
                            RawPoint(
                                lat = loc.latitude,
                                lon = loc.longitude,
                                accuracy = loc.accuracy,
                                speed = if (loc.hasSpeed()) loc.speed else null,
                                bearing = if (loc.hasBearing()) loc.bearing else null,
                                activityType = _currentActivity.value.mode,
                                activityConfidence = _currentActivity.value.confidence,
                                timestamp = System.currentTimeMillis(),
                                altitude = usableAltitude(loc)
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        locationCallback = callback
        try {
            client.requestLocationUpdates(locationRequestFor(currentMode), callback, Looper.getMainLooper())
                .addOnSuccessListener {}
                .addOnFailureListener {}
        } catch (e: Exception) {
            _trackerStatus.value = "Fehler: ${e.message}"
        }
    }

    fun onServiceStarted(context: Context) {
        val appCtx = context.applicationContext
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        startLocationUpdates(appCtx, ExploreRepository(appCtx))
        startActivityRecognitionUpdates(appCtx)
        ExploreWorker.schedule(appCtx)
        _trackerStatus.value = "Läuft aktiv"
    }

    fun onServiceStartDenied() {
        isEnabled = false
        _trackerStatus.value = "Inaktiv (FGS nicht gestartet)"
        _trackerInfo.value = _trackerInfo.value.copy(isEnabled = false)
    }

    fun onServiceDestroyed(context: Context) {
        scope.cancel()
        val appCtx = context.applicationContext
        locationCallback?.let {
            try {
                getClient(appCtx).removeLocationUpdates(it)
            } catch (_: Exception) {
            }
        }
        stopActivityRecognitionUpdates(appCtx)
        // Intentionally NOT cancelling ExploreWorker here: it should keep running as a
        // watchdog if the service died unexpectedly (OS/OEM kill), instead of only being
        // switched off on the next deliberate stop().
        locationCallback = null
        lastLocation = null
        currentMode = "UNKNOWN"
        currentProfile = requestProfile("UNKNOWN")
        lastConfidentMode = "UNKNOWN"
        lastConfidentAt = 0L
        _currentActivity.value = ExploreActivityInfo()
        // Reset isEnabled here too, otherwise the stale flag permanently blocks every
        // future start() call (geofence/resume) until the app is restarted.
        isEnabled = false
        _trackerInfo.value = _trackerInfo.value.copy(isEnabled = false)
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        isEnabled = false
        _trackerInfo.value = _trackerInfo.value.copy(isEnabled = false)

        try {
            LocationServices.getGeofencingClient(appCtx).removeGeofences(listOf(GEOFENCE_ID))
        } catch (_: Exception) {
        }
        locationCallback?.let {
            try {
                getClient(appCtx).removeLocationUpdates(it)
            } catch (_: Exception) {
            }
        }
        stopActivityRecognitionUpdates(appCtx)
        ExploreWorker.cancel(appCtx)

        try {
            appCtx.stopService(Intent(appCtx, ExploreForegroundService::class.java))
        } catch (_: Exception) {
        }

        if (isNightTime()) {
            _trackerStatus.value = "Pausiert (Nacht)"
            _trackerInfo.value = _trackerInfo.value.copy(isNight = true)
            ExploreNightRestartWorker.schedule(appCtx)
        } else if (_trackerStatus.value != "Gestoppt (Zuhause)") {
            _trackerStatus.value = "Inaktiv"
        }
    }
}
