package test.test

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import reactor.blockhound.BlockHound

@Configuration
@ConditionalOnProperty(
    value = ["blockhound.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Suppress("UtilityClassWithPublicConstructor")
class BlockhoundConfig {
    init {
        log.info("Blockhound is instrumenting the application")
        BlockHound.install()
    }

    companion object {
        val log = LoggerFactory.getLogger(BlockhoundConfig::class.java)
    }
}
