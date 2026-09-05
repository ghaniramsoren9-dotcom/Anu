package com.ghaniram.zoya

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Local device data for explicit user requests: contacts, direct calls, location and India time. */
class DeviceContactLocationManager(private val app: Application) {
    fun findContact(query: String): String {
        if (!has(Manifest.permission.READ_CONTACTS)) return "Contacts permission is not enabled for Anu."
        if (query.isBlank()) return "contact name is missing"
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        val matches = mutableListOf<Pair<String, String>>()
        app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")?.use { c ->
            val ni = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); val pi = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); val wanted = normalize(query)
            while (c.moveToNext() && matches.size < 5) { val name = c.getString(ni).orEmpty(); val number = c.getString(pi).orEmpty(); val n = normalize(name); if (n == wanted || n.contains(wanted) || wanted.contains(n)) matches.add(name to number) }
        }
        if (matches.isEmpty()) return "I couldn't find a contact named $query."
        return matches.distinct().joinToString("\n") { "${it.first}: ${it.second}" }
    }

    fun directCall(query: String): String {
        if (!has(Manifest.permission.READ_CONTACTS)) return "Contacts permission is not enabled for Anu."
        if (!has(Manifest.permission.CALL_PHONE)) return "Phone call permission is not enabled for Anu."
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        var found: Pair<String, String>? = null
        app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            val ni = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); val pi = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); val wanted = normalize(query)
            while (c.moveToNext()) { val name = c.getString(ni).orEmpty(); val number = c.getString(pi).orEmpty(); val n = normalize(name); if (n == wanted || n.contains(wanted) || wanted.contains(n)) { found = name to number; break } }
        }
        val contact = found ?: return "I couldn't find a contact named $query."
        return runCatching { app.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(contact.second)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "calling ${contact.first}" }.getOrElse { "I couldn't call ${contact.first}: ${it.message ?: "unknown error"}" }
    }

    fun currentLocation(): String {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) return "Location permission is not enabled for Anu."
        val lm = app.getSystemService(LocationManager::class.java) ?: return "Location service is unavailable."
        val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return "I don't have a recent device location yet. Turn on Location and try again."
        val ageMinutes = ((System.currentTimeMillis() - location.time).coerceAtLeast(0L)) / 60000L
        val address = runCatching {
            if (Geocoder.isPresent()) Geocoder(app, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { a ->
                listOfNotNull(a.featureName, a.subLocality, a.locality, a.adminArea, a.countryName).distinct().joinToString(", ")
            } else null
        }.getOrNull()
        return buildString { append("latitude=${location.latitude}, longitude=${location.longitude}"); if (!address.isNullOrBlank()) append(", address=$address"); append(", locationAgeMinutes=$ageMinutes, source=${location.provider}") }
    }

    fun indiaTime(): String = SimpleDateFormat("hh:mm:ss a, EEEE, dd MMMM yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date()) + " (India Standard Time, Asia/Kolkata)"
    private fun has(permission: String): Boolean = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
}
