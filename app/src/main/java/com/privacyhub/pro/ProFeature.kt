package com.privacyhub.pro

enum class ProFeature(
    val id: String,
    val label: String,
    val description: String
) {
    AdvancedSearch(
        id = "advanced_search",
        label = "高级搜索",
        description = "按分类、收藏、来源和时间组合筛选。"
    ),
    AdvancedTags(
        id = "advanced_tags",
        label = "高级标签",
        description = "更多标签规则和整理能力。"
    ),
    BatchImport(
        id = "batch_import",
        label = "批量导入",
        description = "从本地文件批量导入内容。"
    ),
    BatchExport(
        id = "batch_export",
        label = "批量导出",
        description = "把安全箱内容导出到本地文件。"
    ),
    AdvancedRouting(
        id = "advanced_routing",
        label = "高级路由规则",
        description = "为不同平台配置更细的打开规则。"
    ),
    Autofill(
        id = "autofill",
        label = "自动填充",
        description = "通过系统自动填充能力减少复制粘贴。"
    ),
    AccessibilityAutomation(
        id = "accessibility_automation",
        label = "自动操作",
        description = "需要用户主动开启无障碍后执行自动跳转和填入。"
    );

    companion object {
        fun fromId(id: String): ProFeature? {
            return entries.firstOrNull { it.id == id.trim() }
        }
    }
}
