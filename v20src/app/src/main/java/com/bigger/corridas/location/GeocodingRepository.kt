package com.bigger.corridas.location

import com.bigger.corridas.route.Point
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeocodingRepository {
    private val userAgent = "CalculadoraCorridas/2.0 (Android; private-driver-fare-calculator)"

    fun reverse(point: Point): Result<AddressPlace> = runCatching {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&accept-language=pt-BR&lat=${point.lat}&lon=${point.lon}")
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 8000; requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
        }
        if (c.responseCode !in 200..299) error("Não foi possível identificar o endereço atual")
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val a = o.optJSONObject("address") ?: JSONObject()
        fun first(vararg keys: String) = keys.asSequence().map { a.optString(it).trim() }.firstOrNull { it.isNotBlank() }
        val street = first("road", "pedestrian", "residential")
        val number = first("house_number")
        val neighborhood = first("suburb", "neighbourhood", "quarter", "city_district")
        val city = first("city", "town", "municipality", "village", "county")
        val state = first("state", "state_district")
        val postal = first("postcode")
        val main = listOfNotNull(street, number).joinToString(", ").ifBlank { o.optString("name").ifBlank { "Minha localização" } }
        val secondary = listOfNotNull(neighborhood, city, state).distinct().joinToString(" • ")
        val formatted = listOf(main, secondary).filter { it.isNotBlank() }.joinToString(", ")
        AddressPlace(formatted, street, number, neighborhood, city, state, postal, point.lat, point.lon, main, secondary)
    }
}
