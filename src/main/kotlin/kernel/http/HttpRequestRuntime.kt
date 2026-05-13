package kernel.http

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

object HttpRequestRuntime {
    private val current = ThreadLocal<Request?>()

    fun current(): Request {
        return current.get()
            ?: error("No existe una Request HTTP activa en el runtime actual.")
    }

    fun <T> withRequest(request: Request, block: () -> T): T {
        val previous = current.get()
        current.set(request)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }

    suspend fun <T> withRequestContext(
        request: Request,
        block: suspend () -> T
    ): T {
        return withContext(RequestContextElement(request)) {
            block()
        }
    }

    fun contextElement(request: Request): CoroutineContext {
        return RequestContextElement(request)
    }

    private class RequestContextElement(
        private val request: Request
    ) : ThreadContextElement<Request?>,
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<RequestContextElement>

        override fun updateThreadContext(context: CoroutineContext): Request? {
            val previous = current.get()
            current.set(request)
            return previous
        }

        override fun restoreThreadContext(
            context: CoroutineContext,
            oldState: Request?
        ) {
            current.set(oldState)
        }
    }
}
