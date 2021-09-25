package metrics

import io.micrometer.core.instrument.search.Search
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CoroutineDispatcherMetricsTest {
    @Test
    fun `test parsing of ControlState`() {
        val controlState = Dispatchers.Default.coroutineScheduler()?.controlStateMetrics()
        assertNotNull(controlState)
    }

    @Test
    fun `test getting LimitingDispatcher queue size`() {
        val controlState = Dispatchers.IO.coroutineScheduler()?.controlStateMetrics()
        assertNotNull(controlState)
        val queueSize = Dispatchers.IO.asLimitingDispatcher()?.queueSize()
        assertNotNull(queueSize)
    }

    @Test
    fun `test DispatchersDefault gauges`() = runBlocking<Unit> {
        val name = "CoroutineScheduler"
        val prefix = "coroutine."
        val dispatcher = Dispatchers.Default
        val meterRegistry = SimpleMeterRegistry()
        meterRegistry.tryMonitorCoroutineScheduler(dispatcher, name, prefix)
        runWorkOn(dispatcher)
        val search = Search.`in`(meterRegistry).name { it.startsWith(prefix) }
        val gauges = search.gauges()
        assertEquals(4, gauges.size)
    }

    @Test
    fun `test DispatchersIO gauges`() = runBlocking<Unit> {
        val prefix = "coroutine."
        val coroutineSchedulerName = "CoroutineScheduler"
        val limitingDispatcherName = "LimitingDispatcher"
        val dispatcher = Dispatchers.IO
        val meterRegistry = SimpleMeterRegistry()
        meterRegistry.tryMonitorCoroutineScheduler(dispatcher, coroutineSchedulerName, prefix)
        meterRegistry.tryMonitorLimitingDispatcher(dispatcher, limitingDispatcherName, prefix)
        runWorkOn(dispatcher)
        val coroutineSchedulerSearch = Search.`in`(meterRegistry).tag("name", coroutineSchedulerName)
        val coroutineSchedulerGauges = coroutineSchedulerSearch.gauges()
        assertEquals(4, coroutineSchedulerGauges.size)
        val limitingDispatcherSearch = Search.`in`(meterRegistry).tag("name", limitingDispatcherName)
        val limitingDispatcherGauges = limitingDispatcherSearch.gauges()
        assertEquals(1, limitingDispatcherGauges.size)
    }

    private suspend fun runWorkOn(dispatcher: CoroutineDispatcher) = withContext(dispatcher) {
        (0..5).map {
            coroutineScope {
                async {
                    val squared = it * it
                    delay(10)
                    squared.toString()
                }
            }
        }.awaitAll()
    }
}
