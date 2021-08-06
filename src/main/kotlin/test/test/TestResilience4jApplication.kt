package test.test

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.scheduler.Schedulers

@SpringBootApplication
class TestResilience4jApplication

fun main(args: Array<String>) {
    Schedulers.enableMetrics()
    runApplication<TestResilience4jApplication>(*args)
}
