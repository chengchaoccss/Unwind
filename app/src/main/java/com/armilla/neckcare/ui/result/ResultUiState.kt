package com.armilla.neckcare.ui.result

import com.armilla.neckcare.domain.model.Side

data class SideReading(val label: String, val valueDeg: Int?, val lastDeg: Int?, val side: Side)

/** One row of the diverging bar chart: an axis with its two directions growing from the centre. */
data class AxisRow(val title: String, val differenceTag: String?, val left: SideReading, val right: SideReading)

data class ResultUiState(
    val dateLine: String = "",
    val totalText: String = "",
    val totalCaption: String = "",
    val totalCaptionIsPositive: Boolean = true,
    val rows: List<AxisRow> = emptyList(),
    val angles: Map<com.armilla.neckcare.domain.model.Direction, Int> = emptyMap(),
    val nextWeekText: String = "",
    val reminderTimes: List<String> = emptyList(),
)
