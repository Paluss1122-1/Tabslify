package com.tabslify.tabs

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.APP_COLOR
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.BgSurface
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Composable
fun DateCalculatorContent(modifier: Modifier = Modifier) {
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var daysDifference by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.tage_rechner),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        DateSelectionCard(
            label = stringResource(R.string.startdatum),
            selectedDate = startDate,
            onDateSelected = { date ->
                startDate = date
                if (endDate != null) {
                    daysDifference = ChronoUnit.DAYS.between(date, endDate)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        DateSelectionCard(
            label = stringResource(R.string.enddatum),
            selectedDate = endDate,
            onDateSelected = { date ->
                endDate = date
                if (startDate != null) {
                    daysDifference = ChronoUnit.DAYS.between(startDate, date)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (daysDifference != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = APP_COLOR
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.differenz),
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.tage,
                            daysDifference!!.toInt(), daysDifference!!),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.wochen_und_tage,
                            daysDifference!! / 7,
                            daysDifference!! % 7
                        ),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                startDate = null
                endDate = null
                daysDifference = null
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = APP_COLOR,
                contentColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.zurucksetzen), fontSize = 16.sp)
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun DateSelectionCard(
    label: String,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = APP_COLOR
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = { showPicker = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (selectedDate != null) {
                        String.format(
                            "%02d.%02d.%d",
                            selectedDate.dayOfMonth,
                            selectedDate.monthValue,
                            selectedDate.year
                        )
                    } else {
                        stringResource(R.string.datum_auswahlen)
                    },
                    fontSize = 18.sp
                )
            }
        }
    }

    if (showPicker) {
        AppDatePickerDialog(
            initialDate = selectedDate,
            onDismiss = { showPicker = false },
            onDateSelected = {
                onDateSelected(it)
                showPicker = false
            }
        )
    }
}

@Composable
private fun AppDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initialDate ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    )

    val colors = DatePickerDefaults.colors(
        containerColor = BgSurface,
        titleContentColor = TextSecondary,
        headlineContentColor = TextPrimary,
        weekdayContentColor = TextSecondary,
        subheadContentColor = TextSecondary,
        navigationContentColor = TextPrimary,
        yearContentColor = TextSecondary,
        currentYearContentColor = AccentViolet,
        selectedYearContentColor = Color.White,
        selectedYearContainerColor = AccentViolet,
        dayContentColor = TextPrimary,
        disabledDayContentColor = TextTertiary,
        selectedDayContentColor = Color.White,
        selectedDayContainerColor = AccentViolet,
        todayContentColor = AccentViolet,
        todayDateBorderColor = AccentViolet,
        dayInSelectionRangeContentColor = TextPrimary,
        dayInSelectionRangeContainerColor = AccentViolet.copy(alpha = 0.3f),
        dividerColor = Color.White.copy(alpha = 0.12f)
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateSelected(
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        )
                    } ?: onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok), color = AccentViolet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel), color = TextSecondary)
            }
        }
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            colors = colors
        )
    }
}