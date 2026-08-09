package com.rehab2.aac

import android.content.Context
import java.util.Calendar
import java.util.Locale

object StatusOrientationSpeaker {
    fun buildSpeechText(
        context: Context,
        calendar: Calendar = Calendar.getInstance(),
        weatherSentence: String? = null,
        languageCode: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE
    ): String {
        val baseText = buildBaseSpeechText(context, calendar, languageCode)
        if (baseText.isBlank()) return ""
        return listOfNotNull(baseText, weatherSentence?.trim()?.takeIf { it.isNotBlank() })
            .joinToString(separator = " ")
    }

    fun buildBaseSpeechText(
        context: Context,
        calendar: Calendar = Calendar.getInstance(),
        languageCode: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE
    ): String {
        val settings = StatusOrientationSettings.load(context)
        if (!settings.enabled) return ""
        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)

        val parts = mutableListOf<String>()
        if (settings.speakGreeting) {
            parts += greeting(calendar, normalizedLanguage)
        }
        if (settings.speakDate) {
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            parts += if (normalizedLanguage == "uk") {
                "Сьогодні ${dayName(calendar, normalizedLanguage)}, $dayOfMonth ${monthName(calendar, normalizedLanguage)}."
            } else {
                "Danes je ${dayName(calendar, normalizedLanguage)}, ${dayOfMonthOrdinalName(dayOfMonth)} ${monthName(calendar, normalizedLanguage)}."
            }
        }
        if (settings.speakTime) {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            parts += if (normalizedLanguage == "uk") {
                String.format(Locale.ROOT, "Зараз %d:%02d.", hour, minute)
            } else {
                String.format(Locale.ROOT, "Ura je %d:%02d.", hour, minute)
            }
        }
        return parts.joinToString(separator = " ").trim()
    }

    private fun greeting(calendar: Calendar, languageCode: String): String {
        return if (languageCode == "uk") {
            when (calendar.get(Calendar.HOUR_OF_DAY)) {
                in 5..9 -> "Доброго ранку."
                in 10..17 -> "Добрий день."
                in 18..21 -> "Добрий вечір."
                else -> "Доброї ночі."
            }
        } else {
            when (calendar.get(Calendar.HOUR_OF_DAY)) {
                in 5..9 -> "Dobro jutro."
                in 10..17 -> "Dober dan."
                in 18..21 -> "Dober večer."
                else -> "Lahko noč."
            }
        }
    }

    private fun dayName(calendar: Calendar, languageCode: String): String {
        return if (languageCode == "uk") {
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "понеділок"
                Calendar.TUESDAY -> "вівторок"
                Calendar.WEDNESDAY -> "середа"
                Calendar.THURSDAY -> "четвер"
                Calendar.FRIDAY -> "п'ятниця"
                Calendar.SATURDAY -> "субота"
                else -> "неділя"
            }
        } else {
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "ponedeljek"
                Calendar.TUESDAY -> "torek"
                Calendar.WEDNESDAY -> "sreda"
                Calendar.THURSDAY -> "četrtek"
                Calendar.FRIDAY -> "petek"
                Calendar.SATURDAY -> "sobota"
                else -> "nedelja"
            }
        }
    }

    private fun monthName(calendar: Calendar, languageCode: String): String {
        return if (languageCode == "uk") {
            when (calendar.get(Calendar.MONTH)) {
                Calendar.JANUARY -> "січня"
                Calendar.FEBRUARY -> "лютого"
                Calendar.MARCH -> "березня"
                Calendar.APRIL -> "квітня"
                Calendar.MAY -> "травня"
                Calendar.JUNE -> "червня"
                Calendar.JULY -> "липня"
                Calendar.AUGUST -> "серпня"
                Calendar.SEPTEMBER -> "вересня"
                Calendar.OCTOBER -> "жовтня"
                Calendar.NOVEMBER -> "листопада"
                else -> "грудня"
            }
        } else {
            when (calendar.get(Calendar.MONTH)) {
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
