@file:Suppress("INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE", "INVISIBLE_MEMBER")
package metrics
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.BaseUnits
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.scheduling.CoroutineScheduler
import kotlinx.coroutines.scheduling.ExperimentalCoroutineDispatcher
import kotlinx.coroutines.scheduling.LimitingDispatcher
import org.slf4j.LoggerFactory
import test.test.DispatcherProvider
import java.util.concurrent.ConcurrentLinkedQueue

class LimitingDispatcherMetrics(
        val limitingDispatcher: CoroutineDispatcher,
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

        Gauge.builder(metricPrefix + "executor.queued", limitingDispatcher){ it.queueSize()?.toDouble()?:0.0}
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
//        Gauge.builder(metricPrefix + "executor.pool.size", limitingDispatcher) { it.controlStateMetrics()?.createdWorkers?.toDouble()?:0.0 }
//                .tags(tags)
//                .description("The current number of threads in the pool")
//                .baseUnit(BaseUnits.THREADS)
//                .register(registry)
//
//        Gauge.builder(metricPrefix + "executor.pool.core", limitingDispatcher) { it.corePoolSize.toDouble() }
//                .tags(tags)
//                .description("The core number of threads for the pool")
//                .baseUnit(BaseUnits.THREADS)
//                .register(registry)
//
//        Gauge.builder(metricPrefix + "executor.pool.max", limitingDispatcher) { obj -> obj.maxPoolSize.toDouble() }
//                .tags(tags)
//                .description("The maximum allowed number of threads in the pool")
//                .baseUnit(BaseUnits.THREADS)
//                .register(registry)
    }
}
private val log = LoggerFactory.getLogger(LimitingDispatcherMetrics::class.java)

internal fun CoroutineDispatcher.queueSize() = kotlin.runCatching {
    this.declaredFieldValue<ConcurrentLinkedQueue<Runnable>>("queue").size
}.let {
    if(it.isFailure){
        log.error("Could not get LimitingDispatcher.queue", it.exceptionOrNull())
        null
    }else{
        it.getOrNull()
    }
}

// until this lands https://github.com/Kotlin/kotlinx.coroutines/issues/1360
fun MeterRegistry.tryMonitorLimitingDispatcher(coroutineDispatcher: CoroutineDispatcher, name: String, metricPrefix: String) {
    coroutineDispatcher.asLimitingDispatcher()?.let{
        log.info("Monitoring LimitingDispatcher metrics")
        monitorLimitingDispatcher(coroutineDispatcher, name, metricPrefix)
    } ?: log.warn("Failed to monitor LimitingDispatcher metrics for ${coroutineDispatcher::class.java.name}")
}

internal fun CoroutineDispatcher.asLimitingDispatcher() =
    this.takeIf {
        this::class.java.name == "kotlinx.coroutines.scheduling.LimitingDispatcher"
    }

private fun MeterRegistry.monitorLimitingDispatcher(limitingDispatcher: CoroutineDispatcher, name: String, metricPrefix: String) =
    LimitingDispatcherMetrics(limitingDispatcher, listOf(Tag.of("name", name)), metricPrefix).also {
        it.bindTo(this)
    }

@Suppress("INVISIBLE_REFERENCE")
internal fun CoroutineDispatcher.limitingDispatcherCoroutineScheduler(): CoroutineScheduler? =
    this.asLimitingDispatcher()?.let {
        this.declaredFieldValue<ExperimentalCoroutineDispatcher>("dispatcher").experimentalCoroutineDispatcherCoroutineScheduler()
    }
