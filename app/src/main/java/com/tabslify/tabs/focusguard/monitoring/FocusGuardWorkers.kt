package com.tabslify.tabs.focusguard.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.tabs.focusguard.FocusGuardNotifications
import com.tabslify.tabs.focusguard.data.FocusGuardConfig
import com.tabslify.tabs.focusguard.data.FocusGuardDatabase
import com.tabslify.tabs.focusguard.data.FocusGuardRepository
import com.tabslify.tabs.focusguard.data.todayYmd
import com.tabslify.tabs.focusguard.formatDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

class FocusGuardUsageLogWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            FocusGuardRepository.init(ctx)
            val today = LocalDate.now()
            FocusGuardRepository.logUsage(UsageTracker.querySessions(ctx, today))
            FocusGuardRepository.ensureTodayGoal()
            warnAboutSleep(ctx, today)
            FocusGuardRepository.syncToSupabase()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun warnAboutSleep(ctx: Context, today: LocalDate) {
        if (!FocusGuardConfig.notificationsEnabled) return
        val sleep = SleepTracker.computeSleep(ctx, today.minusDays(1)) ?: return
        FocusGuardRepository.upsertSleep(sleep)
        val warnedKey = "sleep_warned_${sleep.date}"
        val prefs = ctx.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean(warnedKey, false)) return
        val thresholdMs = FocusGuardConfig.sleepThresholdMin * 60_000L
        if (sleep.durationMs < thresholdMs) {
            FocusGuardNotifications.postSleepWarning(
                ctx,
                formatDurationMs(sleep.durationMs),
                formatDurationMs(thresholdMs)
            )
            prefs.edit { putBoolean(warnedKey, true) }
        }
    }
}

class FocusGuardDailyResetWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            FocusGuardRepository.init(ctx)
            val db = FocusGuardDatabase.get(ctx)
            val yesterday = LocalDate.now().minusDays(1)
            val yesterdayYmd = yesterday.toString()

            val sessions = UsageTracker.querySessions(ctx, yesterday)
            FocusGuardRepository.logUsage(sessions)

            val alreadyProcessed = FocusGuardConfig.lastActiveDayYmd == yesterdayYmd
            if (!alreadyProcessed) {
                val rules = db.restrictionRuleDao().all()
                val schoolDay = FocusGuardConfig.schoolCalendarEnabled &&
                    BavarianSchoolCalendar.isSchoolDay(yesterday)
                val goal = db.studyGoalDao().forDate(yesterdayYmd)
                val quotaDone = goal != null && goal.completedCount >= goal.targetCount
                val result = GamificationEngine.evaluateDay(
                    logs = sessions,
                    rules = rules,
                    afternoonThresholdMin = FocusGuardConfig.afternoonThresholdMin,
                    quotaDone = quotaDone,
                    schoolDay = schoolDay,
                    date = yesterday
                )

                FocusGuardConfig.points += result.points

                val previousExpected = yesterday.minusDays(1).toString()
                val lastActive = FocusGuardConfig.lastActiveDayYmd
                val newStreak = when (lastActive) {
                    yesterdayYmd -> FocusGuardConfig.currentStreak
                    previousExpected ->
                        if (result.success) FocusGuardConfig.currentStreak + 1 else 0

                    else -> if (result.success) 1 else 0
                }
                FocusGuardConfig.currentStreak = newStreak
                FocusGuardConfig.lastActiveDayYmd = yesterdayYmd

                GamificationEngine.newlyEarnedAchievements(newStreak, FocusGuardConfig.points)
                    .forEach { (type, pts) -> FocusGuardRepository.recordAchievement(type, pts) }
            }

            SleepTracker.computeSleep(ctx, yesterday.minusDays(1))?.let {
                FocusGuardRepository.upsertSleep(it)
            }

            db.appUsageLogDao().deleteSyncedBefore(System.currentTimeMillis() - RETENTION_MS)

            FocusGuardRepository.syncToSupabase()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private companion object {
        const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000
    }
}

class FocusGuardStudyReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            FocusGuardRepository.init(ctx)
            FocusGuardRepository.ensureTodayGoal()

            val schoolDay = FocusGuardConfig.schoolCalendarEnabled && RestrictionEngine.todayIsSchoolDay()
            if (!schoolDay && StudyGoalTracker.needsReminder()) {
                val goal = StudyGoalTracker.todayGoal()
                val remaining = maxOf(0, (goal?.targetCount ?: 0) - (goal?.completedCount ?: 0))
                FocusGuardNotifications.postStudyReminder(ctx, remaining)
                FocusGuardRepository.markGoalReminderSent()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class FocusGuardSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            FocusGuardRepository.init(applicationContext)
            FocusGuardRepository.syncToSupabase()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class FocusGuardDailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        appScope.launch(Dispatchers.IO) {
            runCatching { FocusGuardSummaryRunner.run(context) }
        }
        scheduleFocusGuardDailySummary(context)
    }
}

object FocusGuardSummaryRunner {
    suspend fun run(context: Context) {
        FocusGuardRepository.init(context)
        val db = FocusGuardDatabase.get(context)
        val todayYmd = todayYmd()
        val totals = db.appUsageLogDao().totalsByCategory(todayYmd)
            .associate { it.category to it.total }

        val usageText = buildUsageText(context, totals)
        val goal = db.studyGoalDao().forDate(todayYmd)
        val goalText = buildGoalText(context, goal?.completedCount ?: 0, goal?.targetCount ?: 0)
        val sleep = SleepTracker.computeSleep(context, LocalDate.now().minusDays(1))
        sleep?.let { FocusGuardRepository.upsertSleep(it) }
        val sleepText = if (sleep != null) {
            "${context.getString(com.tabslify.R.string.focusguard_summary_sleep)} ${formatDurationMs(sleep.durationMs)}"
        } else {
            context.getString(com.tabslify.R.string.focusguard_summary_no_sleep)
        }

        FocusGuardNotifications.postDailySummary(context, usageText, goalText, sleepText)
    }

    private fun buildUsageText(context: Context, totals: Map<String, Long>): String {
        val sb = StringBuilder()
        fun append(category: String, labelRes: Int) {
            val ms = totals[category] ?: 0L
            if (ms > 0) {
                if (sb.isNotEmpty()) sb.append(" · ")
                sb.append(context.getString(labelRes)).append(": ").append(formatDurationMs(ms))
            }
        }
        append("SOCIAL", com.tabslify.R.string.focusguard_summary_social)
        append("GAMING", com.tabslify.R.string.focusguard_summary_gaming)
        append("ENTERTAINMENT", com.tabslify.R.string.focusguard_summary_entertainment)
        val result = if (sb.isEmpty()) context.getString(com.tabslify.R.string.focusguard_summary_no_usage)
        else sb.toString()

        val threshold = FocusGuardConfig.excessiveUsageThresholdMin * 60_000L
        val excessive = listOf(
            "SOCIAL" to com.tabslify.R.string.focusguard_cat_social,
            "GAMING" to com.tabslify.R.string.focusguard_cat_gaming,
            "ENTERTAINMENT" to com.tabslify.R.string.focusguard_cat_entertainment
        ).firstOrNull { (category, _) -> (totals[category] ?: 0L) > threshold }

        return if (excessive != null) {
            "$result\n${context.resources.getQuantityString(
                com.tabslify.R.plurals.focusguard_summary_excessive,
                FocusGuardConfig.excessiveUsageThresholdMin,
                context.getString(excessive.second),
                FocusGuardConfig.excessiveUsageThresholdMin
            )}"
        } else result
    }

    private fun buildGoalText(context: Context, completed: Int, target: Int): String {
        if (target <= 0) return context.getString(com.tabslify.R.string.focusguard_summary_no_goal)
        return if (completed >= target) {
            context.getString(com.tabslify.R.string.focusguard_summary_goal_done)
        } else {
            context.getString(
                com.tabslify.R.string.focusguard_summary_goal_progress,
                completed, target
            )
        }
    }
}

fun scheduleFocusGuardDailySummary(context: Context) {
    FocusGuardConfig.init(context)
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    if (!am.canScheduleExactAlarms()) return
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, FocusGuardConfig.dailySummaryHour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    val summaryIntent = Intent(context, FocusGuardDailySummaryReceiver::class.java).apply {
        setPackage(context.packageName)
    }
    val pi = PendingIntent.getBroadcast(
        context,
        DAILY_SUMMARY_REQUEST_CODE,
        summaryIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
}

fun scheduleFocusGuardWorkers(context: Context) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "focusguard_usage_log",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<FocusGuardUsageLogWorker>(15, TimeUnit.MINUTES).build()
    )
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "focusguard_daily_reset",
        ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<FocusGuardDailyResetWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
    )
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "focusguard_study_reminder",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<FocusGuardStudyReminderWorker>(15, TimeUnit.MINUTES).build()
    )
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "focusguard_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<FocusGuardSyncWorker>(6, TimeUnit.HOURS).build()
    )
}

fun cancelFocusGuardWorkers(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("focusguard_usage_log")
    WorkManager.getInstance(context).cancelUniqueWork("focusguard_daily_reset")
    WorkManager.getInstance(context).cancelUniqueWork("focusguard_study_reminder")
    WorkManager.getInstance(context).cancelUniqueWork("focusguard_sync")
}

private const val DAILY_SUMMARY_REQUEST_CODE = 96000
