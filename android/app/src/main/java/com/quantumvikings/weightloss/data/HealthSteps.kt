package com.quantumvikings.weightloss.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

/** Reads the day's steps from Health Connect (where Samsung Health and Google Fit put them). */
class HealthSteps(private val context: Context) {
    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    /** Usable now; false when Health Connect is missing or needs an update. */
    val available: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect is an app on Android 13 and lower; it can be installed or updated from Play. */
    val installable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun installIntent() = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"),
    ).setPackage("com.android.vending")

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Boolean =
        available && client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Total steps on [day] in the phone's time zone; null when Health Connect has none. */
    suspend fun steps(day: LocalDate): Long? {
        val zone = ZoneId.systemDefault()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    day.atStartOfDay(zone).toInstant(),
                    day.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        return result[StepsRecord.COUNT_TOTAL]
    }
}
