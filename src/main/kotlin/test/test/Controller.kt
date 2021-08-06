package test.test

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.SchedulerCoroutineDispatcher
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.scheduler.Schedulers

@RestController
class Controller(
    val service: Service,
    val nopService: NopService,
    meterRegistry: MeterRegistry
) : DispatcherProvider by dispatcherProvider(meterRegistry){

    @GetMapping("/outReactor")
    fun outMono(): List<String> = runBlocking(parallel) {
        concurrent(3){
            service.reactor().awaitSingle()
        }
    }

    @GetMapping("/outCoroutine")
    fun outCoroutine(): List<String> = runBlocking(default) {
        concurrent(3){
            service.coroutineOnCalling()
        }
    }

    @GetMapping("/outCoroutineParallel")
    fun outCoroutineParallel(): List<String> = runBlocking(unconfined) {
        service.coroutineOnParallel()
    }

    @GetMapping("/nopReactor", produces = ["application/json"])
    fun nopReactor(): List<String> = runBlocking(bounded) {
        nopService.reactor().awaitSingle()
    }

    @GetMapping("/nopCoroutine", produces = ["application/json"])
    fun nopCoroutine(): List<String> = runBlocking(bounded) {
        nopService.coroutineOnCalling()
    }

    @GetMapping("/nopCoroutineParallel", produces = ["application/json"])
    fun nopCoroutineParallel(): List<String> = runBlocking(bounded) {
        nopService.coroutineOnParallel()
    }

    @GetMapping("/nop", produces = ["application/json"])
    fun nop(): List<String> {
        return listOf("nop")
    }
}
private suspend fun concurrent(times: Int, block: suspend () -> List<String>): List<String> =
    (0..times).map{
        coroutineScope {
            async{
                block()
            }
        }
    }.awaitAll().flatten()
