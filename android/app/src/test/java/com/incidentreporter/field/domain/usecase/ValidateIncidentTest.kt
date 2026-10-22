package com.incidentreporter.field.domain.usecase

import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.testing.anIncident
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateIncidentTest {

    private val manual = IncidentLocation(latitude = null, longitude = null, label = "Bay 3", source = LocationSource.MANUAL)
    private val valid = IncidentInput(title = "Leak", category = Category.OTHER, location = manual)

    private fun err(input: IncidentInput, field: FormField) = validateIncident(input)[field]

    @Test
    fun validInput_hasNoErrors() {
        assertEquals(emptyMap<FormField, ValidationError>(), validateIncident(valid))
    }

    @Test
    fun title_r1() {
        mapOf(
            "" to ValidationError.TITLE_REQUIRED,
            "   " to ValidationError.TITLE_REQUIRED,
            "a".repeat(81) to ValidationError.TITLE_TOO_LONG,
            "a" to null,
            "a".repeat(80) to null,
            "  " + "a".repeat(80) + "  " to null, // length is counted after trimming
        ).forEach { (title, expected) ->
            assertEquals("title='$title'", expected, err(valid.copy(title = title), FormField.TITLE))
        }
    }

    @Test
    fun description_r2() {
        assertNull(err(valid.copy(description = "d".repeat(1000)), FormField.DESCRIPTION))
        assertEquals(ValidationError.DESCRIPTION_TOO_LONG, err(valid.copy(description = "d".repeat(1001)), FormField.DESCRIPTION))
    }

    @Test
    fun category_r3() {
        assertEquals(ValidationError.CATEGORY_REQUIRED, err(valid.copy(category = null), FormField.CATEGORY))
    }

    @Test
    fun severity_r4_defaultsToMedium() {
        assertEquals(Severity.MEDIUM, IncidentInput().severity)
    }

    @Test
    fun location_r5() {
        assertEquals(ValidationError.LOCATION_REQUIRED, err(valid.copy(location = null), FormField.LOCATION))
        assertEquals(ValidationError.LOCATION_REQUIRED, err(valid.copy(location = manual.copy(label = "  ")), FormField.LOCATION))
        val gps = IncidentLocation(latitude = -0.9471, longitude = 100.4172, label = null, source = LocationSource.GPS)
        assertNull(err(valid.copy(location = gps), FormField.LOCATION))
    }

    @Test
    fun photos_r6_neverRequired_cappedAtFive() {
        assertNull(err(valid.copy(photoCount = 0), FormField.PHOTOS))
        assertEquals(ValidationError.TOO_MANY_PHOTOS, err(valid.copy(photoCount = 6), FormField.PHOTOS))
    }

    @Test
    fun onlyOpenIsEditable_r7() {
        assertTrue(anIncident(status = Status.OPEN).isEditable)
        assertFalse(anIncident(status = Status.RESOLVED).isEditable)
    }
}
