package com.examguard.proctoring

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.examguard.proctoring.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI test exercising the Setup → Dashboard → Report flow on a real
 * device/emulator. CAMERA is pre-granted so the dashboard path is taken.
 */
@RunWith(AndroidJUnit4::class)
class ProctoringUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun setupScreen_isShownOnLaunch() {
        composeRule.onNodeWithTag("student_name_field").assertIsDisplayed()
        composeRule.onNodeWithTag("start_button").assertIsDisplayed()
    }

    @Test
    fun startingSession_navigatesToDashboard() {
        composeRule.onNodeWithTag("student_name_field").performTextInput("Test Candidate")
        composeRule.onNodeWithTag("start_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("score_text").assertIsDisplayed()
    }

    @Test
    fun endingSession_navigatesToReport() {
        composeRule.onNodeWithTag("start_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("end_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("report_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("new_session_button").assertIsDisplayed()
    }
}
