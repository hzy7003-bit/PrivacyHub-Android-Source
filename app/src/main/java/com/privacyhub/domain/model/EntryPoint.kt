package com.privacyhub.domain.model

enum class EntryPoint(val displayName: String) {
    SHARE("分享"),
    MANUAL("手动新建"),
    TILE("控制中心磁贴"),
    NOTIFICATION("通知栏"),
    AUTOFILL("系统自动填充"),
    AUTOMATION("网盘自动填码")
}
