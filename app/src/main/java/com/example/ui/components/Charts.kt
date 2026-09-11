package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BankOverviewItem
import com.example.ui.CategorySpendItem
import com.example.ui.MonthlyBarData
import com.example.ui.WeeklyBarData
import com.example.ui.theme.DebitRed
import com.example.ui.theme.EmeraldGreen
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

data class LineTooltipData(
    val index: Int,
    val monthName: String,
    val amount: Double,
    val isCredit: Boolean,
    val x: Float,
    val y: Float
)

data class BarTooltipData(
    val label: String,
    val credit: Double?,
    val debit: Double?,
    val total: Double?,
    val x: Float
)

@Composable
fun MonthlyDualBarChart(
    data: List<MonthlyBarData>,
    modifier: Modifier = Modifier
) {
    val maxVal = (data.maxOfOrNull { maxOf(it.credit, it.debit) } ?: 100000.0).coerceAtLeast(10000.0)
    var tooltip by remember { mutableStateOf<BarTooltipData?>(null) }
    val inrFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipBorder = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(EmeraldGreen, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Credit", color = textMutedColor, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(8.dp).background(DebitRed, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Debit", color = textMutedColor, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(data) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width
                            if (data.isEmpty() || w <= 0) return@detectTapGestures
                            val slotWidth = w / data.size
                            val index = (offset.x / slotWidth).toInt().coerceIn(0, data.size - 1)
                            val item = data[index]
                            tooltip = BarTooltipData(
                                label = item.monthName,
                                credit = item.credit,
                                debit = item.debit,
                                total = null,
                                x = index * slotWidth + slotWidth / 2f
                            )
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 30.dp.toPx()

                val steps = 4
                for (i in 0..steps) {
                    val y = (h / steps) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (data.isEmpty()) return@Canvas

                val slotWidth = w / data.size
                val barWidth = 8.dp.toPx()
                val gap = 3.dp.toPx()

                data.forEachIndexed { index, item ->
                    val centerX = slotWidth * index + slotWidth / 2f
                    val creditHeight = ((item.credit / maxVal) * h).toFloat().coerceAtLeast(4.dp.toPx())
                    val creditLeft = centerX - barWidth - gap / 2f
                    val creditTop = h - creditHeight
                    drawRoundRect(
                        color = EmeraldGreen,
                        topLeft = Offset(creditLeft, creditTop),
                        size = Size(barWidth, creditHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    val debitHeight = ((item.debit / maxVal) * h).toFloat().coerceAtLeast(4.dp.toPx())
                    val debitLeft = centerX + gap / 2f
                    val debitTop = h - debitHeight
                    drawRoundRect(
                        color = DebitRed,
                        topLeft = Offset(debitLeft, debitTop),
                        size = Size(barWidth, debitHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            tooltip?.let { tt ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (with(LocalDensity.current) { tt.x.toDp() } - 60.dp).coerceAtLeast(0.dp),
                            y = 10.dp
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(tooltipBg)
                        .border(1.dp, tooltipBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { tooltip = null }
                ) {
                    Column {
                        Text(tt.label, color = textMutedColor, fontSize = 10.sp)
                        tt.credit?.let { Text("Cr: ${inrFormat.format(it)}", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        tt.debit?.let { Text("Dr: ${inrFormat.format(it)}", color = DebitRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                data.forEach { item ->
                    Text(
                        text = item.monthName,
                        color = textMutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyDualBarChart(
    data: List<WeeklyBarData>,
    modifier: Modifier = Modifier
) {
    val maxVal = (data.maxOfOrNull { maxOf(it.credit, it.debit) } ?: 60000.0).coerceAtLeast(10000.0)
    var tooltip by remember { mutableStateOf<BarTooltipData?>(null) }
    val inrFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipBorder = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(EmeraldGreen, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Credit", color = textMutedColor, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(8.dp).background(DebitRed, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Debit", color = textMutedColor, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(data) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width
                            if (data.isEmpty() || w <= 0) return@detectTapGestures
                            val slotWidth = w / data.size
                            val index = (offset.x / slotWidth).toInt().coerceIn(0, data.size - 1)
                            val item = data[index]
                            tooltip = BarTooltipData(
                                label = item.weekLabel,
                                credit = item.credit,
                                debit = item.debit,
                                total = null,
                                x = index * slotWidth + slotWidth / 2f
                            )
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 30.dp.toPx()

                val steps = 4
                for (i in 0..steps) {
                    val y = (h / steps) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (data.isEmpty()) return@Canvas

                val slotWidth = w / data.size
                val barWidth = 9.dp.toPx()
                val gap = 4.dp.toPx()

                data.forEachIndexed { index, item ->
                    val centerX = slotWidth * index + slotWidth / 2f
                    val creditHeight = ((item.credit / maxVal) * h).toFloat().coerceAtLeast(4.dp.toPx())
                    val creditLeft = centerX - barWidth - gap / 2f
                    val creditTop = h - creditHeight
                    drawRoundRect(
                        color = EmeraldGreen,
                        topLeft = Offset(creditLeft, creditTop),
                        size = Size(barWidth, creditHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    val debitHeight = ((item.debit / maxVal) * h).toFloat().coerceAtLeast(4.dp.toPx())
                    val debitLeft = centerX + gap / 2f
                    val debitTop = h - debitHeight
                    drawRoundRect(
                        color = DebitRed,
                        topLeft = Offset(debitLeft, debitTop),
                        size = Size(barWidth, debitHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            tooltip?.let { tt ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (with(LocalDensity.current) { tt.x.toDp() } - 60.dp).coerceAtLeast(0.dp),
                            y = 10.dp
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(tooltipBg)
                        .border(1.dp, tooltipBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { tooltip = null }
                ) {
                    Column {
                        Text(tt.label, color = textMutedColor, fontSize = 10.sp)
                        tt.credit?.let { Text("Cr: ${inrFormat.format(it)}", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        tt.debit?.let { Text("Dr: ${inrFormat.format(it)}", color = DebitRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                data.forEach { item ->
                    Text(
                        text = item.weekLabel,
                        color = textMutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun BankComparisonBarChart(
    items: List<BankOverviewItem>,
    modifier: Modifier = Modifier
) {
    val maxVal = (items.maxOfOrNull { it.totalAmount } ?: 100000.0).coerceAtLeast(10000.0)
    var tooltip by remember { mutableStateOf<BarTooltipData?>(null) }
    val inrFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipBorder = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(items) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width
                            if (items.isEmpty() || w <= 0) return@detectTapGestures
                            val slotWidth = w / items.size
                            val index = (offset.x / slotWidth).toInt().coerceIn(0, items.size - 1)
                            val item = items[index]
                            tooltip = BarTooltipData(
                                label = item.bankName,
                                credit = null,
                                debit = null,
                                total = item.totalAmount,
                                x = index * slotWidth + slotWidth / 2f
                            )
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 30.dp.toPx()

                val steps = 4
                for (i in 0..steps) {
                    val y = (h / steps) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (items.isEmpty()) return@Canvas

                val slotWidth = w / items.size
                val barWidth = 14.dp.toPx()

                items.forEachIndexed { index, item ->
                    val centerX = slotWidth * index + slotWidth / 2f
                    val barHeight = ((item.totalAmount / maxVal) * h).toFloat().coerceAtLeast(6.dp.toPx())
                    val left = centerX - barWidth / 2f
                    val top = h - barHeight

                    drawRoundRect(
                        color = EmeraldGreen,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            tooltip?.let { tt ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (with(LocalDensity.current) { tt.x.toDp() } - 60.dp).coerceAtLeast(0.dp),
                            y = 10.dp
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(tooltipBg)
                        .border(1.dp, tooltipBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { tooltip = null }
                ) {
                    Column {
                        Text(tt.label, color = textMutedColor, fontSize = 10.sp)
                        tt.total?.let { Text("Amount: ${inrFormat.format(it)}", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                items.forEach { item ->
                    Text(
                        text = item.bankCode,
                        color = textMutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SmoothLineChart(
    data: List<MonthlyBarData>,
    showCredit: Boolean = true,
    showDebit: Boolean = true,
    modifier: Modifier = Modifier
) {
    val maxVal = (data.maxOfOrNull { maxOf(it.credit, it.debit) } ?: 180000.0).coerceAtLeast(10000.0)
    var tooltipData by remember { mutableStateOf<LineTooltipData?>(null) }
    val inrFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCredit) {
                Box(modifier = Modifier.size(8.dp).background(EmeraldGreen, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Credit", color = textMutedColor, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(16.dp))
            }
            if (showDebit) {
                Box(modifier = Modifier.size(8.dp).background(DebitRed, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Debit", color = textMutedColor, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(data, showCredit, showDebit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position ?: continue
                            val w = size.width
                            val h = size.height - 25.dp.toPx()
                            if (data.size >= 2 && w > 0) {
                                val slotWidth = w / (data.size - 1)
                                val index = (position.x / slotWidth).roundToInt().coerceIn(0, data.size - 1)
                                val item = data[index]
                                val creditY = h - ((item.credit / maxVal) * h).toFloat()
                                val debitY = h - ((item.debit / maxVal) * h).toFloat()

                                val isCred = if (showCredit && showDebit) {
                                    kotlin.math.abs(position.y - creditY) <= kotlin.math.abs(position.y - debitY)
                                } else showCredit

                                val chosenY = if (isCred) creditY else debitY
                                val chosenAmt = if (isCred) item.credit else item.debit

                                if (event.changes.first().pressed) {
                                    tooltipData = LineTooltipData(
                                        index = index,
                                        monthName = item.monthName,
                                        amount = chosenAmt,
                                        isCredit = isCred,
                                        x = index.toFloat() * slotWidth,
                                        y = chosenY.coerceIn(0f, h)
                                    )
                                } else {
                                    tooltipData = null
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 25.dp.toPx()

                val steps = 4
                for (i in 0..steps) {
                    val y = (h / steps) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (data.size < 2) return@Canvas

                val slotWidth = w / (data.size - 1)

                if (showCredit) {
                    val creditPoints = data.mapIndexed { index, item ->
                        val x = index * slotWidth
                        val y = h - ((item.credit / maxVal) * h).toFloat()
                        Offset(x, y.coerceIn(0f, h))
                    }

                    val creditPath = Path().apply {
                        moveTo(creditPoints.first().x, creditPoints.first().y)
                        for (i in 1 until creditPoints.size) {
                            val prev = creditPoints[i - 1]
                            val curr = creditPoints[i]
                            val cx = (prev.x + curr.x) / 2f
                            cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }
                    }

                    drawPath(
                        path = creditPath,
                        color = EmeraldGreen,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    creditPoints.forEach { pt ->
                        drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = pt)
                        drawCircle(color = EmeraldGreen, radius = 3.5.dp.toPx(), center = pt)
                    }
                }

                if (showDebit) {
                    val debitPoints = data.mapIndexed { index, item ->
                        val x = index * slotWidth
                        val y = h - ((item.debit / maxVal) * h).toFloat()
                        Offset(x, y.coerceIn(0f, h))
                    }

                    val debitPath = Path().apply {
                        moveTo(debitPoints.first().x, debitPoints.first().y)
                        for (i in 1 until debitPoints.size) {
                            val prev = debitPoints[i - 1]
                            val curr = debitPoints[i]
                            val cx = (prev.x + curr.x) / 2f
                            cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }
                    }

                    drawPath(
                        path = debitPath,
                        color = DebitRed,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    debitPoints.forEach { pt ->
                        drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = pt)
                        drawCircle(color = DebitRed, radius = 3.5.dp.toPx(), center = pt)
                    }
                }

                tooltipData?.let { tt ->
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.4f),
                        start = Offset(tt.x, 0f),
                        end = Offset(tt.x, h),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = onSurfaceColor,
                        radius = 6.dp.toPx(),
                        center = Offset(tt.x, tt.y)
                    )
                    drawCircle(
                        color = if (tt.isCredit) EmeraldGreen else DebitRed,
                        radius = 4.dp.toPx(),
                        center = Offset(tt.x, tt.y)
                    )
                }
            }

            tooltipData?.let { tt ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (with(LocalDensity.current) { tt.x.toDp() } - 55.dp).coerceAtLeast(0.dp),
                            y = (with(LocalDensity.current) { tt.y.toDp() } - 60.dp).coerceAtLeast(0.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceColor)
                        .border(1.dp, if (tt.isCredit) EmeraldGreen else DebitRed, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(tt.monthName, color = textMutedColor, fontSize = 10.sp)
                        Text(
                            text = "${if (tt.isCredit) "Credit" else "Debit"}: ${inrFormat.format(tt.amount)}",
                            color = if (tt.isCredit) EmeraldGreen else DebitRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                data.forEach { item ->
                    Text(
                        text = item.monthName,
                        color = textMutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    items: List<CategorySpendItem>,
    totalSpentText: String = "₹0",
    modifier: Modifier = Modifier
) {
    val unallocatedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val strokeWidth = 24.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val radius = diameter / 2f
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            var startAngle = -90f

            if (items.isEmpty()) {
                drawCircle(
                    color = unallocatedColor,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                items.forEach { item ->
                    val sweep = (item.percentage / 100f) * 360f
                    val color = try {
                        Color(android.graphics.Color.parseColor(item.colorHex))
                    } catch (_: Exception) {
                        EmeraldGreen
                    }

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalSpentText,
                color = onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Total Spent",
                color = onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
