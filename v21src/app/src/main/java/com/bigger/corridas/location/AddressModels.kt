package com.bigger.corridas.location

import com.bigger.corridas.route.Point

data class AddressPlace(
    val formatted: String,
    val street: String? = null,
    val number: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val displayName: String = formatted,
    val secondary: String = "",
    val category: String = "Local",
    val categoryIcon: String = "📍",
    val distanceKm: Double? = null
) {
    val point: Point get() = Point(latitude, longitude)
    val hasHouseNumber: Boolean get() = !number.isNullOrBlank()
    fun routeLabel(): String = formatted.ifBlank { displayName }
    fun categoryBadge(): String = "$categoryIcon  $category"
}

data class AddressSearchContext(
    val origin: Point? = null,
    val city: String? = null,
    val state: String? = null,
    val broad: Boolean = false
)
