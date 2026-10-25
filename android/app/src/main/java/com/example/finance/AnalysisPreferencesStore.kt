package com.example.finance

import android.content.Context

class AnalysisPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("saldo_analysis_v1", Context.MODE_PRIVATE)

    fun excludedIdentities(): Set<String> =
        preferences.getStringSet("excluded_transactions", emptySet()).orEmpty().toSet()

    fun setExcluded(identity: String, excluded: Boolean) {
        if (identity.isBlank()) return
        val updated = excludedIdentities().toMutableSet()
        if (excluded) updated += identity else updated -= identity
        preferences.edit().putStringSet("excluded_transactions", updated).apply()
    }

    fun includeReserves(): Boolean = preferences.getBoolean("include_reserves", false)

    fun setIncludeReserves(include: Boolean) {
        preferences.edit().putBoolean("include_reserves", include).apply()
    }
}
