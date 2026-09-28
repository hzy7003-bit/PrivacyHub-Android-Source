package com.privacyhub.presentation

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.BuildConfig
import com.privacyhub.R
import com.privacyhub.data.settings.AppSettingsStore
import com.privacyhub.data.settings.ThemeModeOption
import com.privacyhub.presentation.guide.GuideContent
import com.privacyhub.presentation.guide.TrialGuideActivity
import com.privacyhub.presentation.guide.WelcomeDialogFragment
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** No vault fixtures: preserves the user's notice, theme and lock preferences. */
@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val settings by lazy { AppSettingsStore(context) }
    private var oldNotice: String? = null
    private var oldTheme = ThemeModeOption.FollowSystem
    private var oldLock = false
    private val output by lazy { File(context.getExternalFilesDir(null), "flower-review").apply { mkdirs() } }

    @Before fun setup() {
        oldNotice = settings.lastSeenTrialNoticeVersion()
        oldTheme = settings.themeMode()
        oldLock = settings.isSecurityLockEnabled()
        settings.setSecurityLockEnabled(false)
        settings.setLastSeenTrialNoticeVersion(GuideContent.noticeKey(BuildConfig.VERSION_NAME))
        inst.uiAutomation.executeShellCommand("am start -W -n com.privacyhub/.presentation.MainActivity").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        inst.waitForIdleSync()
        settings.setLastSeenTrialNoticeVersion(null)
    }

    @After fun restore() {
        settings.setLastSeenTrialNoticeVersion(oldNotice)
        settings.setThemeMode(oldTheme)
        settings.setSecurityLockEnabled(oldLock)
    }

    @Test fun welcomeRecreationKeepsOneDialogAndAcknowledgementSurvives() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { assertWelcome(it) }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertWelcome(activity)
                assertEquals(1, activity.supportFragmentManager.fragments.filterIsInstance<WelcomeDialogFragment>().size)
                val dialog = activity.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) as WelcomeDialogFragment
                button(dialog.requireDialog().window!!.decorView, "开始使用").performClick()
            }
            assertEquals(GuideContent.noticeKey(BuildConfig.VERSION_NAME), settings.lastSeenTrialNoticeVersion())
            scenario.recreate()
            scenario.onActivity { assertNull(it.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG)) }
        }
    }

    @Test fun cancellingWelcomeDoesNotMarkItRead() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity {
                (it.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) as WelcomeDialogFragment)
                    .requireDialog().cancel()
            }
            inst.waitForIdleSync()
            assertNull(settings.lastSeenTrialNoticeVersion())
        }
    }

    @Test fun readingGuideNavigatesAndRecordsRevision() {
        val monitor = inst.addMonitor(TrialGuideActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    val dialog = it.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) as WelcomeDialogFragment
                    button(dialog.requireDialog().window!!.decorView, "阅读使用指南").performClick()
                }
                val guide = inst.waitForMonitorWithTimeout(monitor, 5000)
                assertNotNull(guide)
                inst.runOnMainSync {
                    assertTrue(nodes(guide!!.window.decorView).filterIsInstance<TextView>().any { it.text == "使用指南" })
                    guide.finish()
                }
                assertEquals(GuideContent.noticeKey(BuildConfig.VERSION_NAME), settings.lastSeenTrialNoticeVersion())
            }
        } finally { inst.removeMonitor(monitor) }
    }

    @Test fun captureWelcomeAndGuideInBothThemes() {
        for (theme in listOf(ThemeModeOption.Light, ThemeModeOption.Dark)) {
            settings.setThemeMode(theme)
            settings.setLastSeenTrialNoticeVersion(null)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { assertWelcome(it) }
                screenshot("welcome-${theme.name}.png")
                scenario.onActivity {
                    val dialog = it.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) as WelcomeDialogFragment
                    val scroll = nodes(dialog.requireDialog().window!!.decorView).filterIsInstance<ScrollView>().single()
                    scroll.scrollTo(0, scroll.getChildAt(0).height)
                }
                inst.waitForIdleSync()
                screenshot("welcome-${theme.name}-actions.png")
                scenario.onActivity {
                    val dialog = it.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) as WelcomeDialogFragment
                    val root = dialog.requireDialog().window!!.decorView
                    for (label in listOf("开始使用", "阅读使用指南")) {
                        val action = button(root, label)
                        val visible = Rect()
                        assertTrue("$label must be reachable by scrolling", action.getGlobalVisibleRect(visible))
                        assertEquals("$label must not be clipped", action.height, visible.height())
                    }
                    button(root, "开始使用").performClick()
                }
            }
            ActivityScenario.launch<TrialGuideActivity>(Intent(context, TrialGuideActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    assertTrue(nodes(it.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("不删除") })
                }
                screenshot("guide-${theme.name}.png")
            }
        }
    }

    @Test fun launcherIsAdaptiveCenteredAndMonochromeHasFlowerOpening() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        icon!!.setBounds(0, 0, 216, 216)
        icon.draw(Canvas(bitmap))
        var left = 216; var right = 0; var top = 216; var bottom = 0
        for (y in 0 until 216) for (x in 0 until 216) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 240 && Color.red(pixel) < 180 && Color.green(pixel) < 225) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Flower must keep mask clearance", left > 10 && right < 206 && top > 10 && bottom < 206)
        assertEquals(108f, (left + right) / 2f, 4f)
        assertEquals(108f, (top + bottom) / 2f, 4f)
        File(output, "launcher-color.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.eraseColor(Color.TRANSPARENT)
        val mono = ContextCompat.getDrawable(context, R.drawable.ph_flower_monochrome)!!
        mono.setBounds(0, 0, 216, 216); mono.draw(Canvas(bitmap))
        assertEquals(0, Color.alpha(bitmap.getPixel(108, 108)))
        assertTrue(Color.alpha(bitmap.getPixel(108, 60)) > 0)
        File(output, "launcher-monochrome.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun assertWelcome(activity: MainActivity) {
        val fragment = activity.supportFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) as? WelcomeDialogFragment
        assertNotNull(fragment)
        val dialog = fragment!!.requireDialog()
        assertFalse("Must not use platform AlertDialog chrome", dialog is android.app.AlertDialog)
        val texts = nodes(dialog.window!!.decorView).filterIsInstance<TextView>()
        assertTrue(texts.any { it.text == "欢迎来到隐私中转站" })
        assertTrue(button(dialog.window!!.decorView, "开始使用").height >= (48 * activity.resources.displayMetrics.density).toInt())
    }

    private fun screenshot(name: String) {
        inst.waitForIdleSync()
        Thread.sleep(1200)
        val bitmap = inst.uiAutomation.takeScreenshot()
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun button(root: View, text: String) = nodes(root).filterIsInstance<Button>().first { it.text == text }
    private fun nodes(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { nodes(root.getChildAt(it)) } else emptyList()
}
