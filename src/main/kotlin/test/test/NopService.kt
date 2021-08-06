package test.test

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.decorateSuspendFunction
import io.github.resilience4j.kotlin.timelimiter.decorateSuspendFunction
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.reactor.SchedulerCoroutineDispatcher
import kotlinx.coroutines.reactor.asCoroutineDispatcher
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Component
class NopService(
    webclient: WebClient.Builder,
              val circuitBreakerRegistry: CircuitBreakerRegistry,
              val timeLimiterRegistry: TimeLimiterRegistry,
              val meterRegistry: MeterRegistry,
    @Value("\${timeLimiter.enabled}")
    val timeLimiterEnabled: Boolean,
    @Value("\${circuitBreaker.enabled}")
    val circuitBreakerEnabled: Boolean,
) {
    val client = webclient.baseUrl("http://localhost:9090").build()
    val circuitBreaker = circuitBreakerRegistry.circuitBreaker("nop")
    val timeLimiter = timeLimiterRegistry.timeLimiter("nop")

    val parallel: SchedulerCoroutineDispatcher by lazy { Schedulers.parallel().asCoroutineDispatcher() }

    fun reactor(): Mono<List<String>> {
        var transformed = monoNop()
        if(timeLimiterEnabled){
            transformed = transformed
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
        }
        if(circuitBreakerEnabled){
            transformed = transformed
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        }
        return transformed
    }

    suspend fun coroutineOnParallel() = withContext(parallel){
        var decorated: suspend () -> List<String> = {
            monoNop().awaitSingle()
        }
        if(timeLimiterEnabled){
            decorated = timeLimiter.decorateSuspendFunction(decorated)
        }
        if(circuitBreakerEnabled){
            decorated = circuitBreaker.decorateSuspendFunction(decorated)
        }
        decorated.invoke()
    }

    suspend fun coroutineOnCalling(): List<String> {
        var decorated: suspend () -> List<String> = {
            monoNop().awaitSingle()
        }
        if(timeLimiterEnabled){
            decorated = timeLimiter.decorateSuspendFunction(decorated)
        }
        if(circuitBreakerEnabled){
            decorated = circuitBreaker.decorateSuspendFunction(decorated)
        }
        return decorated.invoke()
    }

    private fun monoNop() = Mono.fromCallable { listOf("nop") }
}