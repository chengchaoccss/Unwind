package com.armilla.neckcare.ui.lobby

import com.armilla.neckcare.domain.model.Direction

data class PlanRow(val ordinal: String, val name: String, val amount: String, val unit: String)

data class TrendUi(
    val totalText: String,
    val changeText: String?,
    val changeIsPositive: Boolean,
    val values: List<Int>,
    val firstDateLabel: String,
    val lastDateLabel: String,
    val referenceLines: List<Int>,
)

data class LobbyUiState(
    val loading: Boolean = true,
    val greeting: String = "",
    val dateLine: String = "",
    val plan: List<PlanRow> = DEFAULT_PLAN,
    val nextReminder: String = "",
    val armillaryTitle: String = "还没有测量",
    val armillarySubtitle: String = "先做一次 30 秒测试",
    val angles: Map<Direction, Int> = emptyMap(),
    val trend: TrendUi? = null,
    val emptyTrendText: String = "测过两次之后，这里会出现趋势",
    val balanceHeadline: String? = null,
    val balanceDetail: String? = null,
) {
    companion object {
        val DEFAULT_PLAN =
            listOf(
                PlanRow("1", "活动度测试", "30", "秒"),
                PlanRow("2", "视线接光球", "90", "秒"),
                PlanRow("3", "肩部环绕", "90", "秒"),
            )
    }
}

sealed interface LobbyEvent {
    data object Refresh : LobbyEvent
}
