package test.test

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import reactor.core.scheduler.Schedulers
import springcoroutine.SpringScope
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.annotation.PostConstruct

@Configuration
class Config(
    val meterRegistry: MeterRegistry,
    val service: Service,
    val nopService: NopService,
    @Value("\${runLoadInternally.enabled}")
    val runLoadInternally: Boolean,
    @Value("\${printMetrics.enabled}")
    val printMetrics: Boolean
    ) : SpringScope by SpringScope(Dispatchers.Unconfined) {
//    @Autowired
//    private lateinit var meterRegistry: MeterRegistry

    suspend fun printQueued(){
        val queued = meterRegistry.find("executor.queued")
        val gauges = queued.gauges().toList()
        val gaugesById = gauges.bySchedulerId()
        val bounded = gaugesById.sum("bounded"){it.value()}
        val parallel = gaugesById.sum("parallel"){it.value()}

        //println("meters: ${meters.size} gauges: ${gauges.size} timers: ${timers.size} counters: ${counters.size}")
        println("queued:    bel ${bounded?.formatted()} par ${parallel?.formatted()}")

    }
    suspend fun printCompleted(){
        val queued = meterRegistry.find("executor.completed")
        val gauges = queued.gauges().toList()
        val meters = queued.meters().toList()
        val functionCounters = queued.functionCounters().toList()
        val counters = queued.counters().toList()
        val fcById = functionCounters.bySchedulerId()
        val fcBounded = fcById.sum("bounded"){ it.count() }
        val fcParallel = fcById.sum("parallel"){ it.count() }
        //println("meters: ${meters.size} gauges: ${gauges.size} timers: ${timers.size} counters: ${counters.size}")
        println("completed: bel ${fcBounded?.formatted()} par ${fcParallel?.formatted()}")
    }

    @PostConstruct
    fun postConstruct(){
        if(printMetrics){
            launch {
                while(job.isActive){
                    delay(1000L)
                    printQueued()
                    printCompleted()
                }
            }
        }
        if(runLoadInternally){
            launch {
                val range = 1..100
                while (job.isActive){
                    withContext(Schedulers.boundedElastic().asCoroutineDispatcher()) {
                        for (i in range) {
                            val concurrent = 0..i
                            concurrent.map {
                                async {
                                    //service.resilientCallMonoNop().awaitSingle()
                                    //nopService.coroutineOnParallel()
                                    nopService.reactor().awaitSingle()

                                }
                            }.awaitAll()
                        }
                    }
                    delay(10L)
                }
            }
        }
    }
    companion object{
        fun <A: Meter> List<A>.bySchedulerId() = mapNotNull { g -> g.id.tags.firstOrNull { it.key == "reactor.scheduler.id" }?.value?.let { it to g } }.groupBy { (key,g) -> key }.mapValues { (k,v) -> v.map { it.second } }
        fun Map<String,List<Gauge>>.sumGauge(keyContains: String): Double? =
            key(keyContains)?.let { this[it] }?.sumOf { it.value() }

        fun <A: Meter> Map<String,List<A>>.sum(keyContains: String, getValue: (A)-> Double): Double? =
            key(keyContains)?.let { this[it] }?.sumOf { getValue(it) }

        fun <A> Map<String,A>.key(keyContains: String) = keys.firstOrNull { it.contains(keyContains) }
        fun Map<String,List<Counter>>.sumCounter(keyContains: String) =
            key(keyContains)?.let { this[it] }?.sumOf { it.count() }
        fun Double.formatted() =
            DecimalFormat("###.#").apply {
                roundingMode = RoundingMode.CEILING
            }.format(this)

    }
}