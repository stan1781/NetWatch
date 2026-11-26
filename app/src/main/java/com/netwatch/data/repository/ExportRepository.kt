package com.netwatch.data.repository

import android.content.Context
import com.netwatch.core.database.AlertEventDao
import com.netwatch.core.database.DailyUsageDao
import com.netwatch.core.database.HotspotSessionDao
import com.netwatch.core.database.NetWatchDatabase
import com.netwatch.core.database.SimChangeEventDao
import com.netwatch.core.util.TimeUtils
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ExportRepository(
    private val context: Context,
    private val dailyUsageDao: DailyUsageDao,
    private val alertEventDao: AlertEventDao,
    private val hotspotSessionDao: HotspotSessionDao,
    private val database: NetWatchDatabase,
    private val simChangeEventDao: SimChangeEventDao,
) {
    suspend fun exportJson(): File {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "netwatch-export-${TimeUtils.today()}.json")
        val daily = dailyUsageDao.getRange(0, Long.MAX_VALUE)
        val alerts = alertEventDao.getAll()
        val sessions = hotspotSessionDao.getAll()
        val json = buildJsonObject {
            put("exportedAt", TimeUtils.formatDateTime(System.currentTimeMillis()))
            put(
                "dailyUsage",
                buildJsonArray {
                    daily.forEach { row ->
                        add(
                            buildJsonObject {
                                put("dateEpochDay", row.dateEpochDay)
                                put("kind", row.kind)
                                put("scope", row.scope)
                                put("confidence", row.confidence)
                                put("identityKey", row.identityKey)
                                put("subscriptionId", row.subscriptionId ?: -1)
                                put("slotIndex", row.slotIndex ?: -1)
                                put("rxBytes", row.rxBytes)
                                put("txBytes", row.txBytes)
                            },
                        )
                    }
                },
            )
            put(
                "alerts",
                buildJsonArray {
                    alerts.forEach { row ->
                        add(
                            buildJsonObject {
                                put("type", row.type)
                                put("triggeredAtMs", row.triggeredAtMs)
                                put("triggerSpeedBytesPerSec", row.triggerSpeedBytesPerSec ?: -1)
                                put("triggerVolumeBytes", row.triggerVolumeBytes ?: -1)
                                put("slotIndex", row.slotIndex ?: -1)
                                put("message", row.message)
                            },
                        )
                    }
                },
            )
            put(
                "hotspotSessions",
                buildJsonArray {
                    sessions.forEach { row ->
                        add(
                            buildJsonObject {
                                put("startedAtMs", row.startedAtMs)
                                put("endedAtMs", row.endedAtMs ?: -1)
                                put("rxBytes", row.rxBytes)
                                put("txBytes", row.txBytes)
                                put("interfaces", row.interfaces)
                                put("confidence", row.confidence)
                            },
                        )
                    }
                },
            )
        }
        file.writeText(Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), json))
        return file
    }

    suspend fun clearAll() {
        dailyUsageDao.clear()
        alertEventDao.clear()
        hotspotSessionDao.clear()
        simChangeEventDao.clear()
        database.attributedDeltaDao().clear()
        database.simIdentityDao().clear()
    }
}
