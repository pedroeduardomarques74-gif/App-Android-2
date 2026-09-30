package com.bigger.corridas.data

import android.content.Context
import com.bigger.corridas.location.AddressPlace
import org.json.JSONArray
import org.json.JSONObject

data class SavedFavorite(val name: String, val place: AddressPlace)

class FavoritesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("address_favorites_v2", Context.MODE_PRIVATE)
    private val key = "items"
    fun list(): List<SavedFavorite> = decode(prefs.getString(key, null))
    fun add(name: String, place: AddressPlace) {
        val merged = listOf(SavedFavorite(name, place)) + list().filterNot { it.name.equals(name, true) || it.place.formatted.equals(place.formatted, true) }
        prefs.edit().putString(key, encode(merged.take(20))).apply()
    }
    fun remove(name: String) = prefs.edit().putString(key, encode(list().filterNot { it.name == name })).apply()

    private fun encode(items: List<SavedFavorite>): String = JSONArray().apply {
        items.forEach { f -> put(JSONObject().apply {
            put("name", f.name); put("formatted", f.place.formatted); put("street", f.place.street); put("number", f.place.number)
            put("neighborhood", f.place.neighborhood); put("city", f.place.city); put("state", f.place.state); put("postalCode", f.place.postalCode)
            put("latitude", f.place.latitude); put("longitude", f.place.longitude); put("displayName", f.place.displayName); put("secondary", f.place.secondary)
        }) }
    }.toString()
    private fun decode(raw: String?): List<SavedFavorite> = runCatching {
        val a=JSONArray(raw ?: "[]"); buildList { for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(SavedFavorite(o.optString("name"), AddressPlace(o.optString("formatted"),o.optString("street").takeIf{it.isNotBlank()},o.optString("number").takeIf{it.isNotBlank()},o.optString("neighborhood").takeIf{it.isNotBlank()},o.optString("city").takeIf{it.isNotBlank()},o.optString("state").takeIf{it.isNotBlank()},o.optString("postalCode").takeIf{it.isNotBlank()},o.getDouble("latitude"),o.getDouble("longitude"),o.optString("displayName"),o.optString("secondary")))) } }
    }.getOrDefault(emptyList())
}
