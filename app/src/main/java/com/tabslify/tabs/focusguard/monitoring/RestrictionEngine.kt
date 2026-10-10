package com.tabslify.tabs.focusguard.monitoring

import com.tabslify.tabs.focusguard.data.CATEGORY_ENTERTAINMENT
import com.tabslify.tabs.focusguard.data.FOCUSGUARD_RESTRICTED_CATEGORIES
import com.tabslify.tabs.focusguard.data.RestrictionRule
import com.tabslify.tabs.focusguard.data.daysOfWeekMaskFor
import com.tabslify.tabs.focusguard.data.minuteOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class BlockReason {
    AFTERNOON_THRESHOLD,
    CUSTOM_WINDOW,
    RESTING,
    STUDY_QUOTA,
    NONE
}

data class BlockDecision(
    val blocked: Boolean,
    val reason: BlockReason,
    val untilMs: Long,
    val rule: RestrictionRule? = null
)

object RestrictionEngine {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun blockedFor(
        category: String,
        now: Long,
        rules: List<RestrictionRule>,
        afternoonThresholdMin: Int,
        isSchoolDay: Boolean,
        isResting: Boolean,
        quotaDone: Boolean,
        overrideUntilMs: Long
    ): BlockDecision {
        if (category !in FOCUSGUARD_RESTRICTED_CATEGORIES) return BlockDecision(false, BlockReason.NONE, 0L)
        if (now < overrideUntilMs) return BlockDecision(false, BlockReason.NONE, 0L)
        if (isSchoolDay) return BlockDecision(false, BlockReason.NONE, 0L)

        if (isResting) {
            return BlockDecision(true, BlockReason.RESTING, endOfDayMs(now))
        }

        if (category == CATEGORY_ENTERTAINMENT && !quotaDone) {
            return BlockDecision(true, BlockReason.STUDY_QUOTA, endOfDayMs(now))
        }

        val rule = rules.firstOrNull { it.category == category }
        if (rule != null && !rule.enabled) return BlockDecision(false, BlockReason.NONE, 0L)
        if (rule != null && !dayMatches(rule, now)) return BlockDecision(false, BlockReason.NONE, 0L)

        val minute = minuteOfDay(now)
        val inWindow = if (rule != null) {
            if (rule.startMinute <= rule.endMinute) {
                minute in rule.startMinute..<rule.endMinute
            } else {
                minute >= rule.startMinute || minute < rule.endMinute
            }
        } else {
            minute >= afternoonThresholdMin
        }

        if (!inWindow) return BlockDecision(false, BlockReason.NONE, 0L)

        val until = if (rule != null) windowEndMs(now, rule.endMinute) else endOfDayMs(now)
        return BlockDecision(
            true,
            if (rule != null) BlockReason.CUSTOM_WINDOW else BlockReason.AFTERNOON_THRESHOLD,
            until,
            rule
        )
    }

    fun restrictionsPossibleToday(
        restrictedCategories: Collection<String>,
        rules: List<RestrictionRule>,
        isSchoolDay: Boolean,
        overrideUntilMs: Long,
        now: Long
    ): Boolean {
        if (isSchoolDay) return false
        if (now < overrideUntilMs) return false
        if (restrictedCategories.isEmpty()) return false
        val active = restrictedCategories.filter { it in FOCUSGUARD_RESTRICTED_CATEGORIES }
        return active.isNotEmpty() && active.any { category ->
            val rule = rules.firstOrNull { it.category == category }
            when {
                rule != null && !rule.enabled -> false
                rule != null -> dayMatches(rule, now)
                else -> true
            }
        }
    }

    private fun dayMatches(rule: RestrictionRule, now: Long): Boolean {
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return (rule.daysOfWeekMask and daysOfWeekMaskFor(date)) != 0
    }

    private fun endOfDayMs(now: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun windowEndMs(now: Long, endMinute: Int): Long {
        val zone = ZoneId.systemDefault()
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        var end = zdt.toLocalDate()
            .atTime(endMinute / 60, endMinute % 60)
            .atZone(zone).toInstant().toEpochMilli()
        if (end <= now) end += DAY_MS
        return end
    }

    fun todayIsSchoolDay(): Boolean =
        BavarianSchoolCalendar.isSchoolDay(LocalDate.now())
}
