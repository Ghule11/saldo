package com.example.finance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SaldoColorScheme = darkColorScheme(
    primary = FinancePurple,
    onPrimary = FinanceBackground,
    secondary = FinancePurple,
    background = FinanceBackground,
    onBackground = FinanceText,
    surface = FinanceSurface,
    onSurface = FinanceText,
    surfaceVariant = FinanceSurface,
    onSurfaceVariant = FinanceMuted
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaldoColorScheme,
        typography = Typography,
        content = content
    )
}
