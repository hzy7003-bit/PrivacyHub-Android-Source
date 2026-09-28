package com.privacyhub.automation

import com.privacyhub.domain.model.LinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetdiskHostPolicyTest {
    @Test
    fun acceptsOnlyExactSupportedTargetHosts() {
        assertEquals(
            "pan.baidu.com",
            NetdiskHostPolicy.targetHost("https://PAN.BAIDU.COM./s/example?pwd=aB9x", LinkTarget.BAIDU_NETDISK)
        )
        assertEquals(
            "drive.quark.cn",
            NetdiskHostPolicy.targetHost("https://drive.quark.cn/s/example", LinkTarget.QUARK_NETDISK)
        )
        assertNull(NetdiskHostPolicy.targetHost("https://evil.pan.baidu.com.attacker.test/s/example", LinkTarget.BAIDU_NETDISK))
        assertNull(NetdiskHostPolicy.targetHost("https://sub.pan.baidu.com/s/example", LinkTarget.BAIDU_NETDISK))
    }

    @Test
    fun normalizesIdnAndRejectsNonHostValues() {
        assertEquals("xn--bcher-kva.example", NetdiskHostPolicy.normalizeHost("BÜCHER.example."))
        assertNull(NetdiskHostPolicy.normalizeHost("https://pan.baidu.com/path"))
        assertNull(NetdiskHostPolicy.normalizeHost("pan..baidu.com"))
    }

    @Test
    fun rejectsUnsupportedTargetsAndSchemes() {
        assertNull(NetdiskHostPolicy.targetHost("https://pan.baidu.com/s/example", LinkTarget.WEB))
        assertNull(NetdiskHostPolicy.targetHost("javascript://pan.baidu.com", LinkTarget.BAIDU_NETDISK))
        assertNull(NetdiskHostPolicy.targetHost("https://pan.baidu.com.attacker.test", LinkTarget.BAIDU_NETDISK))
    }
}
