package com.example.finance

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

fun FinanceAnalysisReport.toJson(asOf: LocalDate, currency: String): String {
    val forecastJson = JSONObject()
        .put("ausgegeben_bisher", forecast.spentCents)
        .put("anstehende_fixkosten", forecast.upcomingFixedCents)
        .put("variabler_tagesschnitt", nullable(forecast.variableDailyCents))
        .put("resttage", forecast.remainingDays)
        .put("prognose", nullable(forecast.projectedCents))
        .put("einnahmenbasis", nullable(forecast.incomeBasisCents))
        .put("differenz", nullable(forecast.differenceCents))
        .put("ampelstatus", when (forecast.signal) {
            ForecastSignal.InLimit -> "im_limit"
            ForecastSignal.Tight -> "knapp"
            ForecastSignal.Over -> "ueberschritten"
            null -> JSONObject.NULL
        })
        .put("taegliches_restbudget", nullable(forecast.dailyRoomCents))
        .put("ruecklagen_einbezogen", forecast.reservesIncluded)
        .put("ruecklagen_abzug", forecast.reserveDeductionCents)
        .put("fixkosten", JSONArray().also { array ->
            forecast.fixedCosts.forEach { item ->
                array.put(JSONObject().put("name", item.name).put("betrag", item.amountCents))
            }
        })
    val reservesJson = JSONObject()
        .put("monatliche_ruecklage", reserves.monthlyCents)
        .put("im_aktuellen_monat_noch_offen", reserves.remainingThisMonthCents)
        .put("posten", JSONArray().also { array ->
            reserves.entries.forEach { entry ->
                array.put(JSONObject()
                    .put("name", entry.name)
                    .put("intervall", entry.period)
                    .put("jahressumme", entry.yearlyCents)
                    .put("monatliche_ruecklage", entry.monthlyCents))
            }
        })
    val seasonJson = JSONObject()
        .put("historische_monate", seasonality.historyMonths)
        .put("einstufung_verfuegbar", seasonality.canClassify)
        .put("anteil_sonstiges_prozent", seasonality.otherSharePercent)
        .put("monate", JSONArray().also { array ->
            seasonality.months.forEach { month ->
                array.put(JSONObject()
                    .put("kalendermonat", month.month)
                    .put("abweichung_prozent", month.deviationPercent)
                    .put("einstufung", when (month.signal) {
                        SeasonalSignal.Save -> "sparen"
                        SeasonalSignal.Neutral -> "neutral"
                        SeasonalSignal.Room -> "spielraum"
                        null -> JSONObject.NULL
                    })
                    .put("kategorien", JSONArray().also { drivers ->
                        month.drivers.forEach { driver ->
                            drivers.put(JSONObject()
                                .put("name", driver.category)
                                .put("abweichung_prozent", driver.deviationPercent)
                                .put("monatsdurchschnitt", driver.averageCents)
                                .put("jahresmonatsschnitt", driver.baselineCents))
                        }
                    }))
            }
        })
    return JSONObject()
        .put("stand", asOf.toString())
        .put("waehrung", currency)
        .put("betraege_in", "Cent")
        .put("monatsprognose", forecastJson)
        .put("ruecklagen", reservesJson)
        .put("saison", seasonJson)
        .toString(2)
}

private fun nullable(value: Any?): Any = value ?: JSONObject.NULL
