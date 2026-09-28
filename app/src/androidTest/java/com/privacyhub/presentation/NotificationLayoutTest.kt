package com.privacyhub.presentation

import android.app.Notification
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privacyhub.R
import com.privacyhub.presentation.notification.SaveNotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION") // Inspect the RemoteViews payload actually delivered to SystemUI.
class NotificationLayoutTest {
    private val inst = InstrumentationRegistry.getInstrumentation()

    @Test fun collapsedAndExpandedBothExposeTwoFullSizeActions() {
        inst.runOnMainSync {
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                for (scale in listOf(1f, 1.5f)) {
                    val base = inst.targetContext
                    val config = Configuration(base.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                        fontScale = scale
                    }
                    val context = base.createConfigurationContext(config)
                    val notification = SaveNotificationManager(context).buildPersistentSaveNotification()
                    assertEquals(Notification.DecoratedCustomViewStyle::class.java.name,
                        notification.extras.getString(Notification.EXTRA_TEMPLATE))
                    assertNotNull("Collapsed notification must contain direct actions", notification.contentView)
                    assertNotNull(notification.bigContentView)
                    val builder = Notification.Builder.recoverBuilder(context, notification)
                    for (remote in listOf(notification.contentView, notification.bigContentView,
                            builder.createContentView(), builder.createBigContentView())) {
                        val root = remote.apply(context, FrameLayout(context))
                        val density = context.resources.displayMetrics.density
                        root.measure(View.MeasureSpec.makeMeasureSpec((280 * density).toInt(), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec((256 * density).toInt(), View.MeasureSpec.AT_MOST))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                        val buttons = nodes(root).filterIsInstance<Button>()
                        assertEquals(listOf("保存到安全箱", "保存并跳转"), buttons.map { it.text.toString() })
                        buttons.forEach {
                            assertTrue(it.isClickable)
                            assertTrue(it.height >= (48 * density).toInt())
                            assertTrue(
                                "Button '${it.text}' must fit at scale=$scale width=${it.width} " +
                                    "textSize=${it.textSize} lineWidth=${it.layout.getLineWidth(0)}",
                                it.layout.getEllipsisCount(0) == 0
                            )
                            // Smaller visible fill must not shrink the existing touch target.
                            val background = it.background.constantState!!.newDrawable(context.resources).mutate()
                            val fill = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
                            try {
                                background.setBounds(0, 0, fill.width, fill.height)
                                background.draw(Canvas(fill))
                                val centerX = fill.width / 2
                                assertEquals("Keep clear space above the compact fill", 0,
                                    Color.alpha(fill.getPixel(centerX, (2 * density).toInt())))
                                assertTrue(Color.alpha(fill.getPixel(centerX, fill.height / 2)) > 240)
                                assertEquals("Keep clear space below the compact fill", 0,
                                    Color.alpha(fill.getPixel(centerX, fill.height - (2 * density).toInt() - 1)))
                            } finally {
                                fill.recycle()
                            }
                        }
                    }
                    assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
                    assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
                }
            }
        }
    }

    @Test fun notificationUsesDedicatedFlowerWithTransparentCenter() {
        val context = inst.targetContext
        val notification = SaveNotificationManager(context).buildPersistentSaveNotification()
        assertEquals(R.drawable.ic_notification_flower, notification.smallIcon.resId)
        val flower = notification.smallIcon.loadDrawable(context)!!
        val bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        flower.setBounds(0, 0, 72, 72)
        flower.draw(Canvas(bitmap))
        assertEquals(0, Color.alpha(bitmap.getPixel(36, 36)))
        assertTrue(Color.alpha(bitmap.getPixel(36, 12)) > 240)
        bitmap.recycle()
    }

    private fun nodes(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { nodes(root.getChildAt(it)) } else emptyList()
}
