package com.narratome

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.narratome.presentation.root.AudiobookMainShell
import com.narratome.presentation.theme.AudiobookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerHomeNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawerHomeReturnsFromDownloadsShortcut() {
        // Exercise the real shell without requiring server setup or credentials.
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                AudiobookTheme { AudiobookMainShell() }
            }
        }

        compose.onNodeWithText("Home").assertIsDisplayed()
        repeat(2) {
            compose.onNodeWithContentDescription("Downloads").performClick()
            compose.onNodeWithText("Downloads").assertIsDisplayed()
            compose.onNodeWithContentDescription("Menu").performClick()
            compose.onNodeWithText("HOME").performClick()
            compose.onNodeWithText("Home").assertIsDisplayed()
        }

        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("HOME").performClick()
        compose.onNodeWithText("Home").assertIsDisplayed()
    }
}
