package com.example.gpstracker

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.gpstracker.domain.placeName
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * REQ-9: the name of the place at a position, looked up with the phone's geocoder (needs an internet connection),
 * or null when it is not available.
 */
suspend fun lookUpPlace(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val address = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    return address?.let { placeName(it.locality, it.subAdminArea, it.adminArea, it.countryName) }
}
