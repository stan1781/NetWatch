package com.netwatch.core.system

import android.content.Context
import android.net.TetheringInterface
import android.net.TetheringManager
import android.net.TrafficStats
import com.netwatch.domain.calculator.TrafficPlausibility
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.HotspotSession
import com.netwatch.domain.model.HotspotStatus
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observes tethering through the public [TetheringManager] API.
 * Traffic during a session is estimated from mobile TrafficStats deltas and,
 * when possible, interface counters for the tethered ifaces.
 */
class HotspotMonitor(private val context: Context) {

    data class LiveSession(
        val startedAtMs: Long,
        val interfaces: List<String>,
        var rxBytes: Long = 0,
        var txBytes: Long = 0,
        var lastMobileRx: Long = TrafficStats.getMobileRxBytes(),
        var lastMobileTx: Long = TrafficStats.getMobileTxBytes(),
        val ifaceBaselines: MutableMap<String, Pair<Long, Long>> = mutableMapOf(),
    )

    private val live = AtomicReference<LiveSession?>(null)

    fun currentStatus(): HotspotStatus {
        val session = live.get()
        val tethering = session != null
        return HotspotStatus(
            isTethering = tethering,
            tetheredInterfaces = session?.interfaces.orEmpty(),
            session = session?.toDomain(id = 0, endedAtMs = null, slot = null, sub = null),
            confidence = if (tethering) DataConfidence.ESTIMATED else DataConfidence.REAL,
            sourceLabel = if (tethering) {
                "TetheringManager 状态 + 热点开启期间的移动数据增量（估算）"
            } else {
                "TetheringManager（未检测到共享网络接口）"
            },
            notes = listOf(
                "公开 API 无法给出与运营商账单一致的热点专用计数。",
                "会话流量按热点开启期间默认数据卡的移动数据增量估算；接口计数仅作对照。",
            ),
        )
    }

    fun events(): Flow<List<String>> = callbackFlow {
        val manager = context.getSystemService(TetheringManager::class.java)
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val executor = Executor { it.run() }
        val callback = object : TetheringManager.TetheringEventCallback {
            override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
                val names = interfaces.map { it.getInterface() }.filter { it.isNotBlank() }
                val now = System.currentTimeMillis()
                if (names.isEmpty()) {
                    live.set(null)
                } else {
                    val existing = live.get()
                    if (existing == null) {
                        live.set(
                            LiveSession(startedAtMs = now, interfaces = names).also { session ->
                                names.forEach { iface ->
                                    session.ifaceBaselines[iface] = ifaceBytes(iface)
                                }
                            },
                        )
                    } else {
                        live.set(existing.copy(interfaces = names))
                    }
                }
                trySend(names)
            }
        }
        manager.registerTetheringEventCallback(executor, callback)
        awaitClose { manager.unregisterTetheringEventCallback(callback) }
    }

    /**
     * Adds already-sanitized TrafficStats deltas from the current sample.
     * Callers must not pass a jump that should have been treated as a reset.
     */
    fun addDelta(rxBytes: Long, txBytes: Long) {
        val session = live.get() ?: return
        if (rxBytes > 0L) session.rxBytes += rxBytes
        if (txBytes > 0L) session.txBytes += txBytes
    }

    fun accumulate(mobileRx: Long, mobileTx: Long, elapsedMs: Long = 1000L) {
        val session = live.get() ?: return
        val rxDelta = if (mobileRx >= 0 && session.lastMobileRx >= 0) {
            (mobileRx - session.lastMobileRx).coerceAtLeast(0L)
        } else {
            0L
        }
        val txDelta = if (mobileTx >= 0 && session.lastMobileTx >= 0) {
            (mobileTx - session.lastMobileTx).coerceAtLeast(0L)
        } else {
            0L
        }
        if (!TrafficPlausibility.isImplausibleDelta(rxDelta + txDelta, elapsedMs)) {
            if (rxDelta > 0L) session.rxBytes += rxDelta
            if (txDelta > 0L) session.txBytes += txDelta
        }
        session.lastMobileRx = mobileRx
        session.lastMobileTx = mobileTx
    }

    fun snapshotSession(id: Long, slot: Int?, sub: Int?, ended: Boolean): HotspotSession? {
        val session = live.get() ?: return null
        return session.toDomain(id, if (ended) System.currentTimeMillis() else null, slot, sub)
    }

    fun closeSession(): LiveSession? = live.getAndSet(null)

    private fun LiveSession.toDomain(id: Long, endedAtMs: Long?, slot: Int?, sub: Int?): HotspotSession =
        HotspotSession(
            id = id,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            rxBytes = rxBytes,
            txBytes = txBytes,
            defaultDataSubscriptionId = sub,
            defaultDataSlotIndex = slot,
            interfaces = interfaces,
            confidence = DataConfidence.ESTIMATED,
            notes = listOf(
                "数据来源：热点开启期间 TrafficStats 移动数据增量。",
                if (interfaces.isNotEmpty()) "共享接口：${interfaces.joinToString()}" else "未获得接口名",
            ),
        )

    private fun ifaceBytes(iface: String): Pair<Long, Long> {
        val rx = TrafficStats.getRxBytes(iface)
        val tx = TrafficStats.getTxBytes(iface)
        return rx to tx
    }
}
