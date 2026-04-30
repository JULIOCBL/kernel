package kernel.concurrency

import kernel.foundation.Application
import kernel.foundation.app

fun Application.blockingTaskRunner(): BlockingTaskRunner {
    return config.get("services.tasks.blocking") as? BlockingTaskRunner
        ?: error("BlockingTaskRunner no esta registrado en services.tasks.blocking.")
}

fun blockingTaskRunner(): BlockingTaskRunner = app().blockingTaskRunner()
