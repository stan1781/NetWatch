package com.netwatch.core.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.netwatch.core.permissions.PermissionChecker
import com.netwatch.domain.model.AppUsageItem
import com.netwatch.domain.model.ByteVolume
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope
import com.netwatch.domain.model.NetworkKind
import com.netwatch.domain.model.TrafficPeriod

/**
 * Thin wrapper around the public [NetworkStatsManager] API.
 *
 * Third-party apps cannot supply a subscriber id on modern Android, so every
 * query is device-wide (or per-UID device-wide). Results are never split
 * into fake per-SIM numbers.
 */
class NetworkStatsDataSource(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
) {
    private val manager: NetworkStatsManager? =
        context.getSystemService(NetworkStatsManager::class.java)

    data class QueryResult(
        val volume: ByteVolume?,
        val confidence: DataConfidence,
        val error: String?,
    )

    fun queryDevice(kind: NetworkKind, startMs: Long, endMs: Long): QueryResult {
        if (!permissionChecker.hasUsageAccess()) {
            return QueryResult(null, DataConfidence.UNAVAILABLE, "未授予使用情况访问权限")
        }
        val service = manager ?: return QueryResult(null, DataConfidence.UNAVAILABLE, "NetworkStatsManager 不可用")
        val type = when (kind) {
            NetworkKind.MOBILE -> ConnectivityManager.TYPE_MOBILE
            NetworkKind.WIFI -> ConnectivityManager.TYPE_WIFI
            NetworkKind.TOTAL -> return queryTotal(startMs, endMs)
        }
        return try {
            val bucket = service.querySummaryForDevice(type, null, startMs, endMs)
            QueryResult(
                volume = ByteVolume(bucket.rxBytes, bucket.txBytes),
                confidence = DataConfidence.REAL,
                error = null,
            )
        } catch (se: SecurityException) {
            QueryResult(null, DataConfidence.UNAVAILABLE, "权限不足：${se.message}")
        } catch (t: Throwable) {
            QueryResult(null, DataConfidence.UNAVAILABLE, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun queryTotal(startMs: Long, endMs: Long): QueryResult {
        val mobile = queryDevice(NetworkKind.MOBILE, startMs, endMs)
        val wifi = queryDevice(NetworkKind.WIFI, startMs, endMs)
        if (mobile.volume == null && wifi.volume == null) {
            return QueryResult(null, DataConfidence.UNAVAILABLE, mobile.error ?: wifi.error)
        }
        val volume = ByteVolume(
            (mobile.volume?.rxBytes ?: 0) + (wifi.volume?.rxBytes ?: 0),
            (mobile.volume?.txBytes ?: 0) + (wifi.volume?.txBytes ?: 0),
        )
        val confidence = when {
            mobile.volume != null && wifi.volume != null -> DataConfidence.REAL
            else -> DataConfidence.ESTIMATED
        }
        return QueryResult(volume, confidence, mobile.error ?: wifi.error)
    }

    fun queryPeriod(kind: NetworkKind, startMs: Long, endMs: Long): TrafficPeriod {
        val result = queryDevice(kind, startMs, endMs)
        return TrafficPeriod(
            startMs = startMs,
            endMs = endMs,
            volume = result.volume ?: ByteVolume(0, 0),
            kind = kind,
            scope = DataScope.DEVICE_WIDE,
            confidence = result.confidence,
            notes = listOfNotNull(
                result.error,
                if (result.confidence == DataConfidence.REAL) "来源：NetworkStatsManager 全设备统计" else null,
            ),
        )
    }

    fun queryAppUsage(kind: NetworkKind, startMs: Long, endMs: Long): List<AppUsageItem> {
        if (!permissionChecker.hasUsageAccess()) return emptyList()
        val service = manager ?: return emptyList()
        if (kind == NetworkKind.TOTAL) {
            return mergeByUid(
                queryAppUsage(NetworkKind.MOBILE, startMs, endMs),
                queryAppUsage(NetworkKind.WIFI, startMs, endMs),
            )
        }
        val type = when (kind) {
            NetworkKind.MOBILE -> ConnectivityManager.TYPE_MOBILE
            NetworkKind.WIFI -> ConnectivityManager.TYPE_WIFI
            NetworkKind.TOTAL -> return emptyList()
        }
        val totals = mutableMapOf<Int, ByteVolume>()
        try {
            service.querySummary(type, null, startMs, endMs).use { stats ->
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid == NetworkStats.Bucket.UID_ALL || uid == NetworkStats.Bucket.UID_REMOVED ||
                        uid == NetworkStats.Bucket.UID_TETHERING
                    ) {
                        // UID_TETHERING is recorded separately if the system exposes it.
                    }
                    val previous = totals[uid] ?: ByteVolume(0, 0)
                    totals[uid] = ByteVolume(
                        previous.rxBytes + bucket.rxBytes,
                        previous.txBytes + bucket.txBytes,
                    )
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }
        val pm = context.packageManager
        return totals.map { (uid, volume) ->
            val packages = pm.getPackagesForUid(uid)
            val packageName = packages?.firstOrNull()
            val label = resolveLabel(uid, packageName)
            AppUsageItem(
                uid = uid,
                packageName = packageName,
                label = label,
                rxBytes = volume.rxBytes,
                txBytes = volume.txBytes,
                kind = kind,
                confidence = DataConfidence.REAL,
            )
        }.sortedByDescending { it.total }
    }

    fun probe(): String {
        if (!permissionChecker.hasUsageAccess()) return "使用情况访问权限未授予，无法调用 NetworkStatsManager。"
        val now = System.currentTimeMillis()
        val result = queryDevice(NetworkKind.MOBILE, now - 3_600_000L, now)
        return if (result.volume != null) {
            "NetworkStatsManager 查询成功（近 1 小时全设备移动数据 rx=${result.volume.rxBytes} tx=${result.volume.txBytes}）。"
        } else {
            "NetworkStatsManager 查询失败：${result.error}"
        }
    }

    private fun resolveLabel(uid: Int, packageName: String?): String {
        if (uid == NetworkStats.Bucket.UID_TETHERING) return "系统热点（UID_TETHERING）"
        if (uid == NetworkStats.Bucket.UID_REMOVED) return "已卸载应用"
        if (packageName == null) return "系统或未知应用 (uid:$uid)"
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun mergeByUid(a: List<AppUsageItem>, b: List<AppUsageItem>): List<AppUsageItem> {
        val map = LinkedHashMap<Int, AppUsageItem>()
        (a + b).forEach { item ->
            val existing = map[item.uid]
            map[item.uid] = if (existing == null) {
                item.copy(kind = NetworkKind.TOTAL)
            } else {
                existing.copy(
                    rxBytes = existing.rxBytes + item.rxBytes,
                    txBytes = existing.txBytes + item.txBytes,
                    kind = NetworkKind.TOTAL,
                )
            }
        }
        return map.values.sortedByDescending { it.total }
    }
}
