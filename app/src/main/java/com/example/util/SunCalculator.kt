package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/**
 * Astronomical Solar Calculator for calculating exact Sunrise & Sunset times.
 * Default coordinates configured for Plouharnel (56340, Morbihan, Bretagne, France).
 */
object SunCalculator {

    // Plouharnel coordinates (Morbihan, France)
    const val PLOUHARNEL_LATITUDE = 47.5978
    const val PLOUHARNEL_LONGITUDE = -3.1147
    const val PLOUHARNEL_LOCATION_NAME = "Plouharnel (56)"

    // Standard solar zenith angle for official sunrise/sunset (90° 50' accounting for atmospheric refraction)
    private const val ZENITH = 90.83333333333333

    data class SunTimes(
        val dateIso: String,
        val sunriseHour: Int,
        val sunriseMinute: Int,
        val sunsetHour: Int,
        val sunsetMinute: Int,
        val locationName: String = PLOUHARNEL_LOCATION_NAME
    ) {
        val sunriseStr: String
            get() = String.format(Locale.US, "%02d:%02d", sunriseHour, sunriseMinute)

        val sunsetStr: String
            get() = String.format(Locale.US, "%02d:%02d", sunsetHour, sunsetMinute)

        val morningVolStart: String
            get() = sunriseStr

        val morningVolEnd: String
            get() = String.format(Locale.US, "%02d:%02d", (sunriseHour + 2).coerceAtMost(23), sunriseMinute)

        val morningGonflageStart: String
            get() = String.format(Locale.US, "%02d:%02d", (sunriseHour + 1).coerceAtMost(23), sunriseMinute)

        val morningGonflageEnd: String
            get() = String.format(Locale.US, "%02d:%02d", (sunriseHour + 3).coerceAtMost(23), sunriseMinute)

        val eveningGonflageStart: String
            get() = String.format(Locale.US, "%02d:%02d", (sunsetHour - 3).coerceAtLeast(0), sunsetMinute)

        val eveningGonflageEnd: String
            get() = String.format(Locale.US, "%02d:%02d", (sunsetHour - 1).coerceAtLeast(0), sunsetMinute)

        val eveningVolStart: String
            get() = String.format(Locale.US, "%02d:%02d", (sunsetHour - 2).coerceAtLeast(0), sunsetMinute)

        val eveningVolEnd: String
            get() = sunsetStr
    }

    /**
     * Calculates the sunrise and sunset for a given date in format "yyyy-MM-dd" at Plouharnel.
     * Automatically handles Daylight Saving Time (Heure d'été / Heure d'hiver UTC+1 / UTC+2 in France).
     */
    fun calculateSunTimes(
        dateIso: String,
        latitude: Double = PLOUHARNEL_LATITUDE,
        longitude: Double = PLOUHARNEL_LONGITUDE,
        timeZone: TimeZone = TimeZone.getTimeZone("Europe/Paris")
    ): SunTimes {
        val cal = Calendar.getInstance(timeZone, Locale.FRANCE)
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
            sdf.timeZone = timeZone
            val parsedDate = sdf.parse(dateIso)
            if (parsedDate != null) {
                cal.time = parsedDate
            }
        } catch (_: Exception) {}

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        val sunriseUtcHours = computeSunTimeUtc(dayOfYear, latitude, longitude, isSunrise = true)
        val sunsetUtcHours = computeSunTimeUtc(dayOfYear, latitude, longitude, isSunrise = false)

        val (sunriseH, sunriseM) = utcHoursToLocal(cal, sunriseUtcHours, timeZone)
        val (sunsetH, sunsetM) = utcHoursToLocal(cal, sunsetUtcHours, timeZone)

        return SunTimes(
            dateIso = dateIso,
            sunriseHour = sunriseH,
            sunriseMinute = sunriseM,
            sunsetHour = sunsetH,
            sunsetMinute = sunsetM,
            locationName = PLOUHARNEL_LOCATION_NAME
        )
    }

    private fun computeSunTimeUtc(
        dayOfYear: Int,
        lat: Double,
        lng: Double,
        isSunrise: Boolean
    ): Double {
        // 1. Convert longitude to hour value and calculate an approximate time
        val lngHour = lng / 15.0
        val t = if (isSunrise) {
            dayOfYear + ((6.0 - lngHour) / 24.0)
        } else {
            dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        // 2. Calculate Sun's mean anomaly
        val m = (0.9856 * t) - 3.289

        // 3. Calculate Sun's true longitude
        val mRad = Math.toRadians(m)
        var l = m + (1.916 * sin(mRad)) + (0.020 * sin(2 * mRad)) + 282.634
        l = normalizeDegrees(l)

        // 4. Calculate Sun's right ascension
        val lRad = Math.toRadians(l)
        var ra = Math.toDegrees(atan(0.91764 * tan(lRad)))
        ra = normalizeDegrees(ra)

        // Right ascension value needs to be in the same quadrant as L
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0 // Convert to hours

        // 5. Calculate Sun's declination
        val sinDec = 0.39782 * sin(lRad)
        val cosDec = cos(asin(sinDec))

        // 6. Calculate Sun's local hour angle
        val latRad = Math.toRadians(lat)
        val cosZenith = cos(Math.toRadians(ZENITH))
        val cosH = (cosZenith - (sinDec * sin(latRad))) / (cosDec * cos(latRad))

        if (cosH > 1.0) {
            // Polar night (Sun never rises) -> return default reasonable winter hour
            return if (isSunrise) 8.0 else 16.0
        }
        if (cosH < -1.0) {
            // Midnight sun -> return default reasonable summer hour
            return if (isSunrise) 4.0 else 22.0
        }

        val h = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val hHours = h / 15.0

        // 7. Calculate local mean time of rising/setting
        val meanTime = hHours + ra - (0.06571 * t) - 6.622

        // 8. Adjust back to UTC
        var ut = meanTime - lngHour
        while (ut < 0) ut += 24.0
        while (ut >= 24.0) ut -= 24.0

        return ut
    }

    private fun utcHoursToLocal(cal: Calendar, utcDecimalHours: Double, timeZone: TimeZone): Pair<Int, Int> {
        val totalUtcMinutes = (utcDecimalHours * 60.0).roundToInt()
        val utcHour = totalUtcMinutes / 60
        val utcMin = totalUtcMinutes % 60

        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCal.set(Calendar.YEAR, cal.get(Calendar.YEAR))
        utcCal.set(Calendar.MONTH, cal.get(Calendar.MONTH))
        utcCal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
        utcCal.set(Calendar.HOUR_OF_DAY, utcHour)
        utcCal.set(Calendar.MINUTE, utcMin)
        utcCal.set(Calendar.SECOND, 0)
        utcCal.set(Calendar.MILLISECOND, 0)

        // Convert UTC timestamp to Europe/Paris local time (includes daylight savings)
        val localCal = Calendar.getInstance(timeZone)
        localCal.timeInMillis = utcCal.timeInMillis

        return Pair(localCal.get(Calendar.HOUR_OF_DAY), localCal.get(Calendar.MINUTE))
    }

    private fun normalizeDegrees(degrees: Double): Double {
        var d = degrees % 360.0
        if (d < 0) d += 360.0
        return d
    }
}
