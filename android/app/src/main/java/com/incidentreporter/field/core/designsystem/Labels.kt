package com.incidentreporter.field.core.designsystem

import androidx.annotation.StringRes
import com.incidentreporter.field.R
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status

@StringRes
fun Severity.label(): Int = when (this) {
    Severity.LOW -> R.string.severity_low
    Severity.MEDIUM -> R.string.severity_medium
    Severity.HIGH -> R.string.severity_high
    Severity.CRITICAL -> R.string.severity_critical
}

@StringRes
fun Status.label(): Int = when (this) {
    Status.OPEN -> R.string.status_open
    Status.IN_PROGRESS -> R.string.status_in_progress
    Status.RESOLVED -> R.string.status_resolved
}

@StringRes
fun Category.label(): Int = when (this) {
    Category.EQUIPMENT_FAILURE -> R.string.category_equipment_failure
    Category.ROAD_HAZARD -> R.string.category_road_hazard
    Category.ELECTRICAL -> R.string.category_electrical
    Category.PLUMBING -> R.string.category_plumbing
    Category.STRUCTURAL -> R.string.category_structural
    Category.OTHER -> R.string.category_other
}
