package test.test

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller(
    val service: Service,
    val nopService: NopService,
    dispatcherProvider: DispatcherProvider
) : DispatcherProvider by dispatcherProvider{

    @GetMapping("/defaultReactor")
    fun defaultReactor(): List<String> = runBlocking(default) {
        concurrent(3){
            service.reactor().awaitSingle()
        }
    }

    @GetMapping("/defaultCoroutine")
    fun defaultCoroutine(): List<String> = runBlocking(default) {
        concurrent(3){
            service.coroutineOnCalling()
        }
    }

    @GetMapping("/unconfinedCoroutine")
    fun unconfinedCoroutine(): List<String> = runBlocking(unconfined) {
        concurrent(3){
            service.coroutineOnCalling()
        }
    }

    @GetMapping("/suspendCoroutine")
    suspend fun suspendCoroutine(): List<String> =
        concurrent(3){
            service.coroutineOnCalling()
        }

    @GetMapping("/ioCoroutine")
    fun ioCoroutine(): List<String> = runBlocking(io) {
        concurrent(3){
            service.coroutineOnCalling()
        }
    }

    @GetMapping("/coroutineParallel")
    fun outCoroutineParallel(): List<String> = runBlocking(default) {
        service.coroutineOnParallel()
    }

    @GetMapping("/coroutineBlocking")
    fun outCoroutineBlocking(): List<String> = runBlocking(default) {
        Thread.sleep(10)
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
