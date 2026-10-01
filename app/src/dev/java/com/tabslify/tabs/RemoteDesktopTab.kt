package com.tabslify.tabs

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.net.wifi.WifiManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tabslify.R
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.AppBackground
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import uniffi.ironrdp_bridge.ConnectOptions
import uniffi.ironrdp_bridge.MouseButton
import uniffi.ironrdp_bridge.RdpSession
import uniffi.ironrdp_bridge.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val STANDARD_IP = "192.168.178.72"
private const val RDP_PORT = 3389
private const val DATEN_DATEI = "remote_desktop"
private const val DOPPELKLICK_MS = 350L
private const val NEUVERBINDUNG_PAUSE_MS = 2000L
private const val MAX_NEUVERBINDUNGEN = 3
private const val TAG = "CLOUDSA"

private val trenner = Executors.newCachedThreadPool { laufzeit ->
    Thread(laufzeit, "rdp-trennen").apply { isDaemon = true }
}

private class RdpVerbindung(val session: RdpSession) {
    private val beendet = AtomicBoolean(false)

    val aktiv: Boolean get() = !beendet.get()

    fun beenden() {
        if (!beendet.compareAndSet(false, true)) {
            return
        }
        trenner.execute {
            runCatching { session.disconnect() }
            runCatching { session.close() }
        }
    }
}

private class PixelPuffer {
    private var werte: IntArray = IntArray(0)

    fun fuer(anzahl: Int): IntArray {
        if (werte.size != anzahl) {
            werte = IntArray(anzahl)
        }
        return werte
    }
}

private data class TastenInfo(val scancode: Int, val erweitert: Boolean)

private fun tastenInfo(keyCode: Int): TastenInfo? = when (keyCode) {
    KeyEvent.KEYCODE_ESCAPE -> TastenInfo(0x01, false)
    KeyEvent.KEYCODE_1 -> TastenInfo(0x02, false)
    KeyEvent.KEYCODE_2 -> TastenInfo(0x03, false)
    KeyEvent.KEYCODE_3 -> TastenInfo(0x04, false)
    KeyEvent.KEYCODE_4 -> TastenInfo(0x05, false)
    KeyEvent.KEYCODE_5 -> TastenInfo(0x06, false)
    KeyEvent.KEYCODE_6 -> TastenInfo(0x07, false)
    KeyEvent.KEYCODE_7 -> TastenInfo(0x08, false)
    KeyEvent.KEYCODE_8 -> TastenInfo(0x09, false)
    KeyEvent.KEYCODE_9 -> TastenInfo(0x0A, false)
    KeyEvent.KEYCODE_0 -> TastenInfo(0x0B, false)
    KeyEvent.KEYCODE_MINUS -> TastenInfo(0x0C, false)
    KeyEvent.KEYCODE_EQUALS -> TastenInfo(0x0D, false)
    KeyEvent.KEYCODE_DEL -> TastenInfo(0x0E, false)
    KeyEvent.KEYCODE_TAB -> TastenInfo(0x0F, false)
    KeyEvent.KEYCODE_Q -> TastenInfo(0x10, false)
    KeyEvent.KEYCODE_W -> TastenInfo(0x11, false)
    KeyEvent.KEYCODE_E -> TastenInfo(0x12, false)
    KeyEvent.KEYCODE_R -> TastenInfo(0x13, false)
    KeyEvent.KEYCODE_T -> TastenInfo(0x14, false)
    KeyEvent.KEYCODE_Y -> TastenInfo(0x15, false)
    KeyEvent.KEYCODE_U -> TastenInfo(0x16, false)
    KeyEvent.KEYCODE_I -> TastenInfo(0x17, false)
    KeyEvent.KEYCODE_O -> TastenInfo(0x18, false)
    KeyEvent.KEYCODE_P -> TastenInfo(0x19, false)
    KeyEvent.KEYCODE_LEFT_BRACKET -> TastenInfo(0x1A, false)
    KeyEvent.KEYCODE_RIGHT_BRACKET -> TastenInfo(0x1B, false)
    KeyEvent.KEYCODE_ENTER -> TastenInfo(0x1C, false)
    KeyEvent.KEYCODE_NUMPAD_ENTER -> TastenInfo(0x1C, true)
    KeyEvent.KEYCODE_CTRL_LEFT -> TastenInfo(0x1D, true)
    KeyEvent.KEYCODE_CTRL_RIGHT -> TastenInfo(0x1D, true)
    KeyEvent.KEYCODE_A -> TastenInfo(0x1E, false)
    KeyEvent.KEYCODE_S -> TastenInfo(0x1F, false)
    KeyEvent.KEYCODE_D -> TastenInfo(0x20, false)
    KeyEvent.KEYCODE_F -> TastenInfo(0x21, false)
    KeyEvent.KEYCODE_G -> TastenInfo(0x22, false)
    KeyEvent.KEYCODE_H -> TastenInfo(0x23, false)
    KeyEvent.KEYCODE_J -> TastenInfo(0x24, false)
    KeyEvent.KEYCODE_K -> TastenInfo(0x25, false)
    KeyEvent.KEYCODE_L -> TastenInfo(0x26, false)
    KeyEvent.KEYCODE_SEMICOLON -> TastenInfo(0x27, false)
    KeyEvent.KEYCODE_APOSTROPHE -> TastenInfo(0x28, false)
    KeyEvent.KEYCODE_GRAVE -> TastenInfo(0x29, false)
    KeyEvent.KEYCODE_SHIFT_LEFT -> TastenInfo(0x2A, false)
    KeyEvent.KEYCODE_BACKSLASH -> TastenInfo(0x2B, false)
    KeyEvent.KEYCODE_Z -> TastenInfo(0x2C, false)
    KeyEvent.KEYCODE_X -> TastenInfo(0x2D, false)
    KeyEvent.KEYCODE_C -> TastenInfo(0x2E, false)
    KeyEvent.KEYCODE_V -> TastenInfo(0x2F, false)
    KeyEvent.KEYCODE_B -> TastenInfo(0x30, false)
    KeyEvent.KEYCODE_N -> TastenInfo(0x31, false)
    KeyEvent.KEYCODE_M -> TastenInfo(0x32, false)
    KeyEvent.KEYCODE_COMMA -> TastenInfo(0x33, false)
    KeyEvent.KEYCODE_PERIOD -> TastenInfo(0x34, false)
    KeyEvent.KEYCODE_SLASH -> TastenInfo(0x35, false)
    KeyEvent.KEYCODE_SHIFT_RIGHT -> TastenInfo(0x36, false)
    KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> TastenInfo(0x37, false)
    KeyEvent.KEYCODE_ALT_LEFT -> TastenInfo(0x38, true)
    KeyEvent.KEYCODE_ALT_RIGHT -> TastenInfo(0x38, true)
    KeyEvent.KEYCODE_SPACE -> TastenInfo(0x39, false)
    KeyEvent.KEYCODE_CAPS_LOCK -> TastenInfo(0x3A, false)
    KeyEvent.KEYCODE_F1 -> TastenInfo(0x3B, false)
    KeyEvent.KEYCODE_F2 -> TastenInfo(0x3C, false)
    KeyEvent.KEYCODE_F3 -> TastenInfo(0x3D, false)
    KeyEvent.KEYCODE_F4 -> TastenInfo(0x3E, false)
    KeyEvent.KEYCODE_F5 -> TastenInfo(0x3F, false)
    KeyEvent.KEYCODE_F6 -> TastenInfo(0x40, false)
    KeyEvent.KEYCODE_F7 -> TastenInfo(0x41, false)
    KeyEvent.KEYCODE_F8 -> TastenInfo(0x42, false)
    KeyEvent.KEYCODE_F9 -> TastenInfo(0x43, false)
    KeyEvent.KEYCODE_F10 -> TastenInfo(0x44, false)
    KeyEvent.KEYCODE_NUM_LOCK -> TastenInfo(0x45, true)
    KeyEvent.KEYCODE_SCROLL_LOCK -> TastenInfo(0x46, false)
    KeyEvent.KEYCODE_MOVE_HOME -> TastenInfo(0x47, true)
    KeyEvent.KEYCODE_DPAD_UP -> TastenInfo(0x48, true)
    KeyEvent.KEYCODE_PAGE_UP -> TastenInfo(0x49, true)
    KeyEvent.KEYCODE_DPAD_LEFT -> TastenInfo(0x4B, true)
    KeyEvent.KEYCODE_DPAD_RIGHT -> TastenInfo(0x4D, true)
    KeyEvent.KEYCODE_MOVE_END -> TastenInfo(0x4F, true)
    KeyEvent.KEYCODE_DPAD_DOWN -> TastenInfo(0x50, true)
    KeyEvent.KEYCODE_PAGE_DOWN -> TastenInfo(0x51, true)
    KeyEvent.KEYCODE_INSERT -> TastenInfo(0x52, true)
    KeyEvent.KEYCODE_NUMPAD_DOT -> TastenInfo(0x52, true)
    KeyEvent.KEYCODE_DEL -> TastenInfo(0x53, true)
    KeyEvent.KEYCODE_F11 -> TastenInfo(0x57, true)
    KeyEvent.KEYCODE_F12 -> TastenInfo(0x58, true)
    KeyEvent.KEYCODE_NUMPAD_ADD -> TastenInfo(0x4E, false)
    KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> TastenInfo(0x4A, false)
    else -> null
}

private fun bildFlaeche(rahmen: IntSize, bild: IntSize): Pair<Int, Int> {
    if (rahmen.width <= 0 || rahmen.height <= 0 || bild.width <= 0 || bild.height <= 0) {
        return 0 to 0
    }
    val faktor = minOf(
        rahmen.width.toFloat() / bild.width.toFloat(),
        rahmen.height.toFloat() / bild.height.toFloat()
    )
    return (bild.width * faktor).roundToInt() to (bild.height * faktor).roundToInt()
}

@Composable
fun RemoteDesktopTabContent() {
    val kontext = LocalContext.current
    val verboten = stringResource(R.string.forbidden)
    if (!prvt()) {
        Toast.makeText(kontext, verboten, Toast.LENGTH_SHORT).show()
        return
    }

    val daten = remember { kontext.getSharedPreferences(DATEN_DATEI, Context.MODE_PRIVATE) }
    var ip by remember { mutableStateOf(daten.getString("ip", STANDARD_IP) ?: STANDARD_IP) }
    var benutzer by remember { mutableStateOf(daten.getString("benutzer", "paul") ?: "paul") }
    var passwort by remember { mutableStateOf(daten.getString("passwort", "") ?: "") }
    var merken by remember { mutableStateOf(daten.getBoolean("merken", false)) }
    var verbindung by remember { mutableStateOf<RdpVerbindung?>(null) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var letzteOptionen by remember { mutableStateOf<ConnectOptions?>(null) }
    var neuverbindungen by remember { mutableIntStateOf(0) }
    val wifiDienst = remember { kontext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    val konfiguration = LocalConfiguration.current

    DisposableEffect(verbindung) {
        val aktuelleVerbindung = verbindung
        onDispose {
            aktuelleVerbindung?.beenden()
        }
    }

    val laufendeVerbindung = verbindung
    if (laufendeVerbindung == null) {
        AppBackground(Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = AccentViolet
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.remote_desktop),
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = stringResource(R.string.rdp_hinweis_nativ),
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text(stringResource(R.string.rdp_ip)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        value = benutzer,
                        onValueChange = { benutzer = it },
                        label = { Text(stringResource(R.string.rdp_benutzername)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passwort,
                        onValueChange = { passwort = it },
                        label = { Text(stringResource(R.string.rdp_passwort)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = merken, onCheckedChange = { merken = it })
                        Text(
                            text = stringResource(R.string.rdp_passwort_merken),
                            color = TextSecondary
                        )
                    }
                    meldung?.let { text ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = text, color = Color(0xFFFF6B6B))
                        }
                    }
                    Button(
                        onClick = {
                            meldung = null
                            if (ip.isBlank() || benutzer.isBlank() || passwort.isEmpty()) {
                                meldung = kontext.getString(R.string.rdp_fehler_angaben)
                                return@Button
                            }
                            daten.edit {
                                putString("ip", ip.trim())
                                    .putString("benutzer", benutzer.trim())
                                    .putBoolean("merken", merken)
                                    .putString("passwort", if (merken) passwort else "")
                            }
                            val breite = (konfiguration.screenWidthDp * 2).coerceIn(800, 3840)
                            val hoehe = (breite * konfiguration.screenHeightDp.toFloat() / konfiguration.screenWidthDp.toFloat())
                                .roundToInt()
                                .coerceIn(600, 2160)
                            val optionen = ConnectOptions(
                                host = ip.trim(),
                                port = RDP_PORT.toUShort(),
                                username = benutzer.trim(),
                                password = passwort,
                                width = breite.toUShort(),
                                height = hoehe.toUShort()
                            )
                            letzteOptionen = optionen
                            neuverbindungen = 0
                            Log.i(
                                TAG,
                                "[RDP] Verbindungsversuch " +
                                    "${kontext.getString(R.string.verbinden)}: " +
                                    "${optionen.host}:${optionen.port} als ${optionen.username}, " +
                                    "${optionen.width}x${optionen.height}"
                            )
                            verbindung = RdpVerbindung(RdpSession.start(optionen))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                    ) {
                        Text(stringResource(R.string.verbinden), color = TextPrimary)
                    }
                }
            }
        }
        return
    }

    DisposableEffect(laufendeVerbindung) {
        val lock = wifiDienst
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "tabslify-rdp")
            ?.also { it.acquire() }
        onDispose {
            if (lock?.isHeld == true) {
                runCatching { lock.release() }
            }
        }
    }

    Bildschirm(
        verbindung = laufendeVerbindung,
        zielIp = ip.trim(),
        onTrennen = { verbindung = null },
        onNeuverbinden = {
            val optionen = letzteOptionen
            if (optionen != null && neuverbindungen < MAX_NEUVERBINDUNGEN) {
                neuverbindungen++
                Log.i(
                    TAG,
                    "[RDP] App-Neuaufbau ${neuverbindungen + 1}/$MAX_NEUVERBINDUNGEN zu " +
                        "${optionen.host}:${optionen.port}; letzter Fehler: " +
                        (verbindung?.session?.lastError()?.ifBlank { "keiner" } ?: "unbekannt")
                )
                verbindung?.beenden()
                verbindung = RdpVerbindung(RdpSession.start(optionen))
            } else {
                Log.w(TAG, "[RDP] Neuaufbau aufgegeben, Meldung anzeigen")
                verbindung = null
                meldung = kontext.getString(R.string.rdp_neuverbinden_fehlgeschlagen)
            }
        }
    )
}

@Composable
private fun Bildschirm(
    verbindung: RdpVerbindung,
    zielIp: String,
    onTrennen: () -> Unit,
    onNeuverbinden: () -> Unit
) {
    val sitzung = verbindung.session
    var bild by remember { mutableStateOf<Bitmap?>(null) }
    var bildGroesse by remember { mutableStateOf(IntSize.Zero) }
    var zaehler by remember { mutableIntStateOf(0) }
    var zustand by remember(verbindung) { mutableStateOf(SessionState.CONNECTING) }
    var zustandsMeldung by remember(verbindung) { mutableStateOf("") }
    var vollbild by remember { mutableStateOf(false) }
    var tastaturOffen by remember { mutableStateOf(false) }
    var tastaturText by remember { mutableStateOf("") }
    var rahmen by remember { mutableStateOf(IntSize.Zero) }
    val tastaturAnker = remember { FocusRequester() }
    val diagnose = remember { mutableStateOf("") }
    var letztFrameMs by remember { mutableLongStateOf(0L) }
    var diagnoseMs by remember { mutableLongStateOf(0L) }
    var konvertierMs by remember { mutableLongStateOf(0L) }
    var letzterBerichtMs by remember { mutableLongStateOf(0L) }
    var zeigerX by remember { mutableFloatStateOf(0f) }
    var zeigerY by remember { mutableFloatStateOf(0f) }
    var zeigerDa by remember { mutableStateOf(false) }
    val ansicht = LocalView.current
    val pixelPuffer = remember { PixelPuffer() }

    DisposableEffect(ansicht) {
        ansicht.keepScreenOn = true
        onDispose { ansicht.keepScreenOn = false }
    }

    LaunchedEffect(verbindung) {
        var diagnoseZeit = System.currentTimeMillis()
        while (isActive && verbindung.aktiv) {
            val aufrufStart = System.currentTimeMillis()
            val aktualisierung = runCatching { sitzung.takeFrame() }.getOrNull()
            if (aktualisierung != null) {
                val vorhanden = bild
                val konvertierStart = System.currentTimeMillis()
                val ziel = withContext(Dispatchers.Default) {
                    val puffer = vorhanden
                        ?.takeIf {
                            it.width == aktualisierung.width.toInt() &&
                                it.height == aktualisierung.height.toInt()
                        }
                        ?: createBitmap(aktualisierung.width.toInt(), aktualisierung.height.toInt())
                    for (region in aktualisierung.regions) {
                        val breite = region.width.toInt()
                        val hoehe = region.height.toInt()
                        val startX = region.x.toInt()
                        val startY = region.y.toInt()
                        if (region.argb.isEmpty()) {
                            puffer.eraseColor(android.graphics.Color.BLACK)
                            continue
                        }
                        if (breite <= 0 || hoehe <= 0 || startX < 0 || startY < 0 ||
                            startX + breite > puffer.width || startY + hoehe > puffer.height ||
                            region.argb.size < breite * hoehe * 4
                        ) {
                            continue
                        }
                        val pixel = pixelPuffer.fuer(breite * hoehe)
                        java.nio.ByteBuffer.wrap(region.argb).asIntBuffer().get(pixel)
                        puffer.setPixels(
                            pixel,
                            0,
                            breite,
                            startX,
                            startY,
                            breite,
                            hoehe
                        )
                    }
                    puffer
                }
                konvertierMs = System.currentTimeMillis() - konvertierStart
                bildGroesse = IntSize(aktualisierung.width.toInt(), aktualisierung.height.toInt())
                if (ziel !== vorhanden) {
                    bild = ziel
                }
                zaehler++
                val jetzt = System.currentTimeMillis()
                val abstand = if (letztFrameMs == 0L) 0L else jetzt - letztFrameMs
                letztFrameMs = jetzt
                val kb = aktualisierung.regions.sumOf { it.argb.size } / 1024
                Log.d(
                    TAG,
                    "[RDP] Frame $zaehler: ${aktualisierung.regions.size} Regionen, " +
                        "${aktualisierung.width}x${aktualisierung.height}, $kb kB, " +
                        "Abstand ${abstand}ms, Konvertierung ${konvertierMs}ms, " +
                        "Aufruf ${System.currentTimeMillis() - aufrufStart}ms"
                )
            } else {
                delay(8.milliseconds)
            }
            val neuerZustand = runCatching { sitzung.state() }.getOrNull() ?: break
            if (neuerZustand != zustand) {
                val vorher = zustand
                zustand = neuerZustand
                Log.i(
                    TAG,
                    "[RDP] Zustand $vorher -> $neuerZustand nach " +
                        "${sitzung.frameCount()} Bridge-Bildern, $zaehler Tab-Bildern"
                )
                if (neuerZustand == SessionState.FAILED || neuerZustand == SessionState.DISCONNECTED) {
                    val fehler = runCatching { sitzung.lastError() }.getOrDefault("")
                    zustandsMeldung = fehler.ifBlank { "" }
                    Log.w(TAG, "[RDP] getrennt: $zustandsMeldung")
                }
            }
            if (neuerZustand == SessionState.DISCONNECTED && verbindung.aktiv) {
                delay(NEUVERBINDUNG_PAUSE_MS)
                val stand = runCatching { sitzung.state() }.getOrNull()
                when {
                    !verbindung.aktiv -> {
                        Log.i(TAG, "[RDP] Verbindung wurde vom Nutzer beendet")
                        return@LaunchedEffect
                    }
                    stand == SessionState.CONNECTED || stand == SessionState.CONNECTING -> {
                        Log.d(TAG, "[RDP] Bruecke baut selbst neu auf ($stand), Schleife laeuft weiter")
                        continue
                    }
                    else -> {
                        Log.i(TAG, "[RDP] Bruecke bleibt zurueck ($stand), App startet Neuaufbau")
                        onNeuverbinden()
                        return@LaunchedEffect
                    }
                }
            }
            val jetztMs = System.currentTimeMillis()
            if (jetztMs - diagnoseMs >= 1000L) {
                diagnoseMs = jetztMs
                val frameAnzahl = runCatching { sitzung.frameCount() }.getOrNull()
                diagnose.value = "Bridge: ${frameAnzahl?.toString() ?: "-"} | Tab: $zaehler | $zustand"
            }
            if (jetztMs - letzterBerichtMs >= 5000L) {
                letzterBerichtMs = jetztMs
                val bericht =
                    runCatching { sitzung.diagnose() }.getOrElse { "diagnose() nicht verfuegbar: ${it.message}" }
                Log.i(TAG, "[RDP] ===== Diagnose (5-s-Takt) =====\n$bericht")
            }
        }
    }

    BackHandler { onTrennen() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bild != null) {
            val (flaecheBreite, flaecheHoehe) = bildFlaeche(rahmen, bildGroesse)
            val versatzX = (rahmen.width - flaecheBreite) / 2
            val versatzY = (rahmen.height - flaecheHoehe) / 2
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rahmen = it }
                    .pointerInput(sitzung, flaecheBreite, flaecheHoehe, versatzX, versatzY) {
                        eingabeVerarbeiten(
                            sitzung = sitzung,
                            versatzX = versatzX,
                            versatzY = versatzY,
                            flaecheBreite = flaecheBreite,
                            flaecheHoehe = flaecheHoehe,
                            bildBreite = bildGroesse.width,
                            bildHoehe = bildGroesse.height,
                            onZeiger = { px, py ->
                                zeigerX = px
                                zeigerY = py
                                zeigerDa = true
                            }
                        )
                    }
            ) {
                key(zaehler) {
                    Image(
                        bitmap = bild!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.rdp_warte_auf_bild),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rahmen = it },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (zustand != SessionState.FAILED && zustand != SessionState.DISCONNECTED) {
                        CircularProgressIndicator(color = AccentViolet)
                        Text(
                            text = stringResource(R.string.rdp_verbinde, zielIp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = zustandsMeldung.ifBlank { stringResource(R.string.rdp_getrennt) },
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onTrennen,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                        ) {
                            Text(stringResource(R.string.trennen), color = TextPrimary)
                        }
                    }
                }
            }
        }

        if (!vollbild) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { tastaturOffen = !tastaturOffen }) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = stringResource(R.string.rdp_tastatur),
                    tint = Color.White
                )
            }
            IconButton(onClick = { vollbild = true }) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.vollbild),
                    tint = Color.White
                )
            }
            IconButton(onClick = onTrennen) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.trennen),
                    tint = Color.White
                )
            }
        }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vollbild = false }) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = stringResource(R.string.vollbild),
                        tint = Color.White
                    )
                }
            }
        }

        if (tastaturOffen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(10.dp)
            ) {
                BasicTextField(
                    value = tastaturText,
                    onValueChange = { neuerText ->
                        for (zeichen in neuerText) {
                            sitzung.typeUnicode(zeichen.code.toUInt())
                        }
                        tastaturText = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tastaturAnker)
                        .onPreviewKeyEvent { ereignis ->
                            val info = tastenInfo(ereignis.nativeKeyEvent.keyCode)
                            if (info == null) {
                                false
                            } else {
                                if (ereignis.type == KeyEventType.KeyDown) {
                                    sitzung.keyDown(info.scancode, info.erweitert)
                                } else {
                                    sitzung.keyUp(info.scancode, info.erweitert)
                                }
                                true
                            }
                        },
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary
                    ),
                    cursorBrush = SolidColor(AccentViolet),
                    decorationBox = { inner ->
                        Column {
                            if (tastaturText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.rdp_tastatur_hinweis),
                                    color = TextTertiary
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
        if (bild != null && zeigerDa) {
            ZeigerAnzeige(
                x = zeigerX,
                y = zeigerY,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        DiagnoseOverlay(
            inhalt = diagnose,
            modifier = Modifier.align(Alignment.TopStart)
        )
        if (bild != null && zustand == SessionState.CONNECTING) {
            Text(
                text = stringResource(R.string.rdp_verbinde_erneuert),
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }

    LaunchedEffect(tastaturOffen) {
        if (tastaturOffen) {
            delay(150.milliseconds)
            tastaturAnker.requestFocus()
        }
    }
}

@Composable
private fun DiagnoseOverlay(inhalt: State<String>, modifier: Modifier = Modifier) {
    val text = inhalt.value
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = modifier
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ZeigerAnzeige(x: Float, y: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(26.dp, 30.dp)
    ) {
        val pfad = Path().apply {
            moveTo(2f, 1f)
            lineTo(2f, size.height * 0.76f)
            lineTo(size.width * 0.26f, size.height * 0.56f)
            lineTo(size.width * 0.44f, size.height * 0.92f)
            lineTo(size.width * 0.62f, size.height * 0.84f)
            lineTo(size.width * 0.44f, size.height * 0.50f)
            lineTo(size.width * 0.74f, size.height * 0.48f)
            close()
        }
        drawPath(pfad, Color.Black, style = Stroke(width = 4f))
        drawPath(pfad, Color.White)
    }
}

private suspend fun PointerInputScope.eingabeVerarbeiten(
    sitzung: RdpSession,
    versatzX: Int,
    versatzY: Int,
    flaecheBreite: Int,
    flaecheHoehe: Int,
    bildBreite: Int,
    bildHoehe: Int,
    onZeiger: (Float, Float) -> Unit
) {
    if (flaecheBreite <= 0 || flaecheHoehe <= 0 || bildBreite <= 0 || bildHoehe <= 0) {
        return
    }
    val faktorX = bildBreite.toFloat() / flaecheBreite.toFloat()
    val faktorY = bildHoehe.toFloat() / flaecheHoehe.toFloat()

    fun senden(x: Float, y: Float) {
        val zielX = ((x - versatzX) * faktorX).roundToInt()
        val zielY = ((y - versatzY) * faktorY).roundToInt()
        if (zielX >= 0 && zielY >= 0 && zielX < bildBreite && zielY < bildHoehe) {
            sitzung.pointerMove(zielX.toUShort(), zielY.toUShort())
        }
    }

    awaitEachGesture {
        val start = awaitFirstDown(requireUnconsumed = false)
        senden(start.position.x, start.position.y)
        onZeiger(start.position.x, start.position.y)
        var zeigerZahl = 1
        var gezogen = false
        var hinunter = false
        var letzterX = start.position.x
        var letzterY = start.position.y
        var scrollBezug: Pair<Float, Float>? = null
        var klickX = 0f
        var klickY = 0f
        val startZeit = System.currentTimeMillis()
        var gemeldetX = start.position.x
        var gemeldetY = start.position.y

        while (true) {
            val ereignis = awaitPointerEvent()
            val zustand = ereignis.changes.firstOrNull { it.id == start.id } ?: break
            if (!zustand.pressed) {
                if (!gezogen && !hinunter && zeigerZahl == 1) {
                    val jetzt = System.currentTimeMillis()
                    val doppelt = jetzt - 0 < DOPPELKLICK_MS &&
                        abs(klickX - start.position.x) < 40f &&
                        abs(klickY - start.position.y) < 40f
                    sitzung.pointerButton(MouseButton.LEFT, true)
                    sitzung.pointerButton(MouseButton.LEFT, false)
                    if (doppelt) {
                        sitzung.pointerButton(MouseButton.LEFT, true)
                        sitzung.pointerButton(MouseButton.LEFT, false)
                    } else {
                        klickX = start.position.x
                        klickY = start.position.y
                    }
                }
                if (hinunter) {
                    sitzung.pointerButton(MouseButton.RIGHT, false)
                }
                break
            }

            senden(zustand.position.x, zustand.position.y)
            if (abs(zustand.position.x - gemeldetX) > 1.5f || abs(zustand.position.y - gemeldetY) > 1.5f) {
                gemeldetX = zustand.position.x
                gemeldetY = zustand.position.y
                onZeiger(gemeldetX, gemeldetY)
            }
            if (abs(zustand.position.x - letzterX) > 3f || abs(zustand.position.y - letzterY) > 3f) {
                gezogen = true
            }
            letzterX = zustand.position.x
            letzterY = zustand.position.y

            zeigerZahl = ereignis.changes.size
            if (zeigerZahl >= 2) {
                val pan = ereignis.calculatePan()
                val zoom = ereignis.calculateZoom()
                if (abs(pan.x) > 0.5f || abs(pan.y) > 0.5f) {
                    if (scrollBezug == null) {
                        scrollBezug = pan.x to pan.y
                    }
                    val senkrecht = ((pan.y - scrollBezug.second) * 12f).roundToInt()
                    if (senkrecht != 0) {
                        sitzung.pointerScroll(0.toShort(), senkrecht.toShort())
                        scrollBezug = pan.x to pan.y
                        gezogen = true
                    }
                }
                if (zoom != 1f) {
                    gezogen = true
                }
            }

            if (!hinunter && !gezogen && System.currentTimeMillis() - startZeit > 600L) {
                hinunter = true
                sitzung.pointerButton(MouseButton.RIGHT, true)
            }
        }
    }
}
