package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FinanceViewModel
import com.example.ui.MonthlyBarData
import com.example.ui.components.DateRangePickerDialog
import com.example.ui.components.FintechCard
import com.example.ui.components.SmoothLineChart
import com.example.ui.components.TimeFilterRow
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.EmeraldGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun BankTrendsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val trendsBank by viewModel.trendsBank.collectAsState()
    val trendsMetric by viewModel.trendsMetric.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val showDateRangeDialog by viewModel.showDateRangeDialog.collectAsState()
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()

    if (showDateRangeDialog) {
        DateRangePickerDialog(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            onDismiss = { viewModel.setShowDateRangeDialog(false) },
            onConfirm = { start, end -> viewModel.setCustomDateRange(start, end) }
        )
    }
    val monthlyData by viewModel.monthlyTrendsData.collectAsState()

    var showBankMenu by remember { mutableStateOf(false) }
    var individualBank by remember { mutableStateOf("SBI") }
    var showIndBankMenu by remember { mutableStateOf(false) }

    val bankOptions = listOf("All Banks", "SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "IDFC", "OTHERS")

    val individualData = remember(individualBank, allTransactions, timeFilter, customStartDate, customEndDate) {
        val monthsCount = when (timeFilter) {
            "1M" -> 1
            "3M" -> 3
            "6M" -> 6
            "1Y" -> 12
            "Custom" -> {
                val cStart = customStartDate
                val cEnd = customEndDate
                if (cStart != null && cEnd != null) {
                    val startCal = Calendar.getInstance().apply { timeInMillis = cStart }
                    val endCal = Calendar.getInstance().apply { timeInMillis = cEnd }
                    val diff = (endCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12 +
                            (endCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)) + 1
                    diff.coerceIn(1, 24)
                } else 6
            }
            else -> 6
        }

        val cal = Calendar.getInstance()
        val sdfShort = SimpleDateFormat("MMM", Locale.getDefault())

        val months = (monthsCount - 1 downTo 0).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), sdfShort.format(c.time))
        }

        months.map { (yr, mo, label) ->
            var credit = 0.0
            var debit = 0.0

            for (tx in allTransactions) {
                if (viewModel.isTimestampInFilter(tx.timestamp, timeFilter, customStartDate, customEndDate)) {
                    cal.timeInMillis = tx.timestamp
                    val matchBank = tx.bankCode.equals(individualBank, true) || tx.bankName.contains(individualBank, true)
                    if (matchBank && cal.get(Calendar.YEAR) == yr && cal.get(Calendar.MONTH) == mo) {
                        if (tx.type == "CREDIT") credit += tx.amount else debit += tx.amount
                    }
                }
            }

            MonthlyBarData(label, credit, debit)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Bank-wise Trends",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bank Selector Dropdown
        item {
            Column {
                Text(
                    text = "Select Bank",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable { showBankMenu = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = trendsBank,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showBankMenu,
                        onDismissRequest = { showBankMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        bankOptions.forEach { b ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = b,
                                        color = if (b == trendsBank) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setTrendsBank(b)
                                    showBankMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Metric Toggle: Credit | Debit | Both
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val metrics = listOf("CREDIT" to "Credit", "DEBIT" to "Debit", "BOTH" to "Both")
                metrics.forEach { (key, label) ->
                    val isSelected = trendsMetric == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentPurple else Color.Transparent)
                            .clickable { viewModel.setTrendsMetric(key) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Time Filters: 1M, 3M, 6M, 1Y, Custom
        item {
            TimeFilterRow(
                selectedFilter = timeFilter,
                onSelect = { viewModel.setTimeFilter(it) },
                onCustomClick = { viewModel.setShowDateRangeDialog(true) }
            )
        }

        // Primary Line Chart
        item {
            FintechCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Flow Trend ($trendsBank)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                SmoothLineChart(
                    data = monthlyData,
                    showCredit = trendsMetric == "CREDIT" || trendsMetric == "BOTH",
                    showDebit = trendsMetric == "DEBIT" || trendsMetric == "BOTH"
                )
            }
        }

        // Individual Bank Trend Section
        item {
            FintechCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Individual Bank Trend",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showIndBankMenu = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = individualBank,
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showIndBankMenu,
                            onDismissRequest = { showIndBankMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            listOf("SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "IDFC").forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        individualBank = b
                                        showIndBankMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SmoothLineChart(
                    data = individualData,
                    showCredit = true,
                    showDebit = true
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
