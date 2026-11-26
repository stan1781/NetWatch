package com.netwatch.core.system

import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.netwatch.core.database.SimChangeEventDao
import com.netwatch.core.database.SimChangeEventEntity
import com.netwatch.core.database.SimIdentityDao
import com.netwatch.core.database.SimIdentityEntity
import com.netwatch.core.permissions.PermissionChecker
import com.netwatch.domain.model.SimCard
import com.netwatch.domain.model.SimIdentificationStatus
import com.netwatch.domain.model.SimSnapshot
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Dual-SIM observation using only public APIs.
 *
 * slotIndex and subscriptionId are always kept distinct.
 * IMSI and phone numbers are never read.
 */
class SimManager(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val identityDao: SimIdentityDao,
    private val changeDao: SimChangeEventDao,
) {
    private val subscriptionManager =
        context.getSystemService(SubscriptionManager::class.java)
    private val telephonyManager =
        context.getSystemService(TelephonyManager::class.java)

    private val lastSnapshot = AtomicReference<SimSnapshot?>(null)

    fun current(): SimSnapshot {
        val now = System.currentTimeMillis()
        val defaultSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        val permission = permissionChecker.hasReadPhoneState()
        val mobileConnected = isMobileConnected()

        if (subscriptionManager == null) {
            return SimSnapshot(emptyList(), defaultSubId, null, mobileConnected, permission, now)
        }

        val infos: List<SubscriptionInfo> = try {
            if (permission) {
                subscriptionManager.activeSubscriptionInfoList.orEmpty()
            } else {
                emptyList()
            }
        } catch (_: SecurityException) {
            emptyList()
        }

        val maxSlots = try {
            subscriptionManager.activeSubscriptionInfoCountMax.coerceAtLeast(2)
        } catch (_: Exception) {
            2
        }.coerceAtMost(4)

        val bySlot = infos.associateBy { it.simSlotIndex }
        val cards = (0 until maxSlots.coerceAtLeast(2)).map { slot ->
            val info = bySlot[slot]
            when {
                info != null -> SimCard(
                    slotIndex = slot,
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString()?.ifBlank { null },
                    carrierName = info.carrierName?.toString()?.ifBlank { null },
                    isEmbedded = info.isEmbedded,
                    isActive = true,
                    isDefaultData = defaultSubId != null && info.subscriptionId == defaultSubId,
                    identificationStatus = SimIdentificationStatus.IDENTIFIED,
                )
                !permission -> SimCard(
                    slotIndex = slot,
                    subscriptionId = null,
                    displayName = null,
                    carrierName = null,
                    isEmbedded = false,
                    isActive = false,
                    isDefaultData = false,
                    identificationStatus = SimIdentificationStatus.PERMISSION_REQUIRED,
                )
                else -> SimCard(
                    slotIndex = slot,
                    subscriptionId = null,
                    displayName = null,
                    carrierName = null,
                    isEmbedded = false,
                    isActive = false,
                    isDefaultData = defaultSubId == null && slot == 0,
                    identificationStatus = SimIdentificationStatus.ABSENT,
                )
            }
        }

        val defaultSlot = cards.firstOrNull { it.isDefaultData }?.slotIndex
            ?: cards.firstOrNull { it.subscriptionId == defaultSubId }?.slotIndex

        return SimSnapshot(
            cards = cards,
            defaultDataSubscriptionId = defaultSubId,
            defaultDataSlotIndex = defaultSlot,
            mobileConnected = mobileConnected,
            permissionGranted = permission,
            capturedAtMs = now,
        )
    }

    fun snapshots(): Flow<SimSnapshot> = callbackFlow {
        trySend(current())
        val manager = subscriptionManager
        if (manager == null) {
            awaitClose { }
            return@callbackFlow
        }
        val executor = Executor { it.run() }
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                val snapshot = current()
                trySend(snapshot)
                launch { persistIdentityChanges(snapshot) }
            }
        }
        manager.addOnSubscriptionsChangedListener(executor, listener)

        val tm = telephonyManager
        val callback = if (tm != null) {
            object : TelephonyCallback(), TelephonyCallback.DataConnectionStateListener {
                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    trySend(current())
                }
            }.also { tm.registerTelephonyCallback(executor, it) }
        } else {
            null
        }

        awaitClose {
            manager.removeOnSubscriptionsChangedListener(listener)
            if (tm != null && callback != null) {
                tm.unregisterTelephonyCallback(callback)
            }
        }
    }.distinctUntilChanged()

    suspend fun persistIdentityChanges(snapshot: SimSnapshot) {
        val previous = lastSnapshot.getAndSet(snapshot)
        for (card in snapshot.cards) {
            if (card.subscriptionId == null) continue
            val existing = identityDao.forSlot(card.slotIndex)
            val current = existing.firstOrNull { it.identityKey == card.identityKey }
            if (current == null) {
                identityDao.upsert(
                    SimIdentityEntity(
                        identityKey = card.identityKey,
                        slotIndex = card.slotIndex,
                        subscriptionId = card.subscriptionId,
                        carrierName = card.carrierName,
                        displayName = card.displayName,
                        firstSeenAtMs = snapshot.capturedAtMs,
                        lastSeenAtMs = snapshot.capturedAtMs,
                        replaced = false,
                    ),
                )
                val previousActive = existing.filter { !it.replaced && it.identityKey != card.identityKey }
                previousActive.forEach { old ->
                    identityDao.upsert(old.copy(replaced = true, lastSeenAtMs = snapshot.capturedAtMs))
                    changeDao.insert(
                        SimChangeEventEntity(
                            timestampMs = snapshot.capturedAtMs,
                            type = "SIM_REPLACED",
                            slotIndex = card.slotIndex,
                            oldSubscriptionId = old.subscriptionId,
                            newSubscriptionId = card.subscriptionId,
                            detail = "卡槽 ${card.slotIndex + 1} 的订阅已变化，旧卡历史不会归到新卡。",
                        ),
                    )
                }
            } else {
                identityDao.upsert(
                    current.copy(
                        carrierName = card.carrierName,
                        displayName = card.displayName,
                        lastSeenAtMs = snapshot.capturedAtMs,
                    ),
                )
            }
        }
        if (previous != null &&
            previous.defaultDataSubscriptionId != snapshot.defaultDataSubscriptionId
        ) {
            changeDao.insert(
                SimChangeEventEntity(
                    timestampMs = snapshot.capturedAtMs,
                    type = "DEFAULT_DATA_SWITCHED",
                    slotIndex = snapshot.defaultDataSlotIndex,
                    oldSubscriptionId = previous.defaultDataSubscriptionId,
                    newSubscriptionId = snapshot.defaultDataSubscriptionId,
                    detail = "默认数据卡已切换，速度采样基线将重置。",
                ),
            )
        }
    }

    private fun isMobileConnected(): Boolean {
        val tm = telephonyManager ?: return false
        return try {
            tm.dataState == TelephonyManager.DATA_CONNECTED
        } catch (_: SecurityException) {
            false
        }
    }
}
