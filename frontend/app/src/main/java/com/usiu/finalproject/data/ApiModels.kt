package com.usiu.finalproject.data


data class ClassificationResponse(
    val success: Boolean,
    val prediction: Prediction?,
    val feedback: Feedback?,
    val error: String?
)

data class Prediction(
    val `class`: String,
    val confidence: Double,
    val timestamp: String
)

data class Feedback(
    val alert_level: String,
    val color: String,
    val vibration_pattern: List<Long>,
    val message: String,
    val priority: Int
)

data class HealthResponse(
    val status: String,
    val timestamp: String,
    val model_loaded: Boolean
)

data class SupportedClassesResponse(
    val success: Boolean,
    val classes: List<String>,
    val total_classes: Int
)

enum class AlertLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    companion object {
        fun fromString(level: String): AlertLevel {
            return when(level.lowercase()) {
                "low" -> LOW
                "medium" -> MEDIUM
                "high" -> HIGH
                "critical" -> CRITICAL
                else -> LOW
            }
        }
    }
}