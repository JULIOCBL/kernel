package kernel.concurrency

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory

class JvmBlockingTaskRunner(
    virtualThreads: Boolean = true,
    namePrefix: String = "kernel-blocking"
) : BlockingTaskRunner {
    private val executor: ExecutorService = if (virtualThreads) {
        Executors.newVirtualThreadPerTaskExecutor()
    } else {
        Executors.newCachedThreadPool(namedFactory(namePrefix))
    }

    override fun <T> submit(task: () -> T): Future<T> {
        return executor.submit<T> { task() }
    }

    override fun close() {
        executor.shutdown()
    }

    private fun namedFactory(namePrefix: String): ThreadFactory {
        return ThreadFactory { runnable ->
            Thread.ofPlatform()
                .name("$namePrefix-", 0)
                .daemon(true)
                .unstarted(runnable)
        }
    }
}
