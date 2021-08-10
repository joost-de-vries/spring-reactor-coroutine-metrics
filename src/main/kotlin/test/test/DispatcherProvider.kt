@file:Suppress("INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE", "INVISIBLE_MEMBER")

package test.test

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.SchedulerCoroutineDispatcher
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.scheduling.CoroutineScheduler
import kotlinx.coroutines.scheduling.ExperimentalCoroutineDispatcher
import metrics.ControlState
import metrics.CoroutineSchedulerMetrics
import metrics.LimitingDispatcherMetrics
import org.slf4j.LoggerFactory
import reactor.core.scheduler.Schedulers

interface DispatcherProvider {
    val bounded: SchedulerCoroutineDispatcher
    val unconfined: CoroutineDispatcher
    val parallel: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}
private val log = LoggerFactory.getLogger(DispatcherProvider::class.java)

fun dispatcherProvider(meterRegistry: MeterRegistry) = object : DispatcherProvider {
    override val bounded: SchedulerCoroutineDispatcher by lazy { Schedulers.boundedElastic().asCoroutineDispatcher() }
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined

    override val parallel: CoroutineDispatcher by lazy { Schedulers.parallel().asCoroutineDispatcher() }

    override val default: CoroutineDispatcher =
            Dispatchers.Default.also {
               // meterRegistry.monitorAsExecutor(it, "Dispatchers.Default")
                meterRegistry.monitorAsDispatcher(it, "Dispatchers.Default")
            }

    override val io: CoroutineDispatcher =
        Dispatchers.IO.also {
            //meterRegistry.monitorAsExecutor(it, "Dispatchers.IO")
            meterRegistry.monitorAsDispatcher(it, "Dispatchers.IO")
        }

}

// until this lands https://github.com/Kotlin/kotlinx.coroutines/issues/1360
private fun MeterRegistry.monitorAsExecutor(dispatcher: CoroutineDispatcher, name: String) {
    ExecutorServiceMetrics.monitor(this, dispatcher.asExecutor(), name, Tag.of("service", "chk"))
}

// workaround as described in https://github.com/Kotlin/kotlinx.coroutines/issues/1360
private fun MeterRegistry.monitorAsDispatcher(dispatcher: CoroutineDispatcher, name: String) {
    val isLimitingDispatcher = dispatcher::class.java.name == "kotlinx.coroutines.scheduling.LimitingDispatcher"
    val scheduler = dispatcher.coroutineScheduler()
    if(isLimitingDispatcher){
        log.info("monitoring as LimitingDispatcher $name")
        monitorLimitingDispatcher(dispatcher,name)

//        dispatcher.coroutineSchedulerFromLimitingDispatcher()?.let {
//            monitorCoroutineScheduler(it, name)
//        }

    } else if (scheduler !== null) {
        log.info("monitoring as CoroutineScheduler $name ${dispatcher::class.java.name}")
        monitorCoroutineScheduler(scheduler, "CoroutineScheduler")
    } else {
        log.warn("Could not determine scheduler for coroutine dispatcher ${dispatcher.toString()} ${dispatcher::class.java.name}")
    }
}
private fun MeterRegistry.monitorCoroutineScheduler(coroutineScheduler: CoroutineScheduler, name: String) =
        CoroutineSchedulerMetrics(coroutineScheduler, listOf(Tag.of("name", name))).also {
            it.bindTo(this)
        }

private fun MeterRegistry.monitorLimitingDispatcher(limitingDispatcher: CoroutineDispatcher, name: String) =
        LimitingDispatcherMetrics(limitingDispatcher, listOf(Tag.of("name", name))).also {
            it.bindTo(this)
        }

@Suppress("INVISIBLE_REFERENCE")
@OptIn(InternalCoroutinesApi::class)
internal fun CoroutineDispatcher.coroutineScheduler(): CoroutineScheduler? =
        (this as? ExperimentalCoroutineDispatcher)?.let {
            it.executor as? CoroutineScheduler
        }

@OptIn(InternalCoroutinesApi::class)
@Suppress("INVISIBLE_REFERENCE")
private fun CoroutineDispatcher.coroutineSchedulerFromLimitingDispatcher() =
        (this as? ExecutorCoroutineDispatcher)?.let {
            (it.executor as? ExperimentalCoroutineDispatcher)?.let {
                it.executor as? CoroutineScheduler
            }
        }