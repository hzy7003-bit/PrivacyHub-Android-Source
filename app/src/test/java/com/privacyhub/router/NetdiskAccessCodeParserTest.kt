package com.privacyhub.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetdiskAccessCodeParserTest {
    @Test
    fun extractsPwdParameterFromNetdiskLink() {
        assertEquals(
            "aB9x",
            NetdiskAccessCodeParser.extract("https://pan.baidu.com/s/1example?pwd=aB9x")
        )
    }

    @Test
    fun decodesEscapedUrlParameterValues() {
        assertEquals(
            "aB9x",
            NetdiskAccessCodeParser.extract("https://pan.baidu.com/s/1example?pwd=%61%42%39%78")
        )
    }

    @Test
    fun extractsExplicitChineseAccessCode() {
        assertEquals(
            "7kP2",
            NetdiskAccessCodeParser.extract("夸克网盘链接：https://pan.quark.cn/s/example 提取码：7kP2")
        )
    }

    @Test
    fun rejectsOrdinaryNumbersWithoutAnAccessCodeLabel() {
        assertNull(NetdiskAccessCodeParser.extract("订单号 202607130001，打开 https://pan.baidu.com/s/example"))
    }

    @Test
    fun rejectsOverlongValues() {
        assertNull(NetdiskAccessCodeParser.extract("访问码：abcde12345"))
    }
}
