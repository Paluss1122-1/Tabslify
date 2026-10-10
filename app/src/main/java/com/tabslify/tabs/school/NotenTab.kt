package com.tabslify.tabs.school

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.AccentVioletDim
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.BgSurface
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NotenGruppe { GROSS, KLEIN, MUENDLICH }

data class NotenEintrag(
    val id: Long,
    val note: Double,
    val gruppe: NotenGruppe,
    val halbjahr: Int,
    val datum: Long = System.currentTimeMillis(),
    val titel: String = ""
)

data class NotenFach(
    val name: String,
    val eintraege: List<NotenEintrag> = emptyList(),
    val gruppenGewichte: Map<NotenGruppe, Double> = emptyMap()
)

private const val NOTEN_KEY = "noten_faecher"

private val NOTEN_STUFEN =
    listOf(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0)

private val NOTEN_GEWICHTE = listOf(0.5, 1.0, 1.5, 2.0, 3.0)

fun notenGewicht(fach: NotenFach, gruppe: NotenGruppe): Double =
    fach.gruppenGewichte[gruppe] ?: 1.0

fun gruppenSchnitt(fach: NotenFach, gruppe: NotenGruppe, halbjahr: Int?): Double? {
    val werte = fach.eintraege
        .filter { it.gruppe == gruppe && (halbjahr == null || it.halbjahr == halbjahr) }
        .map { it.note }
    if (werte.isEmpty()) return null
    return werte.average()
}

fun notenSchnitt(fach: NotenFach, halbjahr: Int?): Double? {
    var summe = 0.0
    var gewichtSumme = 0.0
    NotenGruppe.entries.forEach { gruppe ->
        val schnitt = gruppenSchnitt(fach, gruppe, halbjahr) ?: return@forEach
        val gewicht = notenGewicht(fach, gruppe)
        summe += schnitt * gewicht
        gewichtSumme += gewicht
    }
    if (gewichtSumme <= 0.0) return null
    return summe / gewichtSumme
}

fun formatNote(wert: Double, locale: Locale): String = String.format(locale, "%.2f", wert)

fun kurzZahl(wert: Double, locale: Locale): String =
    if (wert % 1.0 == 0.0) wert.toInt().toString() else String.format(locale, "%.1f", wert)

fun ladeNotenFaecher(prefs: SharedPreferences): List<NotenFach> {
    val raw = prefs.getString(NOTEN_KEY, null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val eintraege = mutableListOf<NotenEintrag>()
            val ea = o.optJSONArray("eintraege")
            if (ea != null) {
                (0 until ea.length()).forEach { j ->
                    val e = ea.optJSONObject(j) ?: return@forEach
                    val gruppe = runCatching {
                        NotenGruppe.valueOf(e.optString("gruppe", NotenGruppe.GROSS.name))
                    }.getOrDefault(NotenGruppe.GROSS)
                    eintraege.add(
                        NotenEintrag(
                            id = e.optLong("id", 0L),
                            note = e.optDouble("note", 0.0),
                            gruppe = gruppe,
                            halbjahr = if (e.optInt("halbjahr", 1) == 2) 2 else 1,
                            datum = e.optLong("datum", System.currentTimeMillis()),
                            titel = e.optString("titel")
                        )
                    )
                }
            }
            val gewichte = mutableMapOf<NotenGruppe, Double>()
            val gw = o.optJSONObject("gewicht")
            if (gw != null) {
                NotenGruppe.entries.forEach { gruppe ->
                    if (gw.has(gruppe.name)) gewichte[gruppe] = gw.optDouble(gruppe.name, 1.0)
                }
            }
            NotenFach(name = name, eintraege = eintraege, gruppenGewichte = gewichte)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun speichereNotenFaecher(prefs: SharedPreferences, faecher: List<NotenFach>): List<NotenFach> {
    val json = JSONArray().also { arr ->
        faecher.forEach { fach ->
            arr.put(
                JSONObject().apply {
                    put("name", fach.name)
                    put(
                        "gewicht",
                        JSONObject().also { gw ->
                            NotenGruppe.entries.forEach { gruppe ->
                                gw.put(gruppe.name, notenGewicht(fach, gruppe))
                            }
                        }
                    )
                    put(
                        "eintraege",
                        JSONArray().also { ea ->
                            fach.eintraege.forEach { e ->
                                ea.put(
                                    JSONObject().apply {
                                        put("id", e.id)
                                        put("note", e.note)
                                        put("gruppe", e.gruppe.name)
                                        put("halbjahr", e.halbjahr)
                                        put("datum", e.datum)
                                        put("titel", e.titel)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }.toString()
    prefs.edit { putString(NOTEN_KEY, json) }
    return faecher
}

fun entferneNotenFach(prefs: SharedPreferences, fach: NotenFach): List<NotenFach> =
    speichereNotenFaecher(prefs, ladeNotenFaecher(prefs).filter { it.name != fach.name })

@Composable
private fun gruppenLabel(gruppe: NotenGruppe): String = when (gruppe) {
    NotenGruppe.GROSS -> stringResource(R.string.noten_gruppe_gross)
    NotenGruppe.KLEIN -> stringResource(R.string.noten_gruppe_klein)
    NotenGruppe.MUENDLICH -> stringResource(R.string.noten_gruppe_muendlich)
}

private fun gruppenEmoji(gruppe: NotenGruppe): String = when (gruppe) {
    NotenGruppe.GROSS -> "📝"
    NotenGruppe.KLEIN -> "📄"
    NotenGruppe.MUENDLICH -> "🗣"
}

@Composable
fun NotenScreen(
    onBack: () -> Unit,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    if (!prvt()) {
        Toast.makeText(context, stringResource(R.string.forbidden), Toast.LENGTH_SHORT).show()
        return
    }
    val prefs = remember { context.getSharedPreferences("noten", Context.MODE_PRIVATE) }
    val locale = LocalLocale.current.platformLocale

    var faecher by remember { mutableStateOf(ladeNotenFaecher(prefs)) }
    var auswahl by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<Int?>(null) }

    var zeigeFachDialog by remember { mutableStateOf(false) }
    var fachZumUmbenennen by remember { mutableStateOf<NotenFach?>(null) }
    var fachNameInput by remember { mutableStateOf("") }
    var fachZumLoeschen by remember { mutableStateOf<NotenFach?>(null) }
    var eintragZumLoeschen by remember { mutableStateOf<Pair<NotenFach, NotenEintrag>?>(null) }
    var fachFuerNoten by remember { mutableStateOf<NotenFach?>(null) }
    var fachFuerGewichte by remember { mutableStateOf<NotenFach?>(null) }

    val aktuellesFach = faecher.firstOrNull { it.name == auswahl }

    BackHandler {
        if (aktuellesFach != null) {
            auswahl = null
            filter = null
        } else onBack()
    }

    if (zeigeFachDialog) {
        AlertDialog(
            onDismissRequest = { zeigeFachDialog = false },
            containerColor = BgSurface,
            title = {
                Text(
                    stringResource(
                        if (fachZumUmbenennen != null) R.string.noten_fach_umbenennen
                        else R.string.fach_erstellen
                    ),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = fachNameInput,
                    onValueChange = { fachNameInput = it },
                    label = { Text(stringResource(R.string.fachname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = fachNameInput.trim()
                    val alt = fachZumUmbenennen
                    if (name.isNotBlank() && faecher.none { it.name == name }) {
                        faecher = speichereNotenFaecher(
                            prefs,
                            if (alt != null) {
                                faecher.map {
                                    if (it.name == alt.name) it.copy(name = name) else it
                                }
                            } else {
                                faecher + NotenFach(name = name)
                            }
                        )
                        if (auswahl != null && auswahl == alt?.name) auswahl = name
                    }
                    fachNameInput = ""
                    fachZumUmbenennen = null
                    zeigeFachDialog = false
                }) { Text(stringResource(R.string.speichern), color = TextPrimary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    zeigeFachDialog = false
                    fachZumUmbenennen = null
                    fachNameInput = ""
                }) { Text(stringResource(R.string.abbrechen), color = TextSecondary) }
            }
        )
    }

    fachZumLoeschen?.let { fach ->
        AlertDialogTabslify(
            title = stringResource(R.string.noten_fach_loeschen),
            text = stringResource(R.string.wird_geloscht, fach.name),
            confirmText = stringResource(R.string.loschen),
            onConfirm = {
                if (auswahl == fach.name) {
                    auswahl = null
                    filter = null
                }
                faecher = entferneNotenFach(prefs, fach)
                fachZumLoeschen = null
            },
            onDismiss = { fachZumLoeschen = null }
        )
    }

    eintragZumLoeschen?.let { (fach, eintrag) ->
        AlertDialogTabslify(
            title = stringResource(R.string.noten_eintrag_loeschen),
            text = stringResource(
                R.string.noten_note_wird_geloescht,
                formatNote(eintrag.note, locale)
            ),
            confirmText = stringResource(R.string.loschen),
            onConfirm = {
                faecher = speichereNotenFaecher(
                    prefs,
                    faecher.map {
                        if (it.name == fach.name)
                            it.copy(eintraege = it.eintraege.filter { e -> e.id != eintrag.id })
                        else it
                    }
                )
                eintragZumLoeschen = null
            },
            onDismiss = { eintragZumLoeschen = null }
        )
    }

    fachFuerNoten?.let { fach ->
        NotenEintragDialog(
            startGruppe = NotenGruppe.GROSS,
            startHalbjahr = filter ?: 1,
            onConfirm = { eintrag ->
                faecher = speichereNotenFaecher(
                    prefs,
                    faecher.map {
                        if (it.name == fach.name) it.copy(eintraege = it.eintraege + eintrag)
                        else it
                    }
                )
                fachFuerNoten = null
            },
            onDismiss = { fachFuerNoten = null }
        )
    }

    fachFuerGewichte?.let { fach ->
        NotenGewichteDialog(
            fach = fach,
            onConfirm = { gewichte ->
                faecher = speichereNotenFaecher(
                    prefs,
                    faecher.map { if (it.name == fach.name) it.copy(gruppenGewichte = gewichte) else it }
                )
                fachFuerGewichte = null
            },
            onDismiss = { fachFuerGewichte = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (aktuellesFach == null) {
            NotenFachListe(
                faecher = faecher,
                locale = locale,
                paddingValues = paddingValues,
                onBack = onBack,
                onOpen = { fach ->
                    auswahl = fach.name
                    filter = null
                },
                onRename = { fach ->
                    fachZumUmbenennen = fach
                    fachNameInput = fach.name
                    zeigeFachDialog = true
                },
                onDelete = { fachZumLoeschen = it },
                onAddFach = {
                    fachZumUmbenennen = null
                    fachNameInput = ""
                    zeigeFachDialog = true
                }
            )
        } else {
            NotenFachInhalt(
                fach = aktuellesFach,
                filter = filter,
                locale = locale,
                paddingValues = paddingValues,
                onBack = {
                    auswahl = null
                    filter = null
                },
                onFilter = { filter = it },
                onAddNote = { fachFuerNoten = it },
                onDeleteNote = { eintrag -> eintragZumLoeschen = aktuellesFach to eintrag },
                onOpenWeights = { fachFuerGewichte = it }
            )
        }
    }
}

@Composable
private fun NotenFachListe(
    faecher: List<NotenFach>,
    locale: Locale,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpen: (NotenFach) -> Unit,
    onRename: (NotenFach) -> Unit,
    onDelete: (NotenFach) -> Unit,
    onAddFach: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SchoolHeader(
            title = stringResource(R.string.noten),
            subtitle = stringResource(R.string.noten_uebersicht),
            onBack = onBack,
            showDashboard = false,
            paddingValues = paddingValues
        )

        if (faecher.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📊", fontSize = 48.sp)
                    Text(
                        stringResource(R.string.noten_keine_faecher),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.noten_fach_hinweis),
                        color = TextTertiary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(faecher, key = { it.name }) { fach ->
                    NotenFachKarte(
                        fach = fach,
                        locale = locale,
                        onOpen = { onOpen(fach) },
                        onRename = { onRename(fach) },
                        onDelete = { onDelete(fach) }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = onAddFach,
            modifier = Modifier
                .align(Alignment.End)
                .padding(20.dp),
            containerColor = AccentViolet,
            contentColor = TextPrimary
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.fach_erstellen))
        }
    }
}

@Composable
private fun NotenFachKarte(
    fach: NotenFach,
    locale: Locale,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val schnitt = notenSchnitt(fach, null)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(AccentViolet, AccentVioletDim))),
            contentAlignment = Alignment.Center
        ) {
            Text("📊", fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fach.name,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (fach.eintraege.isEmpty()) stringResource(R.string.noten_keine_eintraege)
                else pluralStringResource(
                    R.plurals.noten_anzahl, fach.eintraege.size, fach.eintraege.size
                ),
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
        if (schnitt != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    formatNote(schnitt, locale),
                    color = AccentViolet,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Box {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = BgCard
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.noten_fach_umbenennen),
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.loschen),
                            color = Color(0xFFEF5350),
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun NotenFachInhalt(
    fach: NotenFach,
    filter: Int?,
    locale: Locale,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onFilter: (Int?) -> Unit,
    onAddNote: (NotenFach) -> Unit,
    onDeleteNote: (NotenEintrag) -> Unit,
    onOpenWeights: (NotenFach) -> Unit
) {
    val schnitt = notenSchnitt(fach, filter)
    val sichtbar = fach.eintraege
        .filter { filter == null || it.halbjahr == filter }
        .sortedByDescending { it.datum }
    val datumFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy", locale) }

    Column(modifier = Modifier.fillMaxSize()) {
        SchoolHeader(
            title = fach.name,
            subtitle = if (schnitt != null) {
                "${stringResource(R.string.noten_durchschnitt)}: ${formatNote(schnitt, locale)}"
            } else stringResource(R.string.noten_keine_eintraege),
            onBack = onBack,
            showDashboard = false,
            paddingValues = paddingValues
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotenChip(
                text = stringResource(R.string.noten_ganzes_jahr),
                selected = filter == null,
                modifier = Modifier.weight(1f),
                onClick = { onFilter(null) }
            )
            NotenChip(
                text = stringResource(R.string.noten_halbjahr_1),
                selected = filter == 1,
                modifier = Modifier.weight(1f),
                onClick = { onFilter(1) }
            )
            NotenChip(
                text = stringResource(R.string.noten_halbjahr_2),
                selected = filter == 2,
                modifier = Modifier.weight(1f),
                onClick = { onFilter(2) }
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.noten_aufschluesselung),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.noten_gewichte),
                        color = AccentViolet,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenWeights(fach) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                NotenGruppe.entries.forEach { gruppe ->
                    val gruppenWert = gruppenSchnitt(fach, gruppe, filter)
                    if (gruppenWert != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${gruppenEmoji(gruppe)}  ${gruppenLabel(gruppe)}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.noten_anzahl,
                                    fach.eintraege.count {
                                        it.gruppe == gruppe &&
                                            (filter == null || it.halbjahr == filter)
                                    },
                                    fach.eintraege.count {
                                        it.gruppe == gruppe &&
                                            (filter == null || it.halbjahr == filter)
                                    }
                                ),
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatNote(gruppenWert, locale),
                                color = AccentViolet,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (schnitt != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.noten_gesamt),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatNote(schnitt, locale),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sichtbar.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📝", fontSize = 40.sp)
                    Text(
                        stringResource(R.string.noten_keine_eintraege),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.noten_tippe_fuer_note),
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sichtbar, key = { it.id }) { eintrag ->
                    NotenZeile(
                        halbjahrText = stringResource(
                            if (eintrag.halbjahr == 2) R.string.noten_halbjahr_2
                            else R.string.noten_halbjahr_1
                        ),
                        datumText = datumFormat.format(Date(eintrag.datum)),
                        notenText = formatNote(eintrag.note, locale),
                        titelText = eintrag.titel.ifBlank { gruppenLabel(eintrag.gruppe) },
                        onDelete = { onDeleteNote(eintrag) }
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { onAddNote(fach) },
            modifier = Modifier
                .align(Alignment.End)
                .padding(20.dp),
            containerColor = AccentViolet,
            contentColor = TextPrimary
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.noten_eintrag_hinzufuegen))
        }
    }
}

@Composable
private fun NotenZeile(
    halbjahrText: String,
    datumText: String,
    notenText: String,
    titelText: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(AccentViolet, AccentVioletDim))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                notenText,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titelText,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$halbjahrText · $datumText",
                color = TextTertiary,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "✕",
            color = TextTertiary,
            fontSize = 15.sp,
            modifier = Modifier.clickable { onDelete() }
        )
    }
}

@Composable
private fun NotenChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentViolet.copy(alpha = 0.2f) else BgSurface)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) AccentViolet else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NotenEintragDialog(
    startGruppe: NotenGruppe,
    startHalbjahr: Int,
    onConfirm: (NotenEintrag) -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    var note by remember { mutableDoubleStateOf(2.0) }
    var gruppe by remember { mutableStateOf(startGruppe) }
    var halbjahr by remember { mutableIntStateOf(startHalbjahr) }
    var titel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurface,
        title = {
            Text(
                stringResource(R.string.noten_eintrag_hinzufuegen),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.noten_note),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                NOTEN_STUFEN.chunked(4).forEach { zeile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        zeile.forEach { stufe ->
                            NotenChip(
                                text = kurzZahl(stufe, locale),
                                selected = note == stufe,
                                modifier = Modifier.weight(1f),
                                onClick = { note = stufe }
                            )
                        }
                        repeat(4 - zeile.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    stringResource(R.string.noten_art),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotenGruppe.entries.forEach { eintragGruppe ->
                        NotenChip(
                            text = "${gruppenEmoji(eintragGruppe)}  ${gruppenLabel(eintragGruppe)}",
                            selected = gruppe == eintragGruppe,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { gruppe = eintragGruppe }
                        )
                    }
                }
                Text(
                    stringResource(R.string.noten_halbjahr),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotenChip(
                        text = stringResource(R.string.noten_halbjahr_1),
                        selected = halbjahr == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { halbjahr = 1 }
                    )
                    NotenChip(
                        text = stringResource(R.string.noten_halbjahr_2),
                        selected = halbjahr == 2,
                        modifier = Modifier.weight(1f),
                        onClick = { halbjahr = 2 }
                    )
                }
                OutlinedTextField(
                    value = titel,
                    onValueChange = { titel = it },
                    label = { Text(stringResource(R.string.noten_titel_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    NotenEintrag(
                        id = System.currentTimeMillis(),
                        note = note,
                        gruppe = gruppe,
                        halbjahr = halbjahr,
                        titel = titel.trim()
                    )
                )
            }) { Text(stringResource(R.string.speichern), color = TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.abbrechen), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun NotenGewichteDialog(
    fach: NotenFach,
    onConfirm: (Map<NotenGruppe, Double>) -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    var gewichte by remember(fach.name) {
        mutableStateOf(NotenGruppe.entries.associateWith { notenGewicht(fach, it) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurface,
        title = {
            Text(
                stringResource(R.string.noten_gewichte),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.noten_gewichte_hinweis),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                NotenGruppe.entries.forEach { gruppe ->
                    Column {
                        Text(
                            "${gruppenEmoji(gruppe)}  ${gruppenLabel(gruppe)}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NOTEN_GEWICHTE.forEach { wert ->
                                NotenChip(
                                    text = kurzZahl(wert, locale),
                                    selected = (gewichte[gruppe] ?: 1.0) == wert,
                                    modifier = Modifier.weight(1f),
                                    onClick = { gewichte = gewichte + (gruppe to wert) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(gewichte) }) {
                Text(stringResource(R.string.speichern), color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.abbrechen), color = TextSecondary)
            }
        }
    )
}