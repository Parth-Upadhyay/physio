package com.example.ped

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class PhysioAppUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_starts_with_initial_state() {
        // Check if Reps: 0 is displayed
        composeTestRule.onNodeWithText("Reps: 0").assertIsDisplayed()
        
        // Check if initial feedback is displayed
        composeTestRule.onNodeWithText("Stand in view").assertIsDisplayed()
    }
}
