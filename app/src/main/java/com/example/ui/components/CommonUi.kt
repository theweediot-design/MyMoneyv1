package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DebitRed
import com.example.ui.theme.EmeraldGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BankLogoBadge(
    bankCode: String,
    modifier: Modifier = Modifier,
    size: Int = 36
) {
    val (bgColor, label) = when (bankCode.uppercase()) {
        "SBI" -> Color(0xFF1E3A8A) to "SBI"
        "HDFC" -> Color(0xFF0284C7) to "HDFC"
        "ICICI" -> Color(0xFFEA580C) to "ICICI"
        "AXIS" -> Color(0xFF9D174D) to "AXIS"
        "KOTAK" -> Color(0xFFDC2626) to "KM"
        "PNB" -> Color(0xFFA21CAF) to "PNB"
        "BOB" -> Color(0xFFF97316) to "BOB"
        "CANARA" -> Color(0xFF0284C7) to "CAN"
        "UNION" -> Color(0xFF1D4ED8) to "UBI"
        "INDIAN" -> Color(0xFFB45309) to "IB"
        "CENTRAL" -> Color(0xFF0F766E) to "CBI"
        "INDUSIND" -> Color(0xFF831843) to "IND"
        "IDFC" -> Color(0xFF7E22CE) to "IDFC"
        "YES" -> Color(0xFF2563EB) to "YES"
        "FEDERAL" -> Color(0xFFF59E0B) to "FB"
        "BANDHAN" -> Color(0xFF0D9488) to "BDN"
        "PAYTM" -> Color(0xFF0EA5E9) to "PYTM"
        "AIRTEL" -> Color(0xFFEF4444) to "AIR"
        "AU", "AUBANK" -> Color(0xFF7C3AED) to "AU"
        else -> Color(0xFF334155) to bankCode.take(2).uppercase()
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 3) (size * 0.28).sp else (size * 0.35).sp
        )
    }
}

@Composable
fun FintechCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        color = backgroundColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun MetricSummaryCard(
    title: String,
    amount: String,
    badgeText: String? = null,
    isPositiveBadge: Boolean = true,
    icon: ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (badgeText != null) {
                    val badgeColor = if (isPositiveBadge) EmeraldGreen else DebitRed
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = amount,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TimeFilterRow(
    selectedFilter: String,
    onSelect: (String) -> Unit,
    onCustomClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filters = listOf("1M", "3M", "6M", "1Y", "Custom")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        filters.forEach { filter ->
            val isSelected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        if (filter == "Custom") {
                            onCustomClick()
                        } else {
                            onSelect(filter)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter,
                    color = if (isSelected) Color(0xFF042F24) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DateRangePickerDialog(
    initialStartDate: Long?,
    initialEndDate: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    var startDate by remember { mutableStateOf(initialStartDate ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)) }
    var endDate by remember { mutableStateOf(initialEndDate ?: System.currentTimeMillis()) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Select Custom Date Range", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Choose start and end dates for your transaction analysis:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                startDate = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                                endDate = System.currentTimeMillis()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Last 30 Days", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                startDate = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                                endDate = System.currentTimeMillis()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Last 90 Days", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start Date:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(dateFormatter.format(Date(startDate)), color = EmeraldGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("End Date:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(dateFormatter.format(Date(endDate)), color = EmeraldGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(startDate, endDate) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Apply Range", color = Color(0xFF042F24), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun MonthSelectorPill(
    currentLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = currentLabel,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AppBottomNav(
    currentTab: String,
    onTabSelect: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("Home", Icons.Default.Home, "Home"),
            Triple("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, "Transactions"),
            Triple("Analytics", Icons.Default.BarChart, "Analytics"),
            Triple("Accounts", Icons.Default.AccountBalance, "Accounts"),
            Triple("More", Icons.Default.MoreHoriz, "More")
        )

        items.forEach { (route, icon, label) ->
            val isSelected = currentTab == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EmeraldGreen,
                    selectedTextColor = EmeraldGreen,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
fun BankFilterChipsRow(
    discoveredBanks: List<String>,
    selectedBanks: Set<String>,
    onToggleBank: (String) -> Unit,
    onClearFilter: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onManageBanksClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (discoveredBanks.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onManageBanksClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(EmeraldGreen.copy(alpha = 0.12f))
                    .border(1.dp, EmeraldGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable { onManageBanksClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Manage Banks",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Manage",
                    color = EmeraldGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val isAllSelected = selectedBanks.isNotEmpty() && (selectedBanks.size >= discoveredBanks.size || discoveredBanks.all { selectedBanks.contains(it) })
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isAllSelected) EmeraldGreen else MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (isAllSelected) EmeraldGreen else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(20.dp)
                )
                .clickable {
                    if (onSelectAll != null) onSelectAll() else onClearFilter()
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "All Banks",
                color = if (isAllSelected) Color(0xFF042F24) else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
            )
        }

        discoveredBanks.forEach { bankCode ->
            val isSelected = selectedBanks.contains(bankCode)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onToggleBank(bankCode) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BankLogoBadge(bankCode = bankCode, size = 18)
                Text(
                    text = bankCode,
                    color = if (isSelected) Color(0xFF042F24) else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun NoBanksSelectedEmptyCard(
    onManageBanksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FintechCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Banks Selected",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No banks selected. Please select at least one bank in Manage Banks to view transactions.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onManageBanksClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = Color(0xFF042F24)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Manage Active Banks",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

