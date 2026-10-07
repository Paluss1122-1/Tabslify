package com.tabslify.tabs.exploretab

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.DialogTabslify
import com.tabslify.core.ui.NeonBox
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor

@Composable
fun ExploreTabContent(setGesturesEnabled: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: ExploreViewModel = viewModel()

    val tileCount by vm.tileCount.collectAsStateWithLifecycle()
    val exploredPercent by vm.exploredPercent.collectAsStateWithLifecycle()
    val tiles by vm.allTiles.collectAsStateWithLifecycle()
    val trackerStatus by ExploreLocationTracker.trackerStatus.collectAsStateWithLifecycle()
    val trackerInfo by ExploreLocationTracker.trackerInfo.collectAsStateWithLifecycle()
    val currentActivity by ExploreLocationTracker.currentActivity.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val daySegments by vm.daySegments.collectAsStateWithLifecycle()
    val dayStays by vm.dayStays.collectAsStateWithLifecycle()
    val dayPoints by vm.dayPoints.collectAsStateWithLifecycle()
    val totalDistanceKm by vm.totalDistanceKm.collectAsStateWithLifecycle()
    val timeMovingMs by vm.timeMovingMs.collectAsStateWithLifecycle()
    val exportStatus by vm.exportStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var tileToDelete by remember { mutableStateOf<ExploredTile?>(null) }
    var showDebug by remember { mutableStateOf(false) }
    var initialCenterDone by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ExploreLocationTracker.start(ctx)
            if (!initialCenterDone) {
                try {
                    LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                initialCenterDone = true
                            }
                        }
                } catch (_: SecurityException) {
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    ) {
                        ExploreLocationTracker.start(ctx)
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    LaunchedEffect(Unit) {
        setGesturesEnabled(false)
        try {
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) mapView?.controller?.animateTo(
                        GeoPoint(loc.latitude, loc.longitude)
                    )
                }
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            val today = remember { LocalDate.now() }
            val isToday = selectedDate == today
            val todayLabel = stringResource(R.string.heute)
            val gesternLabel = stringResource(R.string.gestern)
            val dayLabel = remember(selectedDate, todayLabel, gesternLabel) {
                when (selectedDate) {
                    today -> todayLabel
                    today.minusDays(1) -> gesternLabel
                    else -> selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonIconButton(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.zuruck),
                    onClick = { vm.previousDay() }
                )
                Text(
                    text = dayLabel,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                NeonIconButton(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.weiter),
                    enabled = !isToday,
                    onClick = { vm.nextDay() }
                )
                Spacer(Modifier.width(8.dp))
                NeonIconButton(
                    imageVector = Icons.Filled.FileDownload,
                    contentDescription = stringResource(R.string.explore_export_tag),
                    onClick = { vm.exportData(1) }
                )
                NeonIconButton(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = stringResource(R.string.explore_export_7_tage),
                    onClick = { vm.exportData(7) }
                )
                NeonIconButton(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = stringResource(R.string.tracker_status),
                    onClick = { showDebug = true }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(stringResource(R.string.strecke), "%.1f km".format(totalDistanceKm), Modifier.weight(1f))
                StatChip(stringResource(R.string.unterwegs), formatDuration(timeMovingMs), Modifier.weight(1f))
                StatChip(stringResource(R.string.stopps), dayStays.size.toString(), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(stringResource(R.string.tiles), tileCount.toString(), Modifier.weight(1f))
                StatChip(stringResource(R.string.erkundet), "%.8f%%".format(exploredPercent), Modifier.weight(1f))
                StatChip(stringResource(R.string.heute_2), vm.todayCount.toString(), Modifier.weight(1f))
            }

            StatChip(
                label = stringResource(R.string.tracker_status),
                value = trackerStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp)
            )

            exportStatus?.let { status ->
                Text(
                    text = status,
                    color = Color(0xFF9090A0),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .clipToBounds()
            ) {
                AndroidView(
                    factory = { context ->
                        val loc = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                Tasks.await(
                                    LocationServices.getFusedLocationProviderClient(context).lastLocation,
                                    1, TimeUnit.SECONDS
                                )
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        val center = if (loc != null) GeoPoint(loc.latitude, loc.longitude)
                        else GeoPoint(Config.LAT, Config.LON)

                        Configuration.getInstance().userAgentValue = context.packageName
                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            controller.setCenter(center)
                            isTilesScaledToDpi = false
                            setScrollableAreaLimitDouble(null)
                        }.also { mapView = it }
                    },
                    update = { mv ->
                        mv.overlays.removeAll(
                            mv.overlays.filterIsInstance<ExploreOverlay>().toSet()
                        )
                        if (tiles.isNotEmpty()) {
                            mv.overlays.add(
                                0,
                                ExploreOverlay(tiles) { tile -> tileToDelete = tile })
                        }

                        mv.overlays.removeAll(
                            mv.overlays.filterIsInstance<Polyline>().toSet()
                        )
                        daySegments.forEach { segment ->
                            val segmentPoints = dayPoints.filter { it.timestamp in segment.startTime..segment.endTime }
                            if (segmentPoints.size >= 2) {
                                mv.overlays.add(Polyline().apply {
                                    setPoints(segmentPoints.map { GeoPoint(it.lat, it.lon) })
                                    outlinePaint.color = colorForMode(segment.mode)
                                    outlinePaint.strokeWidth = 8f
                                    outlinePaint.isAntiAlias = true
                                })
                            }
                        }

                        mv.overlays.removeAll(
                            mv.overlays.filterIsInstance<ExploreStayOverlay>().toSet()
                        )
                        if (dayStays.isNotEmpty()) {
                            mv.overlays.add(ExploreStayOverlay(dayStays))
                        }

                        mv.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            val timeline = remember(daySegments, dayStays) {
                (daySegments.map { TimelineItem.SegmentItem(it) } + dayStays.map { TimelineItem.StayItem(it) })
                    .sortedBy { it.startTime }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(timeline, key = { it.startTime }) { item ->
                    when (item) {
                        is TimelineItem.SegmentItem -> SegmentCard(item.segment)
                        is TimelineItem.StayItem -> StayCard(item.stay)
                    }
                }
            }
        }
        tileToDelete?.let { tile ->
            AlertDialogTabslify(
                onConfirm = {
                    scope.launch { vm.deleteTile(tile.tileX, tile.tileY) }
                    tileToDelete = null
                },
                onDismiss = { tileToDelete = null },
                icon = { Text("🗺️", fontSize = 24.sp) },
                title = stringResource(R.string.tile_loschen),
                text = stringResource(R.string.diesen_bereich_als_unbesucht_markieren)
            )
        }
        if (showDebug) {
            DialogTabslify(
                onConfirm = { showDebug = false },
                onDismiss = { showDebug = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = stringResource(R.string.tracker_status),
                text = trackerDebugText(trackerInfo, currentActivity, trackerStatus),
                confirmText = stringResource(R.string.verstanden),
                oneButton = true
            )
        }
    }
}

private sealed class TimelineItem(val startTime: Long) {
    class SegmentItem(val segment: Segment) : TimelineItem(segment.startTime)
    class StayItem(val stay: Stay) : TimelineItem(stay.startTime)
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private fun modeIcon(mode: String): String = when (mode) {
    "IN_VEHICLE" -> "🚗"
    "ON_EBIKE" -> "🚴⚡"
    "ON_BICYCLE" -> "🚲"
    "WALKING", "ON_FOOT" -> "🚶"
    else -> "❔"
}

private fun segmentAccentColor(mode: String): Color = when (mode) {
    "IN_VEHICLE" -> Color(0xFF00CCFF)
    "ON_EBIKE" -> Color(0xFFB388FF)
    "ON_BICYCLE" -> Color(0xFF00FFAA)
    "WALKING", "ON_FOOT" -> Color(0xFFFFAB00)
    else -> Color(0xFF9090A0)
}

private fun colorForMode(mode: String): Int = when (mode) {
    "IN_VEHICLE" -> android.graphics.Color.rgb(0, 204, 255)
    "ON_EBIKE" -> android.graphics.Color.rgb(179, 136, 255)
    "ON_BICYCLE" -> android.graphics.Color.rgb(0, 255, 170)
    "WALKING", "ON_FOOT" -> android.graphics.Color.rgb(255, 171, 0)
    else -> android.graphics.Color.rgb(144, 144, 160)
}

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    "IN_VEHICLE" -> stringResource(R.string.modus_auto)
    "ON_EBIKE" -> stringResource(R.string.modus_ebike)
    "ON_BICYCLE" -> stringResource(R.string.modus_fahrrad)
    "WALKING", "ON_FOOT" -> stringResource(R.string.modus_zu_fuss)
    else -> stringResource(R.string.modus_unbekannt)
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun timeRangeLabel(start: Long, end: Long): String {
    val startLabel = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).format(timeFormatter)
    val endLabel = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).format(timeFormatter)
    return "$startLabel – $endLabel"
}

@Composable
private fun SegmentCard(segment: Segment, modifier: Modifier = Modifier) {
    NeonBox(
        modifier = modifier.fillMaxWidth(),
        borderWidth = 2.dp,
        neonColors = listOf(segmentAccentColor(segment.mode), Color(0xFF00CCFF)),
        backgroundAlpha = 0.15f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = modeIcon(segment.mode), fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(text = modeLabel(segment.mode), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(text = timeRangeLabel(segment.startTime, segment.endTime), color = Color(0xFF9090A0), fontSize = 11.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "%.1f km".format(segment.distanceMeters / 1000f), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = formatDuration(segment.endTime - segment.startTime), color = Color(0xFF9090A0), fontSize = 11.sp)
                if (ExploreBikeClassifier.isBikeMode(segment.mode)) {
                    val hours = (segment.endTime - segment.startTime) / 3_600_000f
                    if (hours > 0f) {
                        Text(
                            text = "Ø %.1f km/h".format(segment.distanceMeters / 1000f / hours),
                            color = segmentAccentColor(segment.mode),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StayCard(stay: Stay, modifier: Modifier = Modifier) {
    NeonBox(
        modifier = modifier.fillMaxWidth(),
        borderWidth = 2.dp,
        neonColors = if (stay.isHome) listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)) else listOf(Color(0xFF9090A0), Color(0xFF606070)),
        backgroundAlpha = 0.12f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (stay.isHome) "🏠" else "📍", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stay.placeName ?: stringResource(R.string.modus_aufenthalt),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(text = timeRangeLabel(stay.startTime, stay.endTime), color = Color(0xFF9090A0), fontSize = 11.sp)
                }
            }
            Text(text = formatDuration(stay.endTime - stay.startTime), color = Color(0xFF9090A0), fontSize = 11.sp)
        }
    }
}

@Composable
private fun NeonIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    NeonBox(
        modifier = modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.3f),
        cornerRadius = RoundedCornerShape(12.dp),
        borderWidth = 2.dp,
        neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
        backgroundAlpha = 0.22f,
        onClick = onClick
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    NeonBox(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(14.dp),
        borderWidth = 2.dp,
        neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
        backgroundAlpha = 0.22f,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFF9090A0),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun trackerDebugText(
    info: ExploreTrackerInfo,
    activity: ExploreActivityInfo,
    status: String
): String = listOf(
    "${stringResource(R.string.tracker_status)}: $status",
    "${stringResource(R.string.zuhause)}: ${"%.6f, %.6f".format(info.homeLat, info.homeLng)}",
    "${stringResource(R.string.letzte_position)}: ${
        if (info.lastLat != null && info.lastLng != null)
            "%.6f, %.6f".format(info.lastLat, info.lastLng)
        else "—"
    }",
    "${stringResource(R.string.distanz_zuhause)}: ${info.distanceToHomeMeters?.let { "%.0f m".format(it) } ?: "—"}",
    "${stringResource(R.string.letztes_update)}: ${info.lastUpdate ?: "—"}",
    "${stringResource(R.string.geofence)}: ${
        if (info.geofenceRegistered) stringResource(R.string.aktiv)
        else info.geofenceError?.let { stringResource(R.string.fehler_msg, it) }
            ?: stringResource(R.string.nicht_registriert)
    }",
    "${stringResource(R.string.heim_wlan)}: ${
        info.lastWifiHome?.let {
            if (it) stringResource(R.string.verbunden) else stringResource(R.string.nicht_verbunden)
        } ?: "—"
    }",
    "${stringResource(R.string.netz_typ)}: ${info.lastNetwork ?: "—"}",
    "${stringResource(R.string.letzter_check)}: ${info.lastRecheckAt ?: "—"}",
    "${stringResource(R.string.nachtmodus)}: ${if (info.isNight) stringResource(R.string.ja) else stringResource(R.string.nein)}",
    "${stringResource(R.string.enabled)}: ${if (info.isEnabled) stringResource(R.string.ja) else stringResource(R.string.nein)}",
    "${stringResource(R.string.aktivitaet)}: ${modeLabel(activity.mode)} (${activity.confidence}%)"
).joinToString("\n")

private class ExploreOverlay(
    private val tiles: List<ExploredTile>,
    private val onLongPress: (ExploredTile) -> Unit
) : Overlay() {
    private val fillPaint = Paint().apply {
        color = android.graphics.Color.argb(100, 100, 149, 237)  // Etwas sichtbarer
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = android.graphics.Color.argb(180, 70, 120, 220)  // Stärkere Umrandung
        style = Paint.Style.STROKE
        strokeWidth = 2f  // Dickere Linien für größere Tiles
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val bbox = mapView.boundingBox

        val minX = floor(bbox.latSouth / TILE_SIZE).toLong() - 1
        val maxX = floor(bbox.latNorth / TILE_SIZE).toLong() + 1
        val minY = floor(bbox.lonWest / TILE_SIZE).toLong() - 1
        val maxY = floor(bbox.lonEast / TILE_SIZE).toLong() + 1

        tiles.asSequence()
            .filter { it.tileX in minX..maxX && it.tileY in minY..maxY }
            .take(5000)
            .forEach { tile ->
                val lat0 = tile.tileX * TILE_SIZE
                val lon0 = tile.tileY * TILE_SIZE
                val p0 = projection.toPixels(GeoPoint(lat0, lon0), null)
                val p1 = projection.toPixels(GeoPoint(lat0 + TILE_SIZE, lon0 + TILE_SIZE), null)

                val left = minOf(p0.x, p1.x).toFloat()
                val top = minOf(p0.y, p1.y).toFloat()
                val right = maxOf(p0.x, p1.x).toFloat()
                val bottom = maxOf(p0.y, p1.y).toFloat()

                canvas.drawRect(left, top, right, bottom, fillPaint)
                canvas.drawRect(left, top, right, bottom, strokePaint)
            }
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val geo = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        val tx = floor(geo.latitude / TILE_SIZE).toLong()
        val ty = floor(geo.longitude / TILE_SIZE).toLong()
        val hit = tiles.find { it.tileX == tx && it.tileY == ty }
        if (hit != null) {
            onLongPress(hit)
            return true
        }
        return false
    }
}

private class ExploreStayOverlay(private val stays: List<Stay>) : Overlay() {
    private val homePaint = Paint().apply {
        color = android.graphics.Color.argb(230, 0, 255, 170)
        style = Paint.Style.FILL
    }
    private val otherPaint = Paint().apply {
        color = android.graphics.Color.argb(230, 144, 144, 160)
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        stays.forEach { stay ->
            val pixel = projection.toPixels(GeoPoint(stay.centerLat, stay.centerLon), null)
            canvas.drawCircle(pixel.x.toFloat(), pixel.y.toFloat(), 16f, if (stay.isHome) homePaint else otherPaint)
        }
    }
}
