package test.test

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.SchedulerCoroutineDispatcher
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import metrics.tryMonitorCoroutineScheduler
import metrics.tryMonitorLimitingDispatcher
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
    init {
        meterRegistry.tryMonitorLimitingDispatcher(Dispatchers.IO, "LimitingDispatcher", "coroutine.")
        meterRegistry.tryMonitorCoroutineScheduler(Dispatchers.Default, "CoroutineScheduler", "coroutine.")
    }
    override val bounded: SchedulerCoroutineDispatcher by lazy { Schedulers.boundedElastic().asCoroutineDispatcher() }
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined

    override val parallel: CoroutineDispatcher by lazy { Schedulers.parallel().asCoroutineDispatcher() }

    override val default: CoroutineDispatcher =
            Dispatchers.Default

    override val io: CoroutineDispatcher =
        Dispatchers.IO
}

// until this lands https://github.com/Kotlin/kotlinx.coroutines/issues/1360
private fun MeterRegistry.monitorAsExecutor(dispatcher: CoroutineDispatcher, name: String) {
    ExecutorServiceMetrics.monitor(this, dispatcher.asExecutor(), name, Tag.of("service", "chk"))
}
