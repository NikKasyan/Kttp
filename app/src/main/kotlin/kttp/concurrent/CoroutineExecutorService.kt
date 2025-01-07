package kttp.concurrent

import kotlinx.coroutines.*
import java.util.concurrent.*

class CoroutineExecutorService(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ExecutorService {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var isShutdown = false

    override fun execute(command: Runnable) {
        ensureNotShutdown()
        scope.launch { command.run() }
    }

    override fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        scope.cancel()
    }

    override fun shutdownNow(): MutableList<Runnable> {
        if (isShutdown) return mutableListOf()
        isShutdown = true
        return scope.coroutineContext[Job]?.children?.mapNotNull { it as? Runnable }?.toMutableList() ?: mutableListOf()
    }

    override fun isShutdown(): Boolean = isShutdown

    override fun isTerminated(): Boolean = isShutdown && scope.coroutineContext.isActive.not()

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = runBlocking {
        withTimeoutOrNull(unit.toMillis(timeout)) {
            while (scope.coroutineContext.isActive) delay(1)
        } != null
    }

    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        ensureNotShutdown()
        val deferred = scope.async { task.call() }
        return deferred.asFuture()
    }

    override fun <T : Any?> submit(task: Runnable, result: T): Future<T> {
        ensureNotShutdown()
        val deferred = scope.async { task.run(); result }
        return deferred.asFuture()
    }

    override fun submit(task: Runnable): Future<*> {
        ensureNotShutdown()
        val deferred = scope.async { task.run() }
        return deferred.asFuture()
    }

    override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>): MutableList<Future<T>> {
        ensureNotShutdown()
        return tasks.map { submit(it) }.toMutableList()
    }

    override fun <T : Any?> invokeAll(
        tasks: MutableCollection<out Callable<T>>,
        timeout: Long,
        unit: TimeUnit
    ): MutableList<Future<T>> {
        ensureNotShutdown()
        return runBlocking {
            val timeoutMillis = unit.toMillis(timeout)
            withTimeoutOrNull(timeoutMillis) {
                tasks.map { submit(it) }.toMutableList()
            } ?: mutableListOf()
        }
    }


    override fun <T : Any> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
        ensureNotShutdown()
        return runBlocking {
            val jobs = tasks.map { scope.async { it.call() } }
            val firstCompleted = jobs.awaitAny()
            jobs.forEach { it.cancel() }
            firstCompleted
        }
    }

    override fun <T : Any> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit): T {
        ensureNotShutdown()
        return runBlocking {
            val timeoutMillis = unit.toMillis(timeout)
            withTimeout(timeoutMillis) {
                invokeAny(tasks)
            }
        }
    }

    private fun ensureNotShutdown() {
        if (isShutdown) throw RejectedExecutionException("Executor service is shut down")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <T> List<Deferred<T>>.awaitAny(): T {
    val deferred = CompletableDeferred<T>()
    forEach { task ->
        task.invokeOnCompletion { cause ->
            if (cause == null) deferred.complete(task.getCompleted())
            else if (this.all { task.isCancelled })
                deferred.completeExceptionally(ExecutionException("All tasks were cancelled", cause))
        }

    }
    return deferred.await()
}

private fun <T> Deferred<T>.asFuture(): Future<T> {
    val deferred = this
    return object : Future<T> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            deferred.cancel()
            return true
        }

        override fun isCancelled(): Boolean = deferred.isCancelled

        override fun isDone(): Boolean = deferred.isCompleted

        override fun get(): T = runBlocking { deferred.await() }

        override fun get(timeout: Long, unit: TimeUnit): T =runBlocking {
                withTimeout(unit.toMillis(timeout)) {
                    deferred.await()
                }
            }
        }

}