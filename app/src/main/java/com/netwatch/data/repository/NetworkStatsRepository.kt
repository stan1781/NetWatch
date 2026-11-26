package com.netwatch.data.repository

import com.netwatch.core.database.AttributedDeltaDao
import com.netwatch.core.database.AttributedDeltaEntity
import com.netwatch.core.database.AttributedSum
import com.netwatch.core.database.DailyUsageDao
import com.netwatch.core.database.DailyUsageEntity
import com.netwatch.core.network.NetworkStatsDataSource
import com.netwatch.core.util.TimeUtils
import com.netwatch.domain.model.AppUsageItem
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.NetworkKind
import com.netwatch.domain.model.SpeedSample
import com.netwatch.domain.model.TrafficPeriod
import java.time.LocalDate

class NetworkStatsRepository(
    private val dataSource: NetworkStatsDataSource,
    private val dailyUsageDao: DailyUsageDao,
    private val attributedDeltaDao: AttributedDeltaDao,
) {
    fun devicePeriod(kind: NetworkKind, startMs: Long, endMs: Long): TrafficPeriod =
        dataSource.queryPeriod(kind, startMs, endMs)

    fun appUsage(kind: NetworkKind, startMs: Long, endMs: Long): List<AppUsageItem> =
        dataSource.queryAppUsage(kind, startMs, endMs)

    fun probe(): String = dataSource.probe()

    suspend fun recordAttributedDelta(sample: SpeedSample) {
        if (sample.baselineReset) return
        val mobile = sample.mobileDelta
        if (mobile.total <= 0L) return
        val identity = sample.defaultDataSubscriptionId?.let { "sub:$it" }
            ?: sample.defaultDataSlotIndex?.let { "slot:$it:unknown" }
            ?: "unknown"
        attributedDeltaDao.insert(
            AttributedDeltaEntity(
                timestampMs = sample.timestampMs,
                dateEpochDay = TimeUtils.dateOf(sample.timestampMs).toEpochDay(),
                subscriptionId = sample.defaultDataSubscriptionId,
                slotIndex = sample.defaultDataSlotIndex,
                identityKey = identity,
                rxBytes = mobile.rxBytes,
                txBytes = mobile.txBytes,
                confidence = DataConfidence.ESTIMATED.name,
            ),
        )
    }

    suspend fun attributedUsage(identityKey: String, startMs: Long, endMs: Long): ByteVolume? {
        val sum: AttributedSum = attributedDeltaDao.sumForIdentity(identityKey, startMs, endMs) ?: return null
        return ByteVolume(sum.rxBytes, sum.txBytes)
    }

    suspend fun attributedUsageByIdentity(startMs: Long, endMs: Long): List<AttributedSum> =
        attributedDeltaDao.sumAll(startMs, endMs)

    suspend fun syncOfficialDaily(date: LocalDate) {
        val start = TimeUtils.startOfDay(date)
        val end = TimeUtils.endOfDayExclusive(date)
        val now = System.currentTimeMillis()
        for (kind in listOf(NetworkKind.MOBILE, NetworkKind.WIFI, NetworkKind.TOTAL)) {
            val period = dataSource.queryPeriod(kind, start, end)
            if (period.confidence == DataConfidence.UNAVAILABLE) continue
            val existing = dailyUsageDao.find(date.toEpochDay(), kind.name, DEVICE_WIDE_KEY)
            val entity = DailyUsageEntity(
                id = existing?.id ?: 0,
                dateEpochDay = date.toEpochDay(),
                kind = kind.name,
                scope = DataScope.DEVICE_WIDE.name,
                confidence = period.confidence.name,
                identityKey = DEVICE_WIDE_KEY,
                subscriptionId = null,
                slotIndex = null,
                rxBytes = period.volume.rxBytes,
                txBytes = period.volume.txBytes,
                updatedAtMs = now,
                notes = period.notes.joinToString("；"),
            )
            dailyUsageDao.upsert(entity)
        }
    }

    suspend fun officialDaily(start: LocalDate, endInclusive: LocalDate): List<DailyUsageEntity> =
        dailyUsageDao.getRange(start.toEpochDay(), endInclusive.toEpochDay())

    fun observeOfficialDaily(start: LocalDate, endInclusive: LocalDate) =
        dailyUsageDao.observeRange(start.toEpochDay(), endInclusive.toEpochDay())

    companion object {
        const val DEVICE_WIDE_KEY = "device"
    }
}
