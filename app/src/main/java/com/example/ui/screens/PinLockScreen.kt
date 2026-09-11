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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityManager
import com.example.ui.theme.DebitRed
import com.example.ui.theme.EmeraldGreen

@Composable
fun PinLockScreen(
    securityManager: SecurityManager,
    isSettingPin: Boolean = false,
    onSuccess: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var confirmPinStage by remember { mutableStateOf(false) }
    var firstEnteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pinLength = 6

    fun onKeyClick(digit: String) {
        if (enteredPin.length < pinLength) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == pinLength) {
                if (isSettingPin) {
                    if (!confirmPinStage) {
                        firstEnteredPin = newPin
                        confirmPinStage = true
                        enteredPin = ""
                    } else {
                        if (newPin == firstEnteredPin) {
                            securityManager.pin = newPin
                            onSuccess()
                        } else {
                            errorMessage = "PINs do not match. Try again."
                            confirmPinStage = false
                            enteredPin = ""
                            firstEnteredPin = ""
                        }
                    }
                } else {
                    if (securityManager.verifyPin(newPin)) {
                        onSuccess()
                    } else {
                        errorMessage = "Incorrect PIN. Try again."
                        enteredPin = ""
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen.copy(alpha = 0.15f))
                    .border(2.dp, EmeraldGreen.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = when {
                    isSettingPin && !confirmPinStage -> "Set 6-Digit PIN"
                    isSettingPin && confirmPinStage -> "Confirm 6-Digit PIN"
                    else -> "Welcome to MyMoney"
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    isSettingPin && !confirmPinStage -> "Create a secure PIN for local access"
                    isSettingPin && confirmPinStage -> "Re-enter your 6-digit PIN to confirm"
                    else -> "Enter PIN to access your private finances"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 6-digit pin indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until pinLength) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) EmeraldGreen else Color.Transparent)
                            .border(1.5.dp, if (isFilled) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMessage ?: "",
                    color = DebitRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Numeric Keypad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val keyRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )

            keyRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (key in listOf("BIO", "DEL")) Color.Transparent else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    when (key) {
                                        "DEL" -> onBackspace()
                                        "BIO" -> {
                                            if (securityManager.isBiometricEnabled) {
                                                onSuccess()
                                            }
                                        }
                                        else -> onKeyClick(key)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (key) {
                                "DEL" -> {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                "BIO" -> {
                                    if (securityManager.isBiometricEnabled) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Biometric",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = key,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
