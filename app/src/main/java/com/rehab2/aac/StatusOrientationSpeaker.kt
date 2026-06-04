package com.rehab2.aac

import android.content.Context
import java.util.Calendar
import java.util.Locale

object StatusOrientationSpeaker {
    fun buildSpeechText(
        context: Context,
        calendar: Calendar = Calendar.getInstance(),
        weatherSentence: String? = null
    ): String {
        val baseText = buildBaseSpeechText(context, calendar)
        if (baseText.isBlank()) return ""
        return listOfNotNull(baseText, weatherSentence?.trim()?.takeIf { it.isNotBlank() })
            .joinToString(separator = " ")
    }

    fun buildBaseSpeechText(
        context: Context,
        calendar: Calendar = Calendar.getInstance()
    ): String {
        val settings = StatusOrientationSettings.load(context)
        if (!settings.enabled) return ""

        val parts = mutableListOf<String>()
        if (settings.speakGreeting) {
            parts += greeting(calendar)
        }
        if (settings.speakDate) {
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            parts += "Danes je ${dayName(calendar)}, ${dayOfMonthOrdinalName(dayOfMonth)} ${monthName(calendar)}."
        }
        if (settings.speakTime) {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            parts += String.format(Locale.ROOT, "Ura je %d:%02d.", hour, minute)
        }
        return parts.joinToString(separator = " ").trim()
    }

    private fun greeting(calendar: Calendar): String {
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..9 -> "Dobro jutro."
            in 10..17 -> "Dober dan."
            in 18..21 -> "Dober ve\u010der."
            else -> "Lahko no\u010d."
        }
    }

    private fun dayName(calendar: Calendar): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "ponedeljek"
            Calendar.TUESDAY -> "torek"
            Calendar.WEDNESDAY -> "sreda"
            Calendar.THURSDAY -> "\u010detrtek"
            Calendar.FRIDAY -> "petek"
            Calendar.SATURDAY -> "sobota"
            else -> "nedelja"
        }
    }

    private fun monthName(calendar: Calendar): String {
        return when (calendar.get(Calendar.MONTH)) {
            Calendar.JANUARY -> "januar"
            Calendar.FEBRUARY -> "februar"
            Calendar.MARCH -> "marec"
            Calendar.APRIL -> "april"
            Calendar.MAY -> "maj"
            Calendar.JUNE -> "junij"
            Calendar.JULY -> "julij"
            Calendar.AUGUST -> "avgust"
            Calendar.SEPTEMBER -> "september"
            Calendar.OCTOBER -> "oktober"
            Calendar.NOVEMBER -> "november"
            else -> "december"
        }
    }

    private fun dayOfMonthOrdinalName(dayOfMonth: Int): String {
        return when (dayOfMonth) {
            1 -> "prvi"
            2 -> "drugi"
            3 -> "tretji"
            4 -> "\u010detrti"
            5 -> "peti"
            6 -> "\u0161esti"
            7 -> "sedmi"
            8 -> "osmi"
            9 -> "deveti"
            10 -> "deseti"
            11 -> "enajsti"
            12 -> "dvanajsti"
            13 -> "trinajsti"
            14 -> "\u0161tirinajsti"
            15 -> "petnajsti"
            16 -> "\u0161estnajsti"
            17 -> "sedemnajsti"
            18 -> "osemnajsti"
            19 -> "devetnajsti"
            20 -> "dvajseti"
            21 -> "enaindvajseti"
            22 -> "dvaindvajseti"
            23 -> "triindvajseti"
            24 -> "\u0161tiriindvajseti"
            25 -> "petindvajseti"
            26 -> "\u0161estindvajseti"
            27 -> "sedemindvajseti"
            28 -> "osemindvajseti"
            29 -> "devetindvajseti"
            30 -> "trideseti"
            31 -> "enaintrideseti"
            else -> dayOfMonth.toString()
        }
    }
}
