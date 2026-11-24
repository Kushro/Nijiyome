// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import app.nijiyome.model.ListSyncReport
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.RequestPriority
import app.nijiyome.model.RequestPriorityElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals

/** Hito 6 (D1, social-cuentas-listas-5): the desktop push of the list queue is background traffic, as Android's worker. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopListSyncTest {
    /** A ListsRepository + ListsSyncControl whose pushPending records the priority it ran with; anything else is not used. */
    private fun fakeLists(priorities: MutableList<RequestPriority?>, pending: Int): ListsRepository {
        val pendingFlow = MutableStateFlow(pending)
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ListsRepository::class.java, ListsSyncControl::class.java)) { _, method, args ->
            when (method.name) {
                "pushPending" -> {
                    val continuation = args.last() as Continuation<*>
                    priorities += continuation.context[RequestPriorityElement]?.priority
                    ListSyncReport(pushed = 0, pulled = 0, conflicts = 0, failed = if (pendingFlow.value > 0) 1 else 0)
                }
                "getPendingChanges" -> pendingFlow
                "hashCode" -> 0
                "equals" -> false
                "toString" -> "FakeLists"
                else -> error("not used: ${method.name}")
            }
        } as ListsRepository
    }

    @Test
    fun pushesRunAsBackgroundTrafficAndRetryWhileChangesRemain() = runTest {
        val priorities = mutableListOf<RequestPriority?>()
        val lists = fakeLists(priorities, pending = 2)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        DesktopListSyncScheduler(scope, { lists }, firstDelayMs = 10, backoffMs = 10, maxAttempts = 3).requestPush()
        advanceUntilIdle()
        assertEquals(listOf<RequestPriority?>(RequestPriority.BACKGROUND, RequestPriority.BACKGROUND, RequestPriority.BACKGROUND), priorities)
    }

    @Test
    fun aPushThatLeavesNothingStops() = runTest {
        val priorities = mutableListOf<RequestPriority?>()
        val lists = fakeLists(priorities, pending = 0)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        DesktopListSyncScheduler(scope, { lists }, firstDelayMs = 10, backoffMs = 10, maxAttempts = 3).requestPush()
        advanceUntilIdle()
        assertEquals(listOf<RequestPriority?>(RequestPriority.BACKGROUND), priorities)
    }
}
