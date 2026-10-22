package com.incidentreporter.field.domain.usecase

import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.MAX_DESCRIPTION_LENGTH
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.MAX_TITLE_LENGTH

enum class FormField { TITLE, DESCRIPTION, CATEGORY, LOCATION, PHOTOS }

enum class ValidationError {
    TITLE_REQUIRED,
    TITLE_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    CATEGORY_REQUIRED,
    LOCATION_REQUIRED,
    TOO_MANY_PHOTOS,
}

fun validateIncident(input: IncidentInput): Map<FormField, ValidationError> = buildMap {
    val title = input.title.trim()
    when {
        title.isEmpty() -> put(FormField.TITLE, ValidationError.TITLE_REQUIRED)
        title.length > MAX_TITLE_LENGTH -> put(FormField.TITLE, ValidationError.TITLE_TOO_LONG)
    }
    if (input.description.length > MAX_DESCRIPTION_LENGTH) put(FormField.DESCRIPTION, ValidationError.DESCRIPTION_TOO_LONG)
    if (input.category == null) put(FormField.CATEGORY, ValidationError.CATEGORY_REQUIRED)
    if (!input.location.isUsable()) put(FormField.LOCATION, ValidationError.LOCATION_REQUIRED)
    if (input.photoCount > MAX_PHOTOS) put(FormField.PHOTOS, ValidationError.TOO_MANY_PHOTOS)
}

private fun IncidentLocation?.isUsable(): Boolean =
    this != null && ((latitude != null && longitude != null) || !label.isNullOrBlank())
