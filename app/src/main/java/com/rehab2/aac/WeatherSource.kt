package com.rehab2.aac

data class WeatherSource(
    val name: String,
    val url: String
) {
    companion object {
        val PREDEFINED = listOf(
            WeatherSource(
                name = "Open-Meteo Ljubljana",
                url = "https://api.open-meteo.com/v1/forecast?latitude=46.0569&longitude=14.5058&current=temperature_2m,weather_code"
            ),
            WeatherSource(
                name = "Open-Meteo Maribor",
                url = "https://api.open-meteo.com/v1/forecast?latitude=46.5547&longitude=15.6459&current=temperature_2m,weather_code"
            ),
            WeatherSource(
                name = "Open-Meteo Koper",
                url = "https://api.open-meteo.com/v1/forecast?latitude=45.5481&longitude=13.7302&current=temperature_2m,weather_code"
            )
        )
    }
}
