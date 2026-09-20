package com.tabslify.tabs.ainotify

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.BgSurface
import com.tabslify.core.ui.SharedViewModel
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@Composable
fun AiNotifyHost(svm: SharedViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingId by svm.pendingAiSession.collectAsState()
    val sessionId = pendingId ?: return
    var session by remember(sessionId) {
        mutableStateOf(AiNotifyStore.loadSession(context, sessionId))
    }
    var sending by remember(sessionId) { mutableStateOf(false) }
    val active = session
    if (active == null) {
        Dialog(onDismissRequest = { svm.setPendingAiSession(null) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgSurface)
                    .padding(24.dp)
            ) {
                Text(stringResource(R.string.ai_notify_abgelaufen), color = TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { svm.setPendingAiSession(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.ai_notify_ok)) }
            }
        }
        return
    }
    val singleChoice = remember(sessionId) { mutableStateMapOf<String, String>() }
    val multiChoice = remember(sessionId) { mutableStateMapOf<String, MutableSet<String>>() }
    val freeText = remember(sessionId) { mutableStateMapOf<String, String>() }
    val singleCustom = remember(sessionId) { mutableStateMapOf<String, String>() }
    val multiCustom = remember(sessionId) { mutableStateMapOf<String, String>() }
    val unvollstaendig = stringResource(R.string.ai_notify_unvollstaendig)
    val beantwortet = stringResource(R.string.ai_notify_beantwortet)
    val fehler = stringResource(R.string.ai_notify_fehler)

    Dialog(
        onDismissRequest = { svm.setPendingAiSession(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BgSurface)
                .padding(24.dp)
        ) {
            Text(active.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (active.source.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    context.getString(R.string.ai_notify_quelle, active.source),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (active.body.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(active.body, color = TextSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                active.questions.forEach { question ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .padding(16.dp)
                    ) {
                        Text(question.text, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        when (question.type) {
                            AI_NOTIFY_TYPE_MULTI -> {
                                val selected = multiChoice.getOrPut(question.id) { mutableSetOf() }
                                question.options.forEach { option ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!selected.add(option)) selected.remove(option)
                                                multiChoice[question.id] = selected.toMutableSet()
                                            }
                                    ) {
                                        Checkbox(
                                            checked = option in selected,
                                            onCheckedChange = {
                                                if (it) selected.add(option) else selected.remove(option)
                                                multiChoice[question.id] = selected.toMutableSet()
                                            }
                                        )
                                        Text(option, color = TextPrimary, fontSize = 14.sp)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!selected.add(AI_NOTIFY_CUSTOM)) selected.remove(AI_NOTIFY_CUSTOM)
                                            multiChoice[question.id] = selected.toMutableSet()
                                        }
                                ) {
                                    Checkbox(
                                        checked = AI_NOTIFY_CUSTOM in selected,
                                        onCheckedChange = {
                                            if (it) selected.add(AI_NOTIFY_CUSTOM) else selected.remove(AI_NOTIFY_CUSTOM)
                                            multiChoice[question.id] = selected.toMutableSet()
                                        }
                                    )
                                    Text(stringResource(R.string.ai_notify_eigene_antwort), color = TextPrimary, fontSize = 14.sp)
                                }
                                if (AI_NOTIFY_CUSTOM in selected) {
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = multiCustom[question.id].orEmpty(),
                                        onValueChange = { multiCustom[question.id] = it },
                                        placeholder = { Text(stringResource(R.string.ai_notify_eigene_antwort)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            AI_NOTIFY_TYPE_TEXT -> {
                                OutlinedTextField(
                                    value = freeText[question.id].orEmpty(),
                                    onValueChange = { freeText[question.id] = it },
                                    placeholder = { Text(stringResource(R.string.ai_notify_eigene_antwort)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            else -> {
                                question.options.forEach { option ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { singleChoice[question.id] = option }
                                    ) {
                                        RadioButton(
                                            selected = singleChoice[question.id] == option,
                                            onClick = { singleChoice[question.id] = option }
                                        )
                                        Text(option, color = TextPrimary, fontSize = 14.sp)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { singleChoice[question.id] = AI_NOTIFY_CUSTOM }
                                ) {
                                    RadioButton(
                                        selected = singleChoice[question.id] == AI_NOTIFY_CUSTOM,
                                        onClick = { singleChoice[question.id] = AI_NOTIFY_CUSTOM }
                                    )
                                    Text(stringResource(R.string.ai_notify_eigene_antwort), color = TextPrimary, fontSize = 14.sp)
                                }
                                if (singleChoice[question.id] == AI_NOTIFY_CUSTOM) {
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = singleCustom[question.id].orEmpty(),
                                        onValueChange = { singleCustom[question.id] = it },
                                        placeholder = { Text(stringResource(R.string.ai_notify_eigene_antwort)) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val answers = mutableMapOf<String, List<String>>()
                    var complete = true
                    active.questions.forEach { question ->
                        when (question.type) {
                            AI_NOTIFY_TYPE_MULTI -> {
                                val picked = multiChoice[question.id].orEmpty()
                                val base = picked.filter { it != AI_NOTIFY_CUSTOM }.toMutableList()
                                if (AI_NOTIFY_CUSTOM in picked) {
                                    val custom = multiCustom[question.id].orEmpty().trim()
                                    if (custom.isEmpty()) complete = false else base.add(custom)
                                }
                                answers[question.id] = base
                            }
                            AI_NOTIFY_TYPE_TEXT -> {
                                val value = freeText[question.id].orEmpty().trim()
                                if (value.isEmpty()) complete = false
                                answers[question.id] = listOf(value)
                            }
                            else -> {
                                val value = singleChoice[question.id]
                                if (value == null) {
                                    complete = false
                                    answers[question.id] = emptyList()
                                } else if (value == AI_NOTIFY_CUSTOM) {
                                    val custom = singleCustom[question.id].orEmpty().trim()
                                    if (custom.isEmpty()) complete = false
                                    answers[question.id] = listOf(custom)
                                } else {
                                    answers[question.id] = listOf(value)
                                }
                            }
                        }
                    }
                    if (!complete) {
                        Toast.makeText(context, unvollstaendig, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    sending = true
                    scope.launch {
                        val ok = runCatching {
                            Config.safeCall {
                                Config.client.postgrest.from(AI_NOTIFY_ANSWERS_TABLE)
                                    .insert(AiNotifyAnswerRow(active.sessionId, answers))
                            }
                        }.isSuccess
                        sending = false
                        Toast.makeText(
                            context,
                            if (ok) beantwortet else fehler,
                            Toast.LENGTH_LONG
                        ).show()
                        if (ok) {
                            session = null
                            AiNotifyStore.removeSession(context, active.sessionId)
                            svm.setPendingAiSession(null)
                        }
                    }
                },
                enabled = !sending,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.ai_notify_absenden))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { svm.setPendingAiSession(null) },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ai_notify_spaeter), color = TextSecondary) }
        }
    }
}
