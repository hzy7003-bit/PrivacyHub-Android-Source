package com.privacyhub.presentation.guide

import org.junit.Assert.*
import org.junit.Test

class GuideContentTest {
    @Test fun oldVersionAcknowledgementDoesNotHideRevisedGuide() {
        assertTrue(GuideContent.shouldShowWelcome("0.9.27-beta", "0.9.27-beta"))
        assertFalse(GuideContent.shouldShowWelcome("0.9.27-beta", GuideContent.noticeKey("0.9.27-beta")))
        assertTrue(GuideContent.shouldShowWelcome("0.9.28-beta", GuideContent.noticeKey("0.9.27-beta")))
        assertTrue(GuideContent.shouldShowWelcome("0.9.27-beta", null))
    }

    @Test fun tutorialMatchesSaveFirstNavigationAndDoesNotPromiseThirdPartyCompletion() {
        val text = GuideContent.sections.joinToString("\n") { it.title + it.body }
        listOf("中转", "安全箱", "设置", "保存并处理", "最近保存", "已发起打开", "不删除", "Pro", "无障碍", "本地诊断报告")
            .forEach { assertTrue("Missing guidance: $it", text.contains(it)) }
        assertFalse(text.contains("保存后可在首页"))
        assertFalse(text.contains("最近使用"))
        assertTrue(text.contains("输入法"))
        assertTrue(text.contains("不会点击确认"))
    }
}
