package com.bigger.corridas.location

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import kotlin.math.*

class AddressSearchService {
    private val userAgent = "CalculadoraCorridas/2.1 (Android; private-driver-fare-calculator)"

    fun search(rawQuery: String, context: AddressSearchContext, limit: Int = 8): Result<List<AddressPlace>> = runCatching {
        val query = normalizeQuery(rawQuery)
        require(query.length >= 2) { "Digite pelo menos 2 caracteres" }

        if (query.matches(Regex("^\\d{1,6}[A-Za-z]?$")) && !context.broad && context.origin != null) {
            val localByNumber = searchHouseNumberNearby(query, context, limit)
            if (localByNumber.isNotEmpty()) return@runCatching localByNumber
            return@runCatching emptyList()
        }

        val attempts = buildQueries(query, context)
        val collected = LinkedHashMap<String, AddressPlace>()
        for (attempt in attempts) {
            fetchSearch(attempt.query, context, attempt.useViewBox, limit * 2).forEach { place ->
                val key = "${place.latitude.formatKey()},${place.longitude.formatKey()}"
                if (!collected.containsKey(key)) collected[key] = place
            }
            if (collected.size >= limit * 2) break
        }

        collected.values
            .map { p -> p.copy(distanceKm = context.origin?.let { distanceKm(it.lat, it.lon, p.latitude, p.longitude) }) }
            .sortedWith(
                compareBy<AddressPlace> { localityRank(it, context) }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenBy { categoryRank(it.category) }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) }
            )
            .take(limit)
    }

    fun geocode(text: String, context: AddressSearchContext? = null): Result<AddressPlace> = runCatching {
        val ctx = context ?: AddressSearchContext(broad = true)
        search(text, ctx, 5).getOrThrow().firstOrNull() ?: error("Endereço não encontrado")
    }

    private fun searchHouseNumberNearby(number: String, context: AddressSearchContext, limit: Int): List<AddressPlace> {
        val origin = context.origin ?: return emptyList()
        val query = """
            [out:json][timeout:12];
            (
              nwr["addr:housenumber"="$number"](around:30000,${origin.lat},${origin.lon});
            );
            out center tags 80;
        """.trimIndent()

        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter?data=$encoded",
            "https://overpass.kumi.systems/api/interpreter?data=$encoded"
        )

        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            try {
                val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6500
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", userAgent)
                }
                if (c.responseCode !in 200..299) throw IllegalStateException("Busca local indisponível")
                val root = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val elements = root.optJSONArray("elements") ?: JSONArray()
                val out = mutableListOf<AddressPlace>()
                for (i in 0 until elements.length()) {
                    val e = elements.getJSONObject(i)
                    val tags = e.optJSONObject("tags") ?: JSONObject()
                    val lat = when {
                        e.has("lat") -> e.optDouble("lat")
                        e.optJSONObject("center") != null -> e.getJSONObject("center").optDouble("lat")
                        else -> Double.NaN
                    }
                    val lon = when {
                        e.has("lon") -> e.optDouble("lon")
                        e.optJSONObject("center") != null -> e.getJSONObject("center").optDouble("lon")
                        else -> Double.NaN
                    }
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val street = tags.optString("addr:street").trim().ifBlank {
                        tags.optString("addr:place").trim().ifBlank { null }
                    } ?: continue
                    val n = tags.optString("addr:housenumber").trim().ifBlank { number }
                    val neighborhood = firstTag(tags, "addr:suburb", "addr:neighbourhood", "addr:district", "addr:quarter")
                    val city = firstTag(tags, "addr:city", "addr:town", "addr:municipality") ?: context.city
                    val state = firstTag(tags, "addr:state") ?: context.state
                    val postal = firstTag(tags, "addr:postcode")
                    val d = distanceKm(origin.lat, origin.lon, lat, lon)
                    val main = "$street, $n"
                    val secondary = listOfNotNull(neighborhood, city, state).distinct().joinToString(" • ")
                    out += AddressPlace(
                        formatted = listOf(main, secondary).filter { it.isNotBlank() }.joinToString(", "),
                        street = street,
                        number = n,
                        neighborhood = neighborhood,
                        city = city,
                        state = state,
                        postalCode = postal,
                        latitude = lat,
                        longitude = lon,
                        displayName = main,
                        secondary = secondary,
                        category = "Endereço",
                        categoryIcon = "🏠",
                        distanceKm = d
                    )
                }
                return out
                    .distinctBy { "${it.street?.lowercase()}|${it.number}|${it.latitude.formatKey()}|${it.longitude.formatKey()}" }
                    .sortedBy { it.distanceKm ?: Double.MAX_VALUE }
                    .take(limit)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (lastError != null) throw lastError
        return emptyList()
    }

    private data class QueryAttempt(val query: String, val useViewBox: Boolean)

    private fun buildQueries(query: String, context: AddressSearchContext): List<QueryAttempt> {
        val city = context.city?.trim().orEmpty()
        val state = context.state?.trim().orEmpty()
        val hasNumber = query.contains(Regex("\\d"))
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        val number = tokens.firstOrNull { it.matches(Regex("\\d{1,6}[A-Za-z]?,?")) }?.trimEnd(',')
        val words = tokens.filterNot { it.trimEnd(',') == number }.joinToString(" ").trim()

        val out = mutableListOf<QueryAttempt>()
        fun add(q: String, box: Boolean) { if (q.isNotBlank() && out.none { it.query.equals(q, true) }) out += QueryAttempt(q, box) }

        if (!context.broad && city.isNotBlank()) {
            add("$query, $city, $state, Brasil", true)
            if (hasNumber && !number.isNullOrBlank() && words.isNotBlank()) {
                add("$words, $number, $city, $state, Brasil", true)
                add("$number, $words, $city, $state, Brasil", true)
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
            "extratags=1",
            "limit=$limit",
            "countrycodes=br",
            "accept-language=pt-BR",
            "q=${enc(query)}"
        )
        if (useViewBox) {
            val p = context.origin
            if (p != null) {
                val lonSpan = 0.65
                val latSpan = 0.52
                params += "viewbox=${p.lon - lonSpan},${p.lat + latSpan},${p.lon + lonSpan},${p.lat - latSpan}"
                params += "bounded=0"
            }
        }
        val url = URL("https://nominatim.openstreetmap.org/search?${params.joinToString("&")}")
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8500
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

    private fun parsePlace(o: JSONObject): AddressPlace? {
        val lat = o.optString("lat").toDoubleOrNull() ?: return null
        val lon = o.optString("lon").toDoubleOrNull() ?: return null
        val a = o.optJSONObject("address") ?: JSONObject()
        val street = firstNonBlank(a, "road", "pedestrian", "residential", "footway", "path", "cycleway")
        val number = firstNonBlank(a, "house_number")
        val neighborhood = firstNonBlank(a, "suburb", "neighbourhood", "quarter", "city_district", "borough")
        val city = firstNonBlank(a, "city", "town", "municipality", "village", "county")
        val state = firstNonBlank(a, "state", "state_district")
        val postcode = firstNonBlank(a, "postcode")
        val named = o.optJSONObject("namedetails")?.optString("name").orEmpty().ifBlank { o.optString("name") }
        val clazz = o.optString("category").ifBlank { o.optString("class") }
        val type = o.optString("type").ifBlank { o.optString("addresstype") }
        val cat = classify(clazz, type, named, street, neighborhood)
        val main = when {
            cat.first == "Rua" && !street.isNullOrBlank() && !number.isNullOrBlank() -> "$street, $number"
            cat.first == "Rua" && !street.isNullOrBlank() -> street
            named.isNotBlank() -> named
            !street.isNullOrBlank() && !number.isNullOrBlank() -> "$street, $number"
            !street.isNullOrBlank() -> street
            !neighborhood.isNullOrBlank() -> neighborhood
            else -> o.optString("display_name").substringBefore(',').ifBlank { "Local encontrado" }
        }
        val secondParts = listOfNotNull(
            if (!number.isNullOrBlank() && !main.contains(number)) number else null,
            neighborhood,
            city,
            state
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
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
            secondary = secondary,
            category = cat.first,
            categoryIcon = cat.second
        )
    }

    private fun classify(clazz: String, type: String, name: String, street: String?, neighborhood: String?): Pair<String, String> {
        val c = clazz.lowercase()
        val t = type.lowercase()
        val n = name.lowercase()
        return when {
            c == "highway" || t in setOf("road","residential","primary","secondary","tertiary","street") -> "Rua" to "🛣️"
            t in setOf("supermarket","grocery","convenience") || n.contains("supermercado") || n.contains("mercado") -> "Supermercado" to "🛒"
            t in setOf("university","college") || n.contains("faculdade") || n.contains("universidade") -> "Faculdade" to "🎓"
            t in setOf("school","kindergarten") || n.contains("escola") || n.contains("colégio") -> "Escola" to "🏫"
            t in setOf("hospital","clinic","doctors") || n.contains("hospital") || n.contains("clínica") -> "Saúde" to "🏥"
            t in setOf("pharmacy") || n.contains("farmácia") || n.contains("drogaria") -> "Farmácia" to "💊"
            t in setOf("fuel") || n.contains("posto") -> "Posto" to "⛽"
            t in setOf("restaurant","fast_food","cafe","bar") -> "Alimentação" to "🍽️"
            t in setOf("hotel","motel","guest_house") -> "Hotel" to "🏨"
            t in setOf("bus_station","station","bus_stop") || n.contains("rodoviária") -> "Transporte" to "🚌"
            t in setOf("airport","aerodrome") || n.contains("aeroporto") -> "Aeroporto" to "✈️"
            t in setOf("mall","department_store","retail") || n.contains("shopping") -> "Compras" to "🛍️"
            t in setOf("place_of_worship","church") || n.contains("igreja") -> "Igreja" to "⛪"
            t in setOf("neighbourhood","suburb","quarter") || (!neighborhood.isNullOrBlank() && street.isNullOrBlank()) -> "Bairro" to "📍"
            t in setOf("city","town","municipality","village") -> "Cidade" to "🏙️"
            name.isBlank() && !street.isNullOrBlank() -> "Endereço" to "🏠"
            c == "amenity" || c == "shop" || c == "tourism" || c == "office" -> "Estabelecimento" to "📌"
            else -> "Local" to "📍"
        }
    }

    private fun localityRank(place: AddressPlace, context: AddressSearchContext): Int {
        if (context.broad) return 10
        val sameCity = !context.city.isNullOrBlank() && place.city?.contains(context.city!!, true) == true
        val sameState = !context.state.isNullOrBlank() && place.state?.contains(context.state!!, true) == true
        return when { sameCity -> 0; sameState -> 2; else -> 5 }
    }

    private fun categoryRank(c: String) = when(c) {
        "Endereço" -> 0; "Rua" -> 1; "Supermercado" -> 2; "Faculdade" -> 2; "Saúde" -> 2; "Farmácia" -> 2
        "Posto" -> 2; "Alimentação" -> 2; "Escola" -> 2; "Bairro" -> 3; "Cidade" -> 8; else -> 4
    }

    private fun firstNonBlank(o: JSONObject, vararg keys: String): String? =
        keys.asSequence().map { o.optString(it).trim() }.firstOrNull { it.isNotBlank() }

    private fun firstTag(o: JSONObject, vararg keys: String): String? =
        keys.asSequence().map { o.optString(it).trim() }.firstOrNull { it.isNotBlank() }

    private fun normalizeQuery(text: String): String = text.trim().replace(Regex("\\s+"), " ")
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun Double.formatKey() = String.format(Locale.US, "%.5f", this)

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2) + cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2).pow(2)
        return 2*r*asin(sqrt(a))
    }
}
