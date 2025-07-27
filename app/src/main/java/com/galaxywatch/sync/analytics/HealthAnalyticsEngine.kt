package com.galaxywatch.sync.analytics

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.*
import android.health.connect.time.TimeRangeFilter
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Enhanced health analytics engine for WearOS Galaxy Watch integration Provides comprehensive
 * analysis of health data with insights and trends
 */
class HealthAnalyticsEngine(private val context: Context) {

    private val healthConnectManager = HealthConnectManager.getOrCreate(context)
    private val TAG = "HealthAnalyticsEngine"

    data class HealthSummary(
            val heartRate: HeartRateSummary,
            val steps: StepsSummary,
            val sleep: SleepSummary,
            val activity: ActivitySummary,
            val timestamp: Long = System.currentTimeMillis()
    )

    data class HeartRateSummary(
            val count: Int,
            val average: Int,
            val min: Int,
            val max: Int,
            val trend: String,
            val zone: String,
            val restingHeartRate: Int? = null
    )

    data class StepsSummary(
            val total: Long,
            val dailyAverage: Int,
            val goalDays: Int,
            val totalDays: Int,
            val goalPercentage: Float
    )

    data class SleepSummary(
            val count: Int,
            val averageDuration: Float, // in hours
            val quality: Int, // 0-100 score
            val optimalNights: Int
    )

    data class ActivitySummary(
            val totalActiveMinutes: Long,
            val caloriesBurned: Long,
            val distanceCovered: Float, // in km
            val workoutsCompleted: Int
    )

    data class HealthInsight(
            val type: String,
            val category: String, // excellent, good, warning, improvement
            val title: String,
            val message: String,
            val actionable: String,
            val confidence: Float
    )

    data class HealthGoals(
            val dailySteps: Int = 10000,
            val sleepHours: Float = 8.0f,
            val activeMinutes: Int = 30,
            val caloriesBurned: Int = 500,
            val waterIntake: Int = 8,
            val maxRestingHeartRate: Int = 100
    )

    /** Generate comprehensive health summary for the specified time period */
    suspend fun generateHealthSummary(days: Int = 7): HealthSummary {
        val endTime = Instant.now()
        val startTime = endTime.minus(days.toLong(), ChronoUnit.DAYS)
        val timeRange = TimeRangeFilter.between(startTime, endTime)

        return HealthSummary(
                heartRate = getHeartRateSummary(timeRange),
                steps = getStepsSummary(timeRange),
                sleep = getSleepSummary(timeRange),
                activity = getActivitySummary(timeRange)
        )
    }

    /** Get heart rate analytics with zones and trends */
    private suspend fun getHeartRateSummary(timeRange: TimeRangeFilter): HeartRateSummary {
        try {
            val heartRateRecords =
                    healthConnectManager.readRecords(HeartRateRecord::class, timeRange)

            if (heartRateRecords.isEmpty()) {
                return HeartRateSummary(0, 0, 0, 0, "stable", "unknown")
            }

            val heartRates = heartRateRecords.map { it.beatsPerMinute.toInt() }
            val average = heartRates.average().roundToInt()
            val min = heartRates.minOrNull() ?: 0
            val max = heartRates.maxOrNull() ?: 0

            // Calculate trend
            val trend = calculateHeartRateTrend(heartRates)

            // Determine heart rate zone
            val zone = getHeartRateZone(average)

            // Get resting heart rate if available
            val restingHR = getRestingHeartRate(timeRange)

            return HeartRateSummary(
                    count = heartRates.size,
                    average = average,
                    min = min,
                    max = max,
                    trend = trend,
                    zone = zone,
                    restingHeartRate = restingHR
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting heart rate summary: ${e.message}")
            return HeartRateSummary(0, 0, 0, 0, "stable", "unknown")
        }
    }

    /** Get steps analytics with goal tracking */
    private suspend fun getStepsSummary(timeRange: TimeRangeFilter): StepsSummary {
        try {
            val stepsRecords = healthConnectManager.readRecords(StepsRecord::class, timeRange)

            // Group by day
            val dailySteps = mutableMapOf<String, Long>()
            stepsRecords.forEach { record ->
                val date =
                        LocalDateTime.ofInstant(record.startTime, ZoneId.systemDefault())
                                .toLocalDate()
                                .toString()
                dailySteps[date] = (dailySteps[date] ?: 0) + record.count
            }

            val totalSteps = dailySteps.values.sum()
            val dailyAverage =
                    if (dailySteps.isNotEmpty()) {
                        (totalSteps / dailySteps.size).toInt()
                    } else 0

            val goalDays = dailySteps.values.count { it >= 10000 }
            val totalDays = dailySteps.size
            val goalPercentage =
                    if (totalDays > 0) {
                        (goalDays.toFloat() / totalDays) * 100
                    } else 0f

            return StepsSummary(
                    total = totalSteps,
                    dailyAverage = dailyAverage,
                    goalDays = goalDays,
                    totalDays = totalDays,
                    goalPercentage = goalPercentage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting steps summary: ${e.message}")
            return StepsSummary(0, 0, 0, 0, 0f)
        }
    }

    /** Get sleep analytics with quality scoring */
    private suspend fun getSleepSummary(timeRange: TimeRangeFilter): SleepSummary {
        try {
            val sleepRecords =
                    healthConnectManager.readRecords(SleepSessionRecord::class, timeRange)

            if (sleepRecords.isEmpty()) {
                return SleepSummary(0, 0f, 0, 0)
            }

            val durations =
                    sleepRecords.map { record ->
                        val duration = ChronoUnit.MINUTES.between(record.startTime, record.endTime)
                        duration / 60.0f // Convert to hours
                    }

            val averageDuration = durations.average().toFloat()
            val quality = calculateSleepQuality(durations)
            val optimalNights = durations.count { it >= 7f && it <= 9f }

            return SleepSummary(
                    count = sleepRecords.size,
                    averageDuration = averageDuration,
                    quality = quality,
                    optimalNights = optimalNights
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sleep summary: ${e.message}")
            return SleepSummary(0, 0f, 0, 0)
        }
    }

    /** Get activity analytics including calories and distance */
    private suspend fun getActivitySummary(timeRange: TimeRangeFilter): ActivitySummary {
        try {
            val activeMinutes = getActiveMinutes(timeRange)
            val calories = getTotalCalories(timeRange)
            val distance = getTotalDistance(timeRange)
            val workouts = getWorkoutCount(timeRange)

            return ActivitySummary(
                    totalActiveMinutes = activeMinutes,
                    caloriesBurned = calories,
                    distanceCovered = distance,
                    workoutsCompleted = workouts
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting activity summary: ${e.message}")
            return ActivitySummary(0, 0, 0f, 0)
        }
    }

    /** Generate AI-powered health insights */
    suspend fun generateHealthInsights(category: String = "all"): List<HealthInsight> {
        val insights = mutableListOf<HealthInsight>()
        val summary = generateHealthSummary(7)

        if (category == "all" || category == "heart_rate") {
            insights.addAll(generateHeartRateInsights(summary.heartRate))
        }

        if (category == "all" || category == "steps") {
            insights.addAll(generateStepsInsights(summary.steps))
        }

        if (category == "all" || category == "sleep") {
            insights.addAll(generateSleepInsights(summary.sleep))
        }

        if (category == "all" || category == "activity") {
            insights.addAll(generateActivityInsights(summary.activity))
        }

        return insights
    }

    /** Generate trends data for visualization */
    fun generateTrends(dataType: String, period: String, days: Int): Flow<List<TrendData>> = flow {
        val endTime = Instant.now()
        val startTime = endTime.minus(days.toLong(), ChronoUnit.DAYS)
        val timeRange = TimeRangeFilter.between(startTime, endTime)

        when (dataType.lowercase()) {
            "heart_rate" -> emit(getHeartRateTrends(timeRange, period))
            "steps" -> emit(getStepsTrends(timeRange, period))
            "sleep" -> emit(getSleepTrends(timeRange, period))
            "calories" -> emit(getCaloriesTrends(timeRange, period))
            else -> emit(emptyList())
        }
    }

    data class TrendData(
            val period: String,
            val average: Float,
            val minimum: Float,
            val maximum: Float,
            val count: Int
    )

    // Helper Methods

    private fun calculateHeartRateTrend(heartRates: List<Int>): String {
        if (heartRates.size < 10) return "stable"

        val firstHalf = heartRates.take(heartRates.size / 2)
        val secondHalf = heartRates.drop(heartRates.size / 2)

        val firstAvg = firstHalf.average()
        val secondAvg = secondHalf.average()

        return when {
            secondAvg > firstAvg + 5 -> "increasing"
            secondAvg < firstAvg - 5 -> "decreasing"
            else -> "stable"
        }
    }

    private fun getHeartRateZone(average: Int): String {
        return when {
            average < 60 -> "bradycardia"
            average in 60..100 -> "normal"
            average in 101..150 -> "elevated"
            else -> "tachycardia"
        }
    }

    private suspend fun getRestingHeartRate(timeRange: TimeRangeFilter): Int? {
        return try {
            val restingHRRecords =
                    healthConnectManager.readRecords(RestingHeartRateRecord::class, timeRange)
            restingHRRecords.lastOrNull()?.beatsPerMinute?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSleepQuality(durations: List<Float>): Int {
        val averageDuration = durations.average()
        val consistency = calculateSleepConsistency(durations)

        // Quality score based on duration and consistency
        val durationScore =
                when {
                    averageDuration >= 7 && averageDuration <= 9 -> 100
                    averageDuration >= 6 && averageDuration <= 10 -> 80
                    averageDuration >= 5 && averageDuration <= 11 -> 60
                    else -> 40
                }

        return ((durationScore * 0.7) + (consistency * 0.3)).roundToInt()
    }

    private fun calculateSleepConsistency(durations: List<Float>): Int {
        if (durations.size < 2) return 100

        val variance =
                durations.map { (it - durations.average()).let { diff -> diff * diff } }.average()
        val standardDeviation = kotlin.math.sqrt(variance)

        // Lower standard deviation = higher consistency
        return when {
            standardDeviation < 0.5 -> 100
            standardDeviation < 1.0 -> 80
            standardDeviation < 1.5 -> 60
            else -> 40
        }
    }

    private suspend fun getActiveMinutes(timeRange: TimeRangeFilter): Long {
        return try {
            val exerciseRecords =
                    healthConnectManager.readRecords(ExerciseSessionRecord::class, timeRange)
            exerciseRecords.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun getTotalCalories(timeRange: TimeRangeFilter): Long {
        return try {
            val calorieRecords =
                    healthConnectManager.readRecords(ActiveCaloriesBurnedRecord::class, timeRange)
            calorieRecords.sumOf { it.energy.inKilocalories.toLong() }
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun getTotalDistance(timeRange: TimeRangeFilter): Float {
        return try {
            val distanceRecords = healthConnectManager.readRecords(DistanceRecord::class, timeRange)
            distanceRecords.sumOf { it.distance.inKilometers }.toFloat()
        } catch (e: Exception) {
            0f
        }
    }

    private suspend fun getWorkoutCount(timeRange: TimeRangeFilter): Int {
        return try {
            val exerciseRecords =
                    healthConnectManager.readRecords(ExerciseSessionRecord::class, timeRange)
            exerciseRecords.size
        } catch (e: Exception) {
            0
        }
    }

    // Insight Generation Methods

    private fun generateHeartRateInsights(heartRate: HeartRateSummary): List<HealthInsight> {
        val insights = mutableListOf<HealthInsight>()

        if (heartRate.count == 0) return insights

        when {
            heartRate.average < 60 ->
                    insights.add(
                            HealthInsight(
                                    type = "heart_rate",
                                    category = "excellent",
                                    title = "Excellent Resting Heart Rate",
                                    message =
                                            "Your average heart rate of ${heartRate.average} BPM indicates excellent cardiovascular fitness.",
                                    actionable =
                                            "Continue your current fitness routine to maintain this healthy level.",
                                    confidence = 0.9f
                            )
                    )
            heartRate.average > 100 ->
                    insights.add(
                            HealthInsight(
                                    type = "heart_rate",
                                    category = "warning",
                                    title = "Elevated Heart Rate Detected",
                                    message =
                                            "Your average heart rate of ${heartRate.average} BPM is higher than normal.",
                                    actionable =
                                            "Consider consulting a healthcare provider and reviewing your stress levels.",
                                    confidence = 0.8f
                            )
                    )
        }

        return insights
    }

    private fun generateStepsInsights(steps: StepsSummary): List<HealthInsight> {
        val insights = mutableListOf<HealthInsight>()

        when {
            steps.goalPercentage >= 80 ->
                    insights.add(
                            HealthInsight(
                                    type = "steps",
                                    category = "excellent",
                                    title = "Outstanding Activity Level",
                                    message =
                                            "You're achieving your step goal ${steps.goalPercentage.roundToInt()}% of the time!",
                                    actionable =
                                            "Keep up the excellent work! Consider increasing your goal to continue challenging yourself.",
                                    confidence = 0.95f
                            )
                    )
            steps.goalPercentage < 30 ->
                    insights.add(
                            HealthInsight(
                                    type = "steps",
                                    category = "improvement",
                                    title = "Opportunity to Increase Activity",
                                    message =
                                            "You're averaging ${steps.dailyAverage} steps per day, below the recommended 10,000.",
                                    actionable =
                                            "Try taking short walks throughout the day or using stairs instead of elevators.",
                                    confidence = 0.85f
                            )
                    )
        }

        return insights
    }

    private fun generateSleepInsights(sleep: SleepSummary): List<HealthInsight> {
        val insights = mutableListOf<HealthInsight>()

        if (sleep.count == 0) return insights

        when {
            sleep.averageDuration >= 7f && sleep.averageDuration <= 9f ->
                    insights.add(
                            HealthInsight(
                                    type = "sleep",
                                    category = "excellent",
                                    title = "Optimal Sleep Duration",
                                    message =
                                            "Your average sleep of ${String.format("%.1f", sleep.averageDuration)} hours is in the optimal range.",
                                    actionable =
                                            "Maintain your current sleep schedule for continued health benefits.",
                                    confidence = 0.9f
                            )
                    )
            sleep.averageDuration < 6f ->
                    insights.add(
                            HealthInsight(
                                    type = "sleep",
                                    category = "warning",
                                    title = "Insufficient Sleep Detected",
                                    message =
                                            "Your average sleep of ${String.format("%.1f", sleep.averageDuration)} hours is below recommended levels.",
                                    actionable =
                                            "Try to establish a consistent bedtime routine and limit screen time before bed.",
                                    confidence = 0.85f
                            )
                    )
        }

        return insights
    }

    private fun generateActivityInsights(activity: ActivitySummary): List<HealthInsight> {
        val insights = mutableListOf<HealthInsight>()

        if (activity.totalActiveMinutes >= 150) {
            insights.add(
                    HealthInsight(
                            type = "activity",
                            category = "excellent",
                            title = "Meeting Activity Guidelines",
                            message =
                                    "You're exceeding the recommended 150 minutes of activity per week!",
                            actionable =
                                    "Great job! Consider adding strength training to your routine.",
                            confidence = 0.9f
                    )
            )
        } else if (activity.totalActiveMinutes < 75) {
            insights.add(
                    HealthInsight(
                            type = "activity",
                            category = "improvement",
                            title = "Increase Physical Activity",
                            message =
                                    "You're getting ${activity.totalActiveMinutes} minutes of activity per week, below the recommended 150 minutes.",
                            actionable =
                                    "Try to add 20-30 minutes of moderate exercise to your daily routine.",
                            confidence = 0.8f
                    )
            )
        }

        return insights
    }

    // Trend Generation Methods

    private suspend fun getHeartRateTrends(
            timeRange: TimeRangeFilter,
            period: String
    ): List<TrendData> {
        // Implementation for heart rate trends
        return emptyList() // Placeholder
    }

    private suspend fun getStepsTrends(
            timeRange: TimeRangeFilter,
            period: String
    ): List<TrendData> {
        // Implementation for steps trends
        return emptyList() // Placeholder
    }

    private suspend fun getSleepTrends(
            timeRange: TimeRangeFilter,
            period: String
    ): List<TrendData> {
        // Implementation for sleep trends
        return emptyList() // Placeholder
    }

    private suspend fun getCaloriesTrends(
            timeRange: TimeRangeFilter,
            period: String
    ): List<TrendData> {
        // Implementation for calories trends
        return emptyList() // Placeholder
    }
}
