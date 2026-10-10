package com.tabslify.core.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.calloutAwareMarkdownComponents
import com.tabslify.core.ui.normalizeCallouts
import com.tabslify.privatetabslifyapp.isOnline
import com.tabslify.quiethoursnotificationhelper.AiProvider
import com.tabslify.quiethoursnotificationhelper.AiTarget
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import com.tabslify.tabs.aitab.aiUsageText
import com.tabslify.tabs.aitab.readAiUsage
import com.tabslify.tabs.aitab.reserveAiUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TEXT_LAENGE_LIMIT = 18_000
private const val AKTION_GEWICHT = 1

class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val roh = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim().orEmpty()

        if (roh.isEmpty()) {
            Toast.makeText(this, getString(R.string.textaktion_kein_text), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val nurLesen = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        setContent {
            TextAktionOverlay(
                text = roh,
                nurLesen = nurLesen,
                onSchliessen = { finish() },
                onImChat = { openInChat(roh) }
            )
        }
    }

    private fun openInChat(text: String) {
        Toast.makeText(this, getString(R.string.chat_geoeffnet), Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("target", "text_aktionen")
                putExtra("ai_prompt", text)
            }
        )
        finish()
    }

    fun ausfuehren(
        instruktion: String,
        text: String,
        onToken: (String) -> Unit,
        onFertig: (String) -> Unit
    ) {
        lifecycleScope.launch {
            val ctx = this@ProcessTextActivity
            if (!isOnline(ctx)) {
                onFertig(ctx.getString(R.string.kein_netzwerk))
                return@launch
            }
            if (!reserveAiUsage(ctx, AKTION_GEWICHT)) {
                val usage = readAiUsage(ctx)
                onFertig(ctx.getString(R.string.textaktion_limit_erreicht, aiUsageText(ctx, usage)))
                return@launch
            }
            val bereinigt = text.trim().take(TEXT_LAENGE_LIMIT)
            try {
                val antwort = withContext(Dispatchers.IO) {
                    sendAiRequest(
                        context = ctx,
                        userMessage = "$instruktion\n\n$bereinigt",
                        target = AiTarget.TextAction,
                        provider = AiProvider.GEMINI,
                        model = Config.DEF_GEMINI,
                        onToken = onToken
                    )
                }
                onFertig(antwort?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.fehler))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFertig(ctx.getString(R.string.fehler_msg, e.message))
            }
        }
    }
}

private enum class FastAktion(val titelRes: Int, val instruktion: String) {
    ZUSAMMENFASSEN(
        R.string.textaktion_zusammenfassen,
        "Fasse den folgenden Text in maximal 5 knappen Bulletpoints zusammen. Nenne die wichtigsten Fakten, Zahlen und Namen. Gib dich nicht mit einer bloßen Wiederholung zufrieden, sondern verdichte. Beginne direkt, ohne Einleitung."
    ),
    ERKLAEREN(
        R.string.textaktion_erklaeren,
        "Erkläre den folgenden Text so einfach wie möglich, als würdest du es einem klugen Laien erzählen. Bleib verständlich, vermeide Fachjargon oder erkläre ihn sofort. Nutze Markdown für Struktur und übersichtliche Absätze."
    ),
    UEBERSETZEN(
        R.string.textaktion_uebersetzen,
        "Übersetze den folgenden Text. Ist er nicht auf Deutsch, übersetze ihn ins Deutsche. Ist er bereits auf Deutsch, übersetze ihn ins Englische. Gib nur die Übersetzung aus, ohne Vorbemerkung und ohne den Originaltext zu wiederholen."
    ),
    KORRIGIEREN(
        R.string.textaktion_korrigieren,
        "Korrigiere im folgenden Text Rechtschreibung, Grammatik und Satzbau. Behalte Inhalt, Ton und Länge bei, verbessere nur die Form. Gib ausschließlich den korrigierten Text aus, ohne Vorbemerkung und ohne Erklärung der Änderungen."
    )
}

@Composable
private fun TextAktionOverlay(
    text: String,
    nurLesen: Boolean,
    onSchliessen: () -> Unit,
    onImChat: (String) -> Unit
) {
    val gekuerzt = text.length > TEXT_LAENGE_LIMIT
    var aktion by remember { mutableStateOf<FastAktion?>(null) }
    var eigenesFeld by remember { mutableStateOf(false) }
    var eigenerPrompt by remember { mutableStateOf("") }
    var laeuft by remember { mutableStateOf(false) }
    var antwort by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf<String?>(null) }
    var abgeschlossen by remember { mutableStateOf(false) }

    val activity = LocalContext.current
    val fehlerAllgemein = stringResource(R.string.fehler)
    val eigenesFeldFehlt = stringResource(R.string.textaktion_eigenes_feld_fehlt)
    val markdownComponents = calloutAwareMarkdownComponents()
    val markdownColors = markdownColor(text = TextPrimary)
    val textScrollState = rememberScrollState()

    LaunchedEffect(antwort) {
        if (laeuft && antwort.isNotEmpty() && !textScrollState.isScrollInProgress) {
            textScrollState.scrollTo(textScrollState.maxValue)
        }
    }

    val starten: (String) -> Unit = { instruktion ->
        val host = activity as? ProcessTextActivity
        if (host == null) {
            fehler = fehlerAllgemein
        } else {
            laeuft = true
            antwort = ""
            fehler = null
            host.ausfuehren(
                instruktion = instruktion,
                text = text,
                onToken = { delta ->
                    antwort += delta
                },
                onFertig = { ergebnis ->
                    laeuft = false
                    antwort = ergebnis
                    abgeschlossen = true
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .verticalScroll(textScrollState)
        ) {
            Text(
                stringResource(R.string.tabslify_textaktionen),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.textaktion_auswahl),
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(14.dp))

            for (option in FastAktion.entries) {
                AktionKachel(
                    titel = stringResource(option.titelRes),
                    aktiv = aktion == option && !abgeschlossen,
                    enabled = !laeuft,
                    onClick = {
                        abgeschlossen = false
                        eigenesFeld = false
                        eigenerPrompt = ""
                        antwort = ""
                        fehler = null
                        aktion = option
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            AktionKachel(
                titel = stringResource(R.string.textaktion_eigener_prompt),
                aktiv = eigenesFeld && !abgeschlossen,
                enabled = !laeuft,
                onClick = {
                    abgeschlossen = false
                    aktion = null
                    eigenesFeld = true
                    antwort = ""
                    fehler = null
                }
            )

            if (gekuerzt) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.textaktion_gekuerzt, TEXT_LAENGE_LIMIT),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (nurLesen) {
                Text(
                    stringResource(R.string.textaktion_nur_lesen),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (eigenesFeld && !abgeschlossen) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = eigenerPrompt,
                    onValueChange = { eigenerPrompt = it },
                    label = { Text(stringResource(R.string.textaktion_eigenes_feld)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = Color(0xFF3A3A3A),
                        cursorColor = AccentViolet
                    ),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val gewaehlt = aktion
            val startklar = gewaehlt != null || (eigenesFeld && eigenerPrompt.isNotBlank())

            if (!abgeschlossen) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        when {
                            gewaehlt != null -> starten(gewaehlt.instruktion)
                            eigenerPrompt.isNotBlank() -> starten(
                                eigenerPrompt.trim() + "\n\n" + text.trim().take(TEXT_LAENGE_LIMIT)
                            )
                            else -> fehler = eigenesFeldFehlt
                        }
                    },
                    enabled = startklar && !laeuft,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (laeuft) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = TextPrimary
                        )
                    } else {
                        Text(
                            stringResource(R.string.textaktion_starten),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            val fehlerText = fehler
            if (fehlerText != null) {
                Spacer(Modifier.height(8.dp))
                Text(fehlerText, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            if (laeuft || antwort.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x33202020))
                        .padding(12.dp)
                ) {
                    if (antwort.isEmpty()) {
                        Text(
                            stringResource(R.string.textaktion_denkt),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        Markdown(
                            content = normalizeCallouts(antwort),
                            components = markdownComponents,
                            colors = markdownColors
                        )
                    }
                }
            }

            if (abgeschlossen) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        abgeschlossen = false
                        antwort = ""
                        aktion = null
                        eigenesFeld = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.textaktion_neue_aktion_waehlen),
                        color = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onSchliessen) {
                    Text(stringResource(R.string.abbrechen), color = TextSecondary)
                }
                TextButton(onClick = { onImChat(text) }) {
                    Text(
                        stringResource(R.string.im_chat_oeffnen),
                        color = AccentViolet,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AktionKachel(
    titel: String,
    aktiv: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (aktiv) AccentViolet.copy(alpha = 0.32f) else Color(0xFF2A2A2A))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            titel,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = if (aktiv) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}