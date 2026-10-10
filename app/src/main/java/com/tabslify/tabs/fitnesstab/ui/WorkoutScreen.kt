package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tabslify.R
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.AppBackground
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.NeonBox
import com.tabslify.core.ui.PloppingButton
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.WorkoutMode
import com.tabslify.tabs.fitnesstab.data.Difficulty
import com.tabslify.tabs.fitnesstab.data.Equipment
import com.tabslify.tabs.fitnesstab.data.ExerciseRepository
import com.tabslify.tabs.fitnesstab.data.MuscleGroup
import com.tabslify.tabs.fitnesstab.data.TrackingType
import kotlin.math.roundToInt

private val AccentOrange = Color(0xFFFF8A4C)
private val AccentGreen = Color(0xFF5BE388)
private val AccentBlue = Color(0xFF4CC9FF)
private val AccentYellow = Color(0xFFFFD74C)
private val AccentPink = Color(0xFFE850A5)

private val BgSoft = Color(0xFF242424)

private fun MuscleGroup.groupEmoji() = when (this) {
    MuscleGroup.CHEST -> "💪"
    MuscleGroup.BACK -> "🔙"
    MuscleGroup.LEGS -> "🦵"
    MuscleGroup.CORE -> "🎯"
    MuscleGroup.SHOULDERS -> "🏋️"
    MuscleGroup.ARMS -> "💥"
    MuscleGroup.CARDIO -> "❤️"
    MuscleGroup.FULLBODY -> "⚡"
}

private fun MuscleGroup.groupColors(): List<Color> = when (this) {
    MuscleGroup.CHEST -> listOf(AccentOrange, Color(0xFFFF4C4C))
    MuscleGroup.BACK -> listOf(Color(0xFF2ECC71), Color(0xFF1ABC9C))
    MuscleGroup.LEGS -> listOf(Color(0xFF8A4CFF), Color(0xFF4C4CFF))
    MuscleGroup.CORE -> listOf(Color(0xFFB45CFC), AccentPink)
    MuscleGroup.SHOULDERS -> listOf(AccentYellow, AccentOrange)
    MuscleGroup.ARMS -> listOf(AccentPink, Color(0xFFFF4C4C))
    MuscleGroup.CARDIO -> listOf(Color(0xFFE850A5), Color(0xFFFF4C7A))
    MuscleGroup.FULLBODY -> listOf(AccentBlue, Color(0xFF5BE388))
}

@Composable
fun WorkoutScreen(
    vm: FitnessViewModel,
    modifier: Modifier = Modifier,
    cameraScreen: @Composable () -> Unit = {}
) {
    var showDiscard by remember { mutableStateOf(false) }
    var showAddExercisePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (vm.workoutMode == WorkoutMode.CAMERA_POSE) {
            Box(Modifier.fillMaxSize()) {
                cameraScreen()
                ModeSwitchOverlay(
                    onSwitch = { vm.changeWorkoutMode(WorkoutMode.MANUAL) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
        } else {
            ManualWorkoutContent(
                vm = vm,
                onDiscard = { showDiscard = true },
                onAddExercise = { showAddExercisePicker = true }
            )
        }
    }

    if (showDiscard) {
        AlertDialogTabslify(
            onDismiss = { showDiscard = false },
            title = stringResource(R.string.fitness_ws_discard_confirm),
            text = "",
            confirmText = stringResource(R.string.fitness_ws_discard_confirm_yes),
            onConfirm = {
                showDiscard = false
                vm.discardWorkout()
            }
        )
    }

    if (showAddExercisePicker || vm.exercisePickerVisible) {
        ExercisePickerDialog(
            vm = vm,
            onDismiss = {
                showAddExercisePicker = false
                vm.closeExercisePicker()
            }
        )
    }
}

@Composable
private fun ModeSwitchOverlay(
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonBox(
        modifier = modifier,
        cornerRadius = RoundedCornerShape(14.dp),
        neonColors = listOf(AccentBlue, AccentViolet)
    ) {
        IconButton(onClick = onSwitch) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Text("Manual", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private val AccentViolet = Color(0xFF7C4DFF)

@Composable
private fun ManualWorkoutContent(
    vm: FitnessViewModel,
    onDiscard: () -> Unit,
    onAddExercise: () -> Unit
) {
    val elapsed = vm.workoutElapsedMs
    val minutes = (elapsed / 60_000).toInt()
    val seconds = ((elapsed % 60_000) / 1000).toInt()

    Column(Modifier.fillMaxSize()) {
        // ---- HEADER ----
        NeonBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp),
            cornerRadius = RoundedCornerShape(20.dp),
            neonColors = listOf(AccentOrange, AccentViolet)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.fitness_ws_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = stringResource(R.string.fitness_ws_session_active),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            color = AccentOrange,
                            fontWeight = FontWeight.Black,
                            fontSize = 28.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = stringResource(R.string.fitness_ws_elapsed),
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = vm.workoutName,
                        onValueChange = { vm.workoutName = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.fitness_ws_session_name_hint),
                                color = TextTertiary,
                                fontSize = 13.sp
                            )
                        },
                        label = {
                            Text(
                                stringResource(R.string.fitness_ws_session_name),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = BgSoft,
                            focusedContainerColor = BgSoft,
                            focusedBorderColor = AccentViolet,
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentViolet
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = onDiscard) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.fitness_ws_discard),
                            tint = AccentPink
                        )
                    }
                    IconButton(onClick = onAddExercise) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.fitness_ws_add_exercise),
                            tint = AccentGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                // MODE SWITCH SMALL
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSoft)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { }
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentViolet, AccentBlue)
                                ),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manual", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { vm.changeWorkoutMode(WorkoutMode.CAMERA_POSE) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷 Push-Up", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- REST TIMER CARD ----
        RestTimerCard(vm = vm)

        Spacer(Modifier.height(10.dp))

        // ---- EXERCISES LIST ----
        if (vm.editableEntries.isEmpty()) {
            EmptyWorkoutHint(onAddExercise = onAddExercise)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 12.dp,
                    top = 2.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.editableEntries, key = { it.entryId }) { entry ->
                    val ex = ExerciseRepository.findById(entry.exerciseId)
                    if (ex != null) {
                        ExerciseEntryCard(
                            vm = vm,
                            entryId = entry.entryId,
                            exerciseNameRes = ex.nameRes,
                            muscleGroup = ex.muscleGroup,
                            difficulty = ex.difficulty,
                            trackingType = ex.trackingType,
                            caloriesPerMinute = ex.caloriesPerMinute,
                            sets = entry.sets
                        )
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PloppingButton(
                            onClick = onAddExercise,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2A33)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue)
                                Text(stringResource(R.string.fitness_ws_add_exercise), color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ---- FIXED BOTTOM ACTION ----
        AnimatedVisibility(
            visible = vm.editableEntries.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column {
                val vol = vm.editableEntries.sumOf { e ->
                    e.sets.sumOf { s ->
                        (s.reps * s.weightKg).toDouble()
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            pluralStringResource(
                                R.plurals.fitness_history_session_exercises,
                                vm.editableEntries.size,
                                vm.editableEntries.size
                            ),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            "${vm.editableEntries.sumOf { it.sets.size }} " +
                                stringResource(R.string.fitness_ws_set_header),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.fitness_ws_total_volume),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            stringResource(R.string.fitness_ws_total_volume_kg, vol.toFloat()),
                            color = AccentGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                PloppingButton(
                    onClick = {
                        vm.saveWorkoutSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(
                            Modifier.background(
                                Brush.linearGradient(listOf(AccentGreen, AccentBlue)),
                                RoundedCornerShape(18.dp)
                            )
                        )
                ) {
                    Text(
                        stringResource(R.string.fitness_ws_finish),
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RestTimerCard(vm: FitnessViewModel) {
    val rem = vm.restTimerRemaining
    val total = vm.restTimerSeconds
    val running = vm.restTimerRunning
    val color = when {
        running && rem > 0 -> if (rem < 15) Color(0xFFFF4C4C) else AccentBlue
        !running && rem == 0 -> AccentGreen
        else -> AccentViolet
    }
    NeonBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        cornerRadius = RoundedCornerShape(18.dp),
        neonColors = listOf(color.copy(alpha = 0.8f), AccentBlue)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fitness_ws_rest_timer),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "%02d:%02d".format(rem / 60, rem % 60),
                    color = color,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            // Rest-Time Picker (30s/60s/90s/120s/180s)
            var expandRest by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgSoft)
                        .clickable { expandRest = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${total}s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = expandRest,
                    onDismissRequest = { expandRest = false },
                    containerColor = BgCard
                ) {
                    listOf(30, 45, 60, 90, 120, 180).forEach { t ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${t}s",
                                    color = if (t == total) AccentGreen else TextPrimary,
                                    fontWeight = if (t == total) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                vm.restTimerSeconds = t
                                if (!running) vm.restTimerRemaining = t
                                expandRest = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { vm.resetRestTimer() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(Icons.Default.RestartAlt, tint = TextSecondary, contentDescription = null)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = {
                    if (running) vm.resetRestTimer() else vm.startRestTimer()
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                if (running) Color(0xFFFF4C4C) else AccentGreen,
                                if (running) AccentPink else AccentBlue
                            )
                        )
                    )
            ) {
                Icon(
                    if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                    tint = Color.Black,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkoutHint(onAddExercise: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("🏋️", fontSize = 56.sp)
        Text(
            stringResource(R.string.fitness_ws_empty),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
        Text(
            stringResource(R.string.fitness_ws_empty_hint),
            color = TextTertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        PloppingButton(
            onClick = onAddExercise,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    Brush.linearGradient(listOf(AccentViolet, AccentBlue)),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.fitness_ws_add_exercise),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ExerciseEntryCard(
    vm: FitnessViewModel,
    entryId: String,
    exerciseNameRes: Int,
    muscleGroup: MuscleGroup,
    difficulty: Difficulty,
    trackingType: TrackingType,
    caloriesPerMinute: Float,
    sets: List<com.tabslify.tabs.fitnesstab.data.ExerciseSet>
) {
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(18.dp),
        neonColors = muscleGroup.groupColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            // HEADER
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(muscleGroup.groupEmoji(), fontSize = 22.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(exerciseNameRes),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DifficultyDotChip(difficulty)
                        Text("·", color = TextTertiary)
                        Text("${caloriesPerMinute.roundToInt()} kcal/min", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { vm.removeWorkoutEntry(entryId) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.fitness_ws_remove_exercise),
                        tint = AccentPink
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val useTime = trackingType == TrackingType.TIME || trackingType == TrackingType.DISTANCE
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
                    Text("#", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                    Text(
                        if (useTime) stringResource(R.string.fitness_ws_set_header_time)
                        else stringResource(R.string.fitness_ws_set_header_reps),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!useTime && trackingType != TrackingType.REPS) {
                    Box(Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.fitness_ws_set_header_weight),
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (useTime) {
                    Spacer(Modifier.weight(1.5f))
                }
                Box(Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
                    Text(
                        "✓",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // SET ROWS
            sets.forEachIndexed { idx, s ->
                SetRow(
                    entryId = entryId,
                    index = idx,
                    set = s,
                    useTime = useTime,
                    allowWeight = !useTime && trackingType != TrackingType.REPS,
                    onRemove = { vm.removeSetFromEntry(entryId, idx) },
                    onUpdate = { reps, weight, time, completed ->
                        vm.updateSetInEntry(
                            entryId = entryId,
                            setIndex = idx,
                            reps = reps,
                            weightKg = weight,
                            durationSeconds = time,
                            completed = completed
                        )
                    }
                )
                if (idx < sets.lastIndex) Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(10.dp))

            // ADD SET
            PloppingButton(
                onClick = { vm.addSetToEntry(entryId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2530)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.fitness_ws_add_set),
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    entryId: String,
    index: Int,
    set: com.tabslify.tabs.fitnesstab.data.ExerciseSet,
    useTime: Boolean,
    allowWeight: Boolean,
    onRemove: () -> Unit,
    onUpdate: (Int?, Float?, Int?, Boolean?) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (set.completed) Color(0x145BE388) else BgSoft),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(0.6f)
                .fillMaxSize()
                .combinedClickable(onClick = {}, onLongClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${set.setNumber}",
                color = if (set.completed) AccentGreen else TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
        }
        // Reps or Time
        Box(
            Modifier
                .weight(1.5f)
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 5.dp)
        ) {
            val current = if (useTime) set.durationSeconds else set.reps
            var text by remember(entryId, index, useTime) {
                mutableStateOf(if (current > 0) current.toString() else "")
            }
            LaunchedEffect(current) {
                if ((text.toIntOrNull() ?: 0) != current) {
                    text = if (current > 0) current.toString() else ""
                }
            }
            CompactSetField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = raw.filter { it.isDigit() }.take(4)
                    text = cleaned
                    val v = cleaned.toIntOrNull() ?: 0
                    if (useTime) onUpdate(null, null, v, null)
                    else onUpdate(v, null, null, null)
                },
                placeholder = if (useTime) "--" else "0",
                keyboardType = KeyboardType.Number
            )
        }
        // Weight (optional)
        if (allowWeight) {
            Box(
                Modifier
                    .weight(1.5f)
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 5.dp)
            ) {
                var weightText by remember(entryId, index) {
                    mutableStateOf(if (set.weightKg > 0f) formatWeight(set.weightKg) else "")
                }
                LaunchedEffect(set.weightKg) {
                    if ((weightText.toFloatOrNull() ?: 0f) != set.weightKg) {
                        weightText = if (set.weightKg > 0f) formatWeight(set.weightKg) else ""
                    }
                }
                CompactSetField(
                    value = weightText,
                    onValueChange = { raw ->
                        val cleaned = raw
                            .replace(',', '.')
                            .filter { it.isDigit() || it == '.' }
                            .let { txt ->
                                val firstDot = txt.indexOf('.')
                                if (firstDot < 0) txt
                                else txt.substring(0, firstDot + 1) + txt.substring(firstDot + 1).filter { it.isDigit() }
                            }
                            .take(6)
                        weightText = cleaned
                        onUpdate(null, cleaned.toFloatOrNull() ?: 0f, null, null)
                    },
                    placeholder = "0",
                    keyboardType = KeyboardType.Decimal
                )
            }
        } else if (useTime) {
            Spacer(Modifier.weight(1.5f))
        }
        // COMPLETED
        Box(
            Modifier
                .weight(0.6f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = set.completed,
                onCheckedChange = { onUpdate(null, null, null, it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentGreen,
                    uncheckedColor = TextTertiary,
                    checkmarkColor = Color.Black
                )
            )
        }
    }
}

@Composable
private fun CompactSetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(AccentViolet),
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(
                1.dp,
                if (focused) AccentViolet else Color(0x26FFFFFF),
                RoundedCornerShape(9.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 6.dp),
        decorationBox = { innerTextField ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = TextTertiary,
                        fontSize = 13.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

private fun formatWeight(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else String.format(java.util.Locale.US, "%.1f", kg)

@Composable
private fun DifficultyDotChip(level: Difficulty) {
    val c = when (level) {
        Difficulty.BEGINNER -> AccentGreen
        Difficulty.INTERMEDIATE -> AccentYellow
        Difficulty.ADVANCED -> Color(0xFFFF4C4C)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(c))
        Text(stringResource(level.nameRes()), color = TextSecondary, fontSize = 11.sp)
    }
}

private fun MuscleGroup.nameRes(): Int = when (this) {
    MuscleGroup.CHEST -> R.string.fitness_exercises_group_chest
    MuscleGroup.BACK -> R.string.fitness_exercises_group_back
    MuscleGroup.LEGS -> R.string.fitness_exercises_group_legs
    MuscleGroup.CORE -> R.string.fitness_exercises_group_core
    MuscleGroup.SHOULDERS -> R.string.fitness_exercises_group_shoulders
    MuscleGroup.ARMS -> R.string.fitness_exercises_group_arms
    MuscleGroup.CARDIO -> R.string.fitness_exercises_group_cardio
    MuscleGroup.FULLBODY -> R.string.fitness_exercises_group_fullbody
}

private fun Difficulty.nameRes(): Int = when (this) {
    Difficulty.BEGINNER -> R.string.fitness_ex_difficulty_beginner
    Difficulty.INTERMEDIATE -> R.string.fitness_ex_difficulty_intermediate
    Difficulty.ADVANCED -> R.string.fitness_ex_difficulty_advanced
}

private fun Equipment.nameRes(): Int = when (this) {
    Equipment.NONE -> R.string.fitness_ex_gear_none
    Equipment.RESISTANCE_BAND -> R.string.fitness_ex_gear_band
    Equipment.DUMBBELL -> R.string.fitness_ex_gear_dumbbell
    Equipment.BARBELL -> R.string.fitness_ex_gear_barbell
    Equipment.KETTLEBELL -> R.string.fitness_ex_gear_kettlebell
    Equipment.PULLUP_BAR -> R.string.fitness_ex_gear_pullupbar
    Equipment.BENCH -> R.string.fitness_ex_gear_bench
}

// ========= EXERCISE PICKER DIALOG =========
@Composable
private fun ExercisePickerDialog(
    vm: FitnessViewModel,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf(vm.exercisesSearch.ifBlank { "" }) }
    var groupFilter by remember { mutableStateOf(vm.exercisesGroupFilter) }
    var diffFilter by remember { mutableStateOf(vm.exercisesDifficultyFilter) }
    var eqFilter by remember { mutableStateOf(vm.exercisesEquipmentFilter) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        AppBackground(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                // TOP BAR
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeonBox(
                        modifier = Modifier.size(42.dp),
                        cornerRadius = RoundedCornerShape(12.dp),
                        neonColors = listOf(AccentViolet, AccentBlue),
                        onClick = onDismiss
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.fitness_picker_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // SEARCH
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.fitness_ex_search), color = TextTertiary)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BgCard,
                        focusedContainerColor = BgCard,
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = Color(0x30FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentViolet
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(Modifier.height(8.dp))

                // FILTER CHIPS
                val allRes = R.string.fitness_exercises_filters_all
                val groups: List<Pair<Int, MuscleGroup?>> =
                    listOf(allRes to null) + MuscleGroup.entries.map { it.nameRes() to it }
                val diffs: List<Pair<Int, Difficulty?>> =
                    listOf(allRes to null) + Difficulty.entries.map { it.nameRes() to it }
                val eqs: List<Pair<Int, Equipment?>> =
                    listOf(allRes to null) + Equipment.entries.map { it.nameRes() to it }

                FilterChipRow(
                    options = groups,
                    selected = groupFilter,
                    onSelect = { groupFilter = it.second }
                )
                Spacer(Modifier.height(6.dp))
                FilterChipRow(
                    options = diffs,
                    selected = diffFilter,
                    onSelect = { diffFilter = it.second }
                )
                Spacer(Modifier.height(6.dp))
                FilterChipRow(
                    options = eqs,
                    selected = eqFilter,
                    onSelect = { eqFilter = it.second }
                )

                Spacer(Modifier.height(10.dp))

                // LIST
                val allEx = remember { ExerciseRepository.all() }
                val namesById = allEx.associate { ex -> ex.id to stringResource(ex.nameRes).lowercase() }
                val filtered by remember(search, groupFilter, diffFilter, eqFilter, allEx, namesById) {
                    derivedStateOf {
                        allEx.filter { ex ->
                            if (groupFilter != null && ex.muscleGroup != groupFilter) return@filter false
                            if (diffFilter != null && ex.difficulty != diffFilter) return@filter false
                            if (eqFilter != null && ex.equipment != eqFilter) return@filter false
                            if (search.isNotBlank()) {
                                val s = search.trim().lowercase()
                                val name = namesById[ex.id].orEmpty()
                                if (s !in name && s !in ex.id.lowercase()) return@filter false
                            }
                            true
                        }
                    }
                }

                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { ex ->
                        PickableExerciseRow(exercise = ex, onPick = {
                            vm.addExerciseToWorkout(ex.id)
                            onDismiss()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> FilterChipRow(
    options: List<Pair<Int, T>>,
    selected: T?,
    onSelect: (Pair<Int, T>) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 10.dp)
    ) {
        items(options) { opt ->
            val isSelected = opt.second == selected
            val bg = if (isSelected) Brush.linearGradient(listOf(AccentViolet, AccentBlue)) else null
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (bg != null) Modifier.background(bg)
                        else Modifier.background(BgCard).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                    )
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(opt.first),
                    color = if (isSelected) Color.White else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PickableExerciseRow(
    exercise: com.tabslify.tabs.fitnesstab.data.Exercise,
    onPick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .clickable { onPick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(exercise.muscleGroup.groupColors().map { it.copy(alpha = 0.35f) })
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(exercise.muscleGroup.groupEmoji(), fontSize = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(exercise.nameRes),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DifficultyDotChip(exercise.difficulty)
                Text("·", color = TextTertiary, fontSize = 10.sp)
                Text(
                    stringResource(exercise.equipment.nameRes()),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Icon(Icons.Default.Add, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
    }
}
