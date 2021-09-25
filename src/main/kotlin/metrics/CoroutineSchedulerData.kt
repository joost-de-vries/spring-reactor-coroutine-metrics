@file:Suppress("INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE", "INVISIBLE_MEMBER")

package metrics

import kotlinx.coroutines.scheduling.CoroutineScheduler
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.lang.reflect.Field

data class PoolSize(
        val corePoolSize: Int,
        val maxPoolSize: Int,
)

data class WorkerStates(
        val cpuWorkers: Int,
        val blockingWorkers: Int,
        val parkedWorkers: Int,
        val dormant: Int,
        val terminated: Int,
)

data class ControlState(
        val createdWorkers: Int,
        val blockingTasks: Int,
        val cpusAcquired: Int
) {
    companion object {
        private const val searchString = "Control State"

        /** parses CoroutineScheduler.toString output */
        fun parse(toString: String) = kotlin.runCatching {
            toString.indexOf(searchString).takeIf { it != -1 }?.let { afterIndex ->
                toString.substring(afterIndex + searchString.length).split(',').takeIf { it.size == 3 }?.let {
                    val createdWorkers = parsePart(it[0])
                    val blockingTasks = parsePart(it[1])
                    val cpusAcquired = parsePart(it[2])
                    ControlState(
                            createdWorkers = createdWorkers,
                            blockingTasks = blockingTasks,
                            cpusAcquired = cpusAcquired
                    )
                }
            }
        }.let {
            if (it.isFailure) {
                log.error("Parsing CoroutineScheduler Control State failed", it.exceptionOrNull())
            }
            it.getOrNull()
        }
        private val log = LoggerFactory.getLogger(ControlState::class.java)

        private fun parsePart(string: String): Int {
            val cleaned = string.replace('}', ' ').replace(']', ' ')
            val start = cleaned.indexOf('=')
            val end = cleaned.length
            if (start == -1) throw IllegalArgumentException("expected 'bla=123' but got $string")
            return cleaned.substring(start + 1, end).trim().toInt()
        }
    }
}

data class RunningWorkerQueues(
        val blockingWorkerQueueSize: List<Int>,
        val cpuAcquiredWorkerQueueSize: List<Int>,
        val dormantWorkerQueueSize: List<Int>,
) {
    companion object {
        fun create(runningWorkerQueues: Map<CoroutineScheduler.WorkerState, List<Int>>) = RunningWorkerQueues(
                blockingWorkerQueueSize = runningWorkerQueues.getValue(CoroutineScheduler.WorkerState.BLOCKING),
                cpuAcquiredWorkerQueueSize = runningWorkerQueues.getValue(CoroutineScheduler.WorkerState.CPU_ACQUIRED),
                dormantWorkerQueueSize = runningWorkerQueues.getValue(CoroutineScheduler.WorkerState.DORMANT)
        )
    }
}

data class CoroutineSchedulerData(
        val poolSize: PoolSize,
        val workerStates: WorkerStates,
        val runningWorkerQueuesAsStrings: List<String>,
        val runningWorkerQueues: RunningWorkerQueues,
        val globalCpuQueueSize: Int,
        val globalBlockingQueueSize: Int,
        val controlState: ControlState?
)

internal fun CoroutineScheduler.metrics(): CoroutineSchedulerData {
    var parkedWorkers = 0
    var blockingWorkers = 0
    var cpuWorkers = 0
    var dormant = 0
    var terminated = 0
    val queueSizesStrings = arrayListOf<String>()
    val queueSizes: Map<CoroutineScheduler.WorkerState, MutableList<Int>> = mutableMapOf(
            CoroutineScheduler.WorkerState.BLOCKING to mutableListOf(),
            CoroutineScheduler.WorkerState.CPU_ACQUIRED to mutableListOf(),
            CoroutineScheduler.WorkerState.DORMANT to mutableListOf(),
    )
    for (index in 1 until workers.length()) {
        val worker: CoroutineScheduler.Worker = workers[index] ?: continue
        val queueSize = worker.localQueue.size
        when (val state = worker.workerState()) {
            CoroutineScheduler.WorkerState.PARKING -> ++parkedWorkers
            CoroutineScheduler.WorkerState.BLOCKING -> {
                ++blockingWorkers
                queueSizesStrings += queueSize.toString() + "b" // Blocking
                queueSizes.getValue(state).add(queueSize)
            }
            CoroutineScheduler.WorkerState.CPU_ACQUIRED -> {
                ++cpuWorkers
                queueSizesStrings += queueSize.toString() + "c" // CPU
                queueSizes.getValue(state).add(queueSize)
            }
            CoroutineScheduler.WorkerState.DORMANT -> {
                ++dormant
                if (queueSize > 0) {
                    queueSizesStrings += queueSize.toString() + "d" // Retiring
                    queueSizes.getValue(state).add(queueSize)
                }
            }
            CoroutineScheduler.WorkerState.TERMINATED -> ++terminated
        }
    }

    return CoroutineSchedulerData(
            poolSize = PoolSize(
                    corePoolSize = corePoolSize,
                    maxPoolSize = maxPoolSize
            ),
            workerStates = WorkerStates(
                    cpuWorkers = cpuWorkers,
                    blockingWorkers = blockingWorkers,
                    parkedWorkers = parkedWorkers,
                    dormant = dormant,
                    terminated = terminated
            ),
            runningWorkerQueuesAsStrings = queueSizesStrings,
            runningWorkerQueues = RunningWorkerQueues.create(queueSizes),
            globalCpuQueueSize = globalCpuQueue.size,
            globalBlockingQueueSize = globalBlockingQueue.size,
            controlState = controlStateMetrics()
    )
}

internal fun CoroutineScheduler.controlStateMetrics() = ControlState.parse(this.toString())

internal fun CoroutineScheduler.Worker.workerState(): CoroutineScheduler.WorkerState = declaredFieldValue("state")

inline fun <reified A> Any.declaredFieldValue(name: String): A =
        declaredField(name).get(this) as A

fun Any.declaredField(name: String): Field =
        this::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
