@file:Suppress("INVISIBLE_REFERENCE")
package test.test

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.reactor.SchedulerCoroutineDispatcher
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.scheduling.CoroutineScheduler
import kotlinx.coroutines.scheduling.ExperimentalCoroutineDispatcher
import reactor.core.scheduler.Schedulers

interface DispatcherProvider {
    val bounded: SchedulerCoroutineDispatcher
    val unconfined: CoroutineDispatcher
    val parallel: CoroutineDispatcher
    val default: CoroutineDispatcher

}
@OptIn(InternalCoroutinesApi::class)
fun dispatcherProvider(meterRegistry: MeterRegistry) = object : DispatcherProvider{
   override val bounded: SchedulerCoroutineDispatcher by lazy { Schedulers.boundedElastic().asCoroutineDispatcher() }
    override  val unconfined: CoroutineDispatcher by lazy { Dispatchers.Unconfined.also {
        meterRegistry.monitorAsExecutor(it, "Dispatchers.Unconfined")
    } }
    override  val parallel: CoroutineDispatcher by lazy { Schedulers.parallel().asCoroutineDispatcher() }
    @Suppress("INVISIBLE_REFERENCE")
    override  val default: CoroutineDispatcher by lazy {
        val dispatcher = Dispatchers.Default as ExperimentalCoroutineDispatcher
        val scheduler = dispatcher.executor as CoroutineScheduler
        Dispatchers.Default.also {
        meterRegistry.monitorAsExecutor(it, "Dispatchers.Default")
    } }
}
// until this lands https://github.com/Kotlin/kotlinx.coroutines/issues/1360
private fun MeterRegistry.monitorAsExecutor(dispatcher: CoroutineDispatcher, name: String) {
    ExecutorServiceMetrics.monitor(this, dispatcher.asExecutor(), name, Tag.of("service", "chk"))
}
