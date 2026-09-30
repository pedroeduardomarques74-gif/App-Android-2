package com.bigger.corridas.data

import android.content.Context
import com.bigger.corridas.location.AddressPlace
import org.json.JSONArray
import org.json.JSONObject

class RecentAddressRepository(context: Context) {
    private val prefs = context.getSharedPreferences("recent_addresses_v2", Context.MODE_PRIVATE)
    private val key = "items"

    fun list(): List<AddressPlace> = decode(prefs.getString(key, null)).take(8)
    fun add(place: AddressPlace) {
        val merged = listOf(place) + list().filterNot { it.formatted.equals(place.formatted, true) }
        prefs.edit().putString(key, encode(merged.take(8))).apply()
    }
    fun clear() = prefs.edit().remove(key).apply()

    private fun encode(items: List<AddressPlace>): String = JSONArray().apply {
        items.forEach { p -> put(JSONObject().apply {
            put("formatted", p.formatted); put("street", p.street); put("number", p.number)
            put("neighborhood", p.neighborhood); put("city", p.city); put("state", p.state); put("postalCode", p.postalCode)
            put("latitude", p.latitude); put("longitude", p.longitude); put("displayName", p.displayName); put("secondary", p.secondary)
        }) }
    }.toString()

    private fun decode(raw: String?): List<AddressPlace> = runCatching {
        val a = JSONArray(raw ?: "[]")
        buildList { for (i in 0 until a.length()) { val o=a.getJSONObject(i); add(AddressPlace(o.optString("formatted"), o.optString("street").takeIf{it.isNotBlank()}, o.optString("number").takeIf{it.isNotBlank()}, o.optString("neighborhood").takeIf{it.isNotBlank()}, o.optString("city").takeIf{it.isNotBlank()}, o.optString("state").takeIf{it.isNotBlank()}, o.optString("postalCode").takeIf{it.isNotBlank()}, o.getDouble("latitude"), o.getDouble("longitude"), o.optString("displayName"), o.optString("secondary"))) } }
    }.getOrDefault(emptyList())
}
