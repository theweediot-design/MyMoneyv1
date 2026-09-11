package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import com.example.ui.components.FintechCard
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.MonthSelectorPill
import com.example.ui.components.WeeklyDualBarChart
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.DebitRed
import com.example.ui.theme.EmeraldGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MonthlySummaryScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val currentMonth by viewModel.selectedMonth.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val periodSummary by viewModel.periodSummary.collectAsState()
    val weeklyData by viewModel.weeklySummaryData.collectAsState()

    var showMonthMenu by remember { mutableStateOf(false) }

    val inrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
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
                    text = "Monthly Summary",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Month Picker Dropdown
        item {
            Box {
                MonthSelectorPill(
                    currentLabel = currentMonth.label,
                    onClick = { showMonthMenu = true }
                )

                DropdownMenu(
                    expanded = showMonthMenu,
                    onDismissRequest = { showMonthMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    availableMonths.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    color = if (option == currentMonth) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                viewModel.setSelectedMonth(option)
                                showMonthMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 4 Summary Cards (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSummaryCard(
                        title = "Credit",
                        amount = inrFormat.format(periodSummary.totalCredit),
                        badgeText = periodSummary.creditChangePct,
                        isPositiveBadge = !periodSummary.creditChangePct.startsWith("-"),
                        icon = Icons.Default.ArrowDownward,
                        iconColor = EmeraldGreen,
                        iconBgColor = EmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricSummaryCard(
                        title = "Debit",
                        amount = inrFormat.format(periodSummary.totalDebit),
                        badgeText = periodSummary.debitChangePct,
                        isPositiveBadge = periodSummary.debitChangePct.startsWith("-"),
                        icon = Icons.Default.ArrowUpward,
                        iconColor = DebitRed,
                        iconBgColor = DebitRed.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSummaryCard(
                        title = "Net Inflow",
                        amount = inrFormat.format(periodSummary.netFlow),
                        badgeText = null,
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = AccentBlue,
                        iconBgColor = AccentBlue.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricSummaryCard(
                        title = "Transactions",
                        amount = periodSummary.transactionCount.toString(),
                        badgeText = null,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        iconColor = AccentPurple,
                        iconBgColor = AccentPurple.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Weekly Breakdown Chart
        item {
            FintechCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Weekly Activity (${currentMonth.label})",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                WeeklyDualBarChart(data = weeklyData)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
