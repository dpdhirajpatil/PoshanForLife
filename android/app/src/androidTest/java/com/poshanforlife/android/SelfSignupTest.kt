package com.poshanforlife.android

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End users joining from the app, end to end against a real backend: email
 * sign-up, phone (OTP) sign-up, phone login, and existing-patient login.
 * The Android twin of iOS SelfSignupUITests.
 *
 * Each run creates LEAD accounts named "ANDROID-SELF-<timestamp>". The phone
 * flows use the backend's local/dev fixed code 000000, so run this against
 * local or the AWS dev environment, never prod:
 *
 *   ./gradlew :app:connectedAndroidTest -PtestBuildType=staging \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.poshanforlife.android.SelfSignupTest
 */
@RunWith(AndroidJUnit4::class)
class SelfSignupTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val stamp = (System.currentTimeMillis() / 1000).toString()

    @Before
    fun grantNotifications() {
        // Granted up front, the app never shows its notification rationale
        // dialog, which would otherwise cover the screen after sign-in.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @Test
    fun emailSignupLandsOnLeadHomeAndRequestsConsultation() {
        signOutIfSignedIn()
        click("Don't have an account? Sign up")

        field("Name").performTextInput("ANDROID-SELF-$stamp Email")
        field("Email").performTextInput("android-self-$stamp@example.com")
        field("Password").performTextInput("Signup${stamp}1")
        compose.onAllNodesWithText("Create account").filterToOne(clickable).scrollIfNeeded().performClick()

        assertOnLeadHome()

        click("Check in today")
        waitForText("Checked in today")

        compose.onAllNodesWithText("Request consultation").filterToOne(clickable).scrollIfNeeded().performClick()
        waitForText("Preferred contact time (optional)")
        compose.onAllNodesWithText("Request consultation").filterToOne(clickable).performClick()
        waitForText("Done")
        click("Done")
    }

    @Test
    fun phoneSignupThenPhoneLogin() {
        signOutIfSignedIn()
        val phone = "8" + stamp.takeLast(9)

        // Unknown number on login → offered sign-up in place.
        sendOtp(phone)
        click("No account found — sign up instead?")
        field("Your name *").performTextInput("ANDROID-SELF-$stamp Phone")
        typeCode()
        // With a valid name already entered, the sixth digit auto-submits.
        submitCodeUnlessAutoSubmitted("Create account")
        assertOnLeadHome()

        signOut()

        sendOtp(phone)
        typeCode()
        submitCodeUnlessAutoSubmitted("Verify")
        assertOnLeadHome()
    }

    @Test
    fun existingPatientSignsInWithEmail() {
        signOutIfSignedIn()
        field("Email").performTextInput("testpatient1@example.com")
        field("Password").performTextInput("Admin@123")
        compose.onAllNodesWithText("Sign in").filterToOne(clickable).performClick()
        // Patient-only tabs prove this landed in the patient app, not the lead one.
        waitForText("Programmes")
        waitForText("Reports")
    }

    // region helpers

    private val clickable = SemanticsMatcher("is clickable") {
        it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }

    private fun field(label: String) =
        compose.onNode(hasSetTextAction() and hasText(label), useUnmergedTree = false).also {
            waitUntil { compose.onAllNodes(hasSetTextAction() and hasText(label)).fetchSemanticsNodes().isNotEmpty() }
            it.scrollIfNeeded()
        }

    private fun click(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text).filterToOne(clickable).scrollIfNeeded().performClick()
    }

    private fun sendOtp(phone: String) {
        click("Phone")
        field("Phone number").performTextInput(phone)
        click("Send OTP")
    }

    private fun typeCode() {
        // Wait for the code step itself: right after "Send OTP" the phone form
        // is still on screen while the request is in flight, and typing then
        // lands in the phone field.
        waitForText("Enter the 6-digit code", substring = true)
        // The six boxes are one hidden text field; on sign-up the only other
        // field on this step is the name.
        val codeField = hasSetTextAction() and !hasText("Your name *") and !hasText("Phone number")
        waitUntil { compose.onAllNodes(codeField).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(codeField)[0].performTextInput("000000")
    }

    /** The code boxes submit on the sixth digit; tap the button only if they didn't. */
    private fun submitCodeUnlessAutoSubmitted(button: String) {
        if (!waitForText("Hi,", substring = true, timeoutMs = 10_000, required = false)) {
            runCatching { compose.onAllNodesWithText(button).filterToOne(clickable).performClick() }
        }
    }

    private fun assertOnLeadHome() {
        waitForText("Hi,", substring = true, timeoutMs = 30_000)
        listOf("Track", "Goals", "Profile").forEach { waitForText(it) }
        check(compose.onAllNodesWithText("Programmes").fetchSemanticsNodes().isEmpty()) {
            "a lead should not get the patient tabs"
        }
    }

    private fun signOutIfSignedIn() {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (exists("Sign in to continue")) return
            if (exists("Profile") || exists("More")) {
                signOut()
                return
            }
            Thread.sleep(300)
        }
    }

    private fun signOut() {
        click("Profile")
        click("Log out")
        waitForText("Sign in to continue", timeoutMs = 20_000)
    }

    private fun exists(text: String) =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMs: Long = 20_000,
        required: Boolean = true,
    ): Boolean = try {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(text, substring = substring, ignoreCase = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
        true
    } catch (e: Throwable) {
        if (required) throw AssertionError("'$text' never appeared. On screen: ${visibleTexts()}", e) else false
    }

    private fun visibleTexts(): List<String> =
        compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                    .map { it.text }
            }
            .filter { it.isNotBlank() }
            .distinct()

    private fun waitUntil(condition: () -> Boolean) = compose.waitUntil(20_000, condition)

    /** performScrollTo throws outside a scrolling container (the login screen doesn't scroll). */
    private fun SemanticsNodeInteraction.scrollIfNeeded(): SemanticsNodeInteraction =
        apply { runCatching { performScrollTo() } }

    // endregion
}
