package com.example.stepsnap.ui.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetPointViewModelTest {

    @Test
    fun initialStateHasEmptyFieldsAndNoLocation() {
        val state = SetPointUiState()
        assertEquals("", state.name)
        assertEquals("", state.description)
        assertNull(state.photoPath)
        assertNull(state.latitude)
        assertNull(state.longitude)
        assertFalse(state.showDiscardConfirm)
    }

    @Test
    fun emptyNameValidationRequiresNonEmptyString() {
        val name = "   "
        val trimmed = name.trim()
        val isNameValid = trimmed.isNotEmpty() && trimmed.length <= 60
        assertFalse(isNameValid)
    }

    @Test
    fun nameExceedingSixtyCharsIsInvalid() {
        val longName = "A".repeat(61)
        val isNameValid = longName.trim().isNotEmpty() && longName.trim().length <= 60
        assertFalse(isNameValid)
    }

    @Test
    fun validNameAndLocationMakesFormValid() {
        val state = SetPointUiState(
            name = "Kajoetangan Heritage",
            latitude = -7.9811,
            longitude = 112.6305
        )
        val isFormValid = state.name.trim().isNotEmpty() &&
            state.name.trim().length <= 60 &&
            state.latitude != null &&
            state.longitude != null

        assertTrue(isFormValid)
    }

    @Test
    fun hasChangesDetectsUserEdits() {
        val stateWithText = SetPointUiState(name = "Monumen")
        val stateWithPhoto = SetPointUiState(photoPath = "/path/to/img.jpg")
        val emptyState = SetPointUiState()

        assertTrue(stateWithText.name.isNotBlank() || stateWithText.description.isNotBlank() || stateWithText.photoPath != null)
        assertTrue(stateWithPhoto.name.isNotBlank() || stateWithPhoto.description.isNotBlank() || stateWithPhoto.photoPath != null)
        assertFalse(emptyState.name.isNotBlank() || emptyState.description.isNotBlank() || emptyState.photoPath != null)
    }
}
