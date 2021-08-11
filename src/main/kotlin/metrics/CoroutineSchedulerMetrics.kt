@file:Suppress("INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE", "INVISIBLE_MEMBER")
package metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.BaseUnits
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.scheduling.CoroutineScheduler
import kotlinx.coroutines.scheduling.ExperimentalCoroutineDispatcher
import org.slf4j.LoggerFactory
import java.lang.reflect.Field

@Suppress("INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE")
class CoroutineSchedulerMetrics(
        private val coroutineScheduler: CoroutineScheduler,
        private val tags: Iterable<Tag> = listOf(),
        private val metricPrefix: String = ""
) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
//
//        FunctionCounter.builder(metricPrefix + "executor.completed", coroutineScheduler, ToDoubleFunction { obj -> obj .toDouble() })
//                .tags(tags)
//                .description("The approximate total number of tasks that have completed execution")
//                .baseUnit(BaseUnits.TASKS)
//                .register(registry)
//
//        Gauge.builder(metricPrefix + "executor.active", coroutineScheduler, ToDoubleFunction { obj -> obj.activeCount.toDouble() })
//                .tags(tags)
//                .description("The approximate number of threads that are actively executing tasks")
//                .baseUnit(BaseUnits.THREADS)
//                .register(registry)
//

        Gauge.builder(metricPrefix + "executor.queued", coroutineScheduler){ it.controlStateMetrics()?.blockingTasks?.toDouble()?:0.0}
                .tags(tags)
                .description("The approximate number of tasks that are queued for execution")
                .baseUnit(BaseUnits.TASKS)
                .register(registry)
//
//        Gauge.builder(metricPrefix + "executor.queue.remaining", coroutineScheduler, ToDoubleFunction { tpRef -> tpRef.queue.remainingCapacity().toDouble() })
//                .tags(tags)
//                .description("The number of additional elements that this queue can ideally accept without blocking")
//                .baseUnit(BaseUnits.TASKS)
//                .register(registry)
//
        Gauge.builder(metricPrefix + "executor.pool.size", coroutineScheduler) { it.controlStateMetrics()?.createdWorkers?.toDouble()?:0.0 }
                .tags(tags)
                .description("The current number of threads in the pool")
                .baseUnit(BaseUnits.THREADS)
                .register(registry)
//
        Gauge.builder(metricPrefix + "executor.pool.core", coroutineScheduler) { it.corePoolSize.toDouble() }
                .tags(tags)
                .description("The core number of threads for the pool")
                .baseUnit(BaseUnits.THREADS)
                .register(registry)
//
        Gauge.builder(metricPrefix + "executor.pool.max", coroutineScheduler) { it.maxPoolSize.toDouble() }
                .tags(tags)
                .description("The maximum allowed number of threads in the pool")
                .baseUnit(BaseUnits.THREADS)
                .register(registry)
    }
}

private val log = LoggerFactory.getLogger(CoroutineSchedulerMetrics::class.java)

// until this lands https://github.com/Kotlin/kotlinx.coroutines/issues/1360
fun MeterRegistry.tryMonitorCoroutineScheduler(coroutineDispatcher: CoroutineDispatcher, name: String, metricPrefix: String = "") {
    coroutineDispatcher.coroutineScheduler()?.let {
        log.info("Monitoring CoroutineScheduler metrics")
        monitorCoroutineScheduler(it, name, metricPrefix)
    } ?: log.warn("Failed to monitor CoroutineScheduler metrics for ${coroutineDispatcher::class.java.name}")
}

private fun MeterRegistry.monitorCoroutineScheduler(coroutineScheduler: CoroutineScheduler, name: String, metricPrefix: String = "") =
    CoroutineSchedulerMetrics(coroutineScheduler, listOf(Tag.of("name", name)), metricPrefix).also {
        it.bindTo(this)
    }

@Suppress("INVISIBLE_REFERENCE")
internal fun CoroutineDispatcher.coroutineScheduler(): CoroutineScheduler? =
    experimentalCoroutineDispatcherCoroutineScheduler()
        ?: limitingDispatcherCoroutineScheduler()

@Suppress("INVISIBLE_REFERENCE")
internal fun CoroutineDispatcher.experimentalCoroutineDispatcherCoroutineScheduler(): CoroutineScheduler? =
    (this as? ExperimentalCoroutineDispatcher)?.let {
        it.executor as? CoroutineScheduler
    }
