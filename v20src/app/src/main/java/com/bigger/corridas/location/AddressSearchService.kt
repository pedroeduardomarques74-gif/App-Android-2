package com.bigger.corridas.location

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class AddressSearchService {
    private val userAgent = "CalculadoraCorridas/2.0 (Android; private-driver-fare-calculator)"

    fun search(rawQuery: String, context: AddressSearchContext, limit: Int = 8): Result<List<AddressPlace>> = runCatching {
        val query = normalizeQuery(rawQuery)
        require(query.length >= 2) { "Digite pelo menos 2 caracteres" }

        val attempts = buildQueries(query, context)
        val collected = LinkedHashMap<String, AddressPlace>()
        for (attempt in attempts) {
            fetchSearch(attempt.query, context, attempt.useViewBox, limit).forEach { place ->
                val key = "${place.latitude.formatKey()},${place.longitude.formatKey()}"
                if (!collected.containsKey(key)) collected[key] = place
            }
            if (collected.size >= limit) break
        }

        collected.values
            .sortedWith(compareBy<AddressPlace> { localityRank(it, context) }.thenBy { it.displayName.lowercase(Locale.getDefault()) })
            .take(limit)
    }

    fun geocode(text: String, context: AddressSearchContext? = null): Result<AddressPlace> = runCatching {
        val ctx = context ?: AddressSearchContext(broad = true)
        search(text, ctx, 5).getOrThrow().firstOrNull() ?: error("Endereço não encontrado")
    }

    private data class QueryAttempt(val query: String, val useViewBox: Boolean)

    private fun buildQueries(query: String, context: AddressSearchContext): List<QueryAttempt> {
        val city = context.city?.trim().orEmpty()
        val state = context.state?.trim().orEmpty()
        val numberOnly = query.matches(Regex("^\\d{1,6}[A-Za-z]?$"))
        val hasNumber = query.contains(Regex("\\d"))
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        val number = tokens.firstOrNull { it.matches(Regex("\\d{1,6}[A-Za-z]?,?")) }?.trimEnd(',')
        val words = tokens.filterNot { it.trimEnd(',') == number }.joinToString(" ").trim()

        val out = mutableListOf<QueryAttempt>()
        fun add(q: String, box: Boolean) { if (q.isNotBlank() && out.none { it.query.equals(q, true) }) out += QueryAttempt(q, box) }

        if (!context.broad && city.isNotBlank()) {
            if (numberOnly) {
                add("$query, $city, $state, Brasil", true)
                add("número $query, $city, $state, Brasil", true)
            } else {
                add("$query, $city, $state, Brasil", true)
                if (hasNumber && !number.isNullOrBlank() && words.isNotBlank()) {
                    add("$words, $number, $city, $state, Brasil", true)
                    add("$number, $words, $city, $state, Brasil", true)
                }
            }
            add(query, true)
        } else {
            add("$query, Brasil", false)
            if (hasNumber && !number.isNullOrBlank() && words.isNotBlank()) {
                add("$words, $number, Brasil", false)
                add("$number, $words, Brasil", false)
            }
        }
        return out
    }

    private fun fetchSearch(query: String, context: AddressSearchContext, useViewBox: Boolean, limit: Int): List<AddressPlace> {
        val params = mutableListOf(
            "format=jsonv2",
            "addressdetails=1",
            "namedetails=1",
            "limit=$limit",
            "countrycodes=br",
            "accept-language=pt-BR",
            "q=${enc(query)}"
        )
        if (useViewBox) {
            val p = context.origin
            if (p != null) {
                val lonSpan = 0.45
                val latSpan = 0.38
                params += "viewbox=${p.lon - lonSpan},${p.lat + latSpan},${p.lon + lonSpan},${p.lat - latSpan}"
                params += "bounded=0"
            }
        }
        val url = URL("https://nominatim.openstreetmap.org/search?${params.joinToString("&")}")
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
        }
        if (c.responseCode !in 200..299) error("Serviço de endereços indisponível")
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return buildList {
            for (i in 0 until arr.length()) parsePlace(arr.getJSONObject(i))?.let(::add)
        }
    }

    private fun parsePlace(o: org.json.JSONObject): AddressPlace? {
        val lat = o.optString("lat").toDoubleOrNull() ?: return null
        val lon = o.optString("lon").toDoubleOrNull() ?: return null
        val a = o.optJSONObject("address") ?: org.json.JSONObject()
        val street = firstNonBlank(a, "road", "pedestrian", "residential", "footway", "path", "cycleway")
        val number = firstNonBlank(a, "house_number")
        val neighborhood = firstNonBlank(a, "suburb", "neighbourhood", "quarter", "city_district", "borough")
        val city = firstNonBlank(a, "city", "town", "municipality", "village", "county")
        val state = firstNonBlank(a, "state", "state_district")
        val postcode = firstNonBlank(a, "postcode")
        val named = o.optJSONObject("namedetails")?.optString("name").orEmpty().ifBlank { o.optString("name") }
        val main = when {
            !street.isNullOrBlank() && !number.isNullOrBlank() -> "$street, $number"
            !street.isNullOrBlank() -> street
            named.isNotBlank() -> named
            !neighborhood.isNullOrBlank() -> neighborhood
            else -> o.optString("display_name").substringBefore(',').ifBlank { "Local encontrado" }
        }
        val secondParts = listOfNotNull(neighborhood, city, state).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val secondary = secondParts.joinToString(" • ")
        val formattedParts = mutableListOf<String>()
        formattedParts += main
        if (!neighborhood.isNullOrBlank() && !main.equals(neighborhood, true)) formattedParts += neighborhood
        if (!city.isNullOrBlank()) formattedParts += city
        if (!state.isNullOrBlank()) formattedParts += state
        return AddressPlace(
            formatted = formattedParts.distinct().joinToString(", "),
            street = street,
            number = number,
            neighborhood = neighborhood,
            city = city,
            state = state,
            postalCode = postcode,
            latitude = lat,
            longitude = lon,
            displayName = main,
            secondary = secondary
        )
    }

    private fun localityRank(place: AddressPlace, context: AddressSearchContext): Int {
        if (context.broad) return 10
        val sameCity = !context.city.isNullOrBlank() && place.city?.contains(context.city!!, true) == true
        val sameState = !context.state.isNullOrBlank() && place.state?.contains(context.state!!, true) == true
        return when { sameCity -> 0; sameState -> 2; else -> 5 }
    }

    private fun firstNonBlank(o: org.json.JSONObject, vararg keys: String): String? =
        keys.asSequence().map { o.optString(it).trim() }.firstOrNull { it.isNotBlank() }

    private fun normalizeQuery(text: String): String = text.trim().replace(Regex("\\s+"), " ")
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun Double.formatKey() = String.format(Locale.US, "%.5f", this)
}
