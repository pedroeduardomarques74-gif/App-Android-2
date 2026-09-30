package com.bigger.corridas.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bigger.corridas.location.AddressPlace

object NavigationHelper {
    fun openWaze(context: Context, place: AddressPlace) {
        val ll = "${place.latitude},${place.longitude}"
        val appUri = Uri.parse("waze://?ll=$ll&navigate=yes")
        val webUri = Uri.parse("https://www.waze.com/ul?ll=$ll&navigate=yes")
        try { context.startActivity(Intent(Intent.ACTION_VIEW, appUri)) }
        catch (_: ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
    }

    fun navigate(context: Context, place: AddressPlace) {
        val label = Uri.encode(place.routeLabel())
        val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}($label)")
        val i = Intent(Intent.ACTION_VIEW, uri)
        if (i.resolveActivity(context.packageManager) != null) context.startActivity(Intent.createChooser(i, "Abrir rota com"))
        else openWaze(context, place)
    }
}
