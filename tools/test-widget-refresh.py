#!/usr/bin/env python3
"""Replay a lost-update race using the production widget dispatch methods.

Requires Kotlin/JVM and a JDK, without Android SDK. Android/Glance rendering is
replaced by a blocking recorder; request and completion bodies are unchanged.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def method(path, signature):
    text = (ROOT / path).read_text()
    start = text.index(signature)
    brace = text.index("{", start)
    depth, end = 1, brace + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


updater = method(
    "Common/src/mobile/java/tk/glucodata/widgets/WidgetUpdater.kt",
    "fun requestUpdateAll(",
)
expressive = method(
    "Common/src/mobile/java/tk/glucodata/widget/ExpressiveWidgetReceiver.kt",
    "private suspend fun performUpdate(",
)

fixture = r'''
package replay

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean as JAtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.*

class Context { val applicationContext get() = this }
class ComponentName(context: Context, provider: Any)
enum class WidgetKind { BASIC; val provider get() = Any() }
class AppWidgetManager {
    fun getAppWidgetIds(name: ComponentName) = intArrayOf(1)
    companion object { fun getInstance(context: Context) = AppWidgetManager() }
}
object Log {
    fun stack(tag: String, message: String, t: Throwable) { throw AssertionError(message, t) }
}
class AtomicBoolean(initial: Boolean) {
    private val value = JAtomicBoolean(initial)
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        val result = value.compareAndSet(expect, update)
        // A thread can be preempted after observing an active renderer and
        // before setting its rerun flag. Make that interleaving deterministic.
        if (!result && this === Probe.pauseFlag && Probe.pauseOnce.compareAndSet(true, false)) {
            Probe.failedRequest.countDown()
            check(Probe.resumeRequest.await(5, TimeUnit.SECONDS))
        }
        return result
    }
    fun set(next: Boolean) = value.set(next)
    fun getAndSet(next: Boolean) = value.getAndSet(next)
}
object Probe {
    val renders = AtomicInteger()
    val pauseOnce = JAtomicBoolean()
    var pauseFlag: AtomicBoolean? = null
    lateinit var enteredRender: CountDownLatch
    lateinit var finishRender: CountDownLatch
    lateinit var failedRequest: CountDownLatch
    lateinit var resumeRequest: CountDownLatch
    fun reset(flag: AtomicBoolean) {
        renders.set(0)
        pauseFlag = flag
        pauseOnce.set(true)
        enteredRender = CountDownLatch(1)
        finishRender = CountDownLatch(1)
        failedRequest = CountDownLatch(1)
        resumeRequest = CountDownLatch(1)
    }
    fun render() {
        if (renders.incrementAndGet() == 1) {
            enteredRender.countDown()
            check(finishRender.await(5, TimeUnit.SECONDS))
        }
    }
}
class QueueExecutor {
    val queue = ConcurrentLinkedQueue<Runnable>()
    fun execute(action: () -> Unit) { queue.add(Runnable(action)) }
    fun drain() { while (true) (queue.poll() ?: return).run() }
}
object Home {
    const val LOG_ID = "home"
    val pending = AtomicBoolean(false)
    val rerunRequested = AtomicBoolean(false)
    val pendingCallbacks = ConcurrentLinkedQueue<() -> Unit>()
    val executor = QueueExecutor()
    fun render(context: Context, manager: AppWidgetManager, targets: Map<WidgetKind, IntArray>) = Probe.render()
    // HOME_METHOD
}
class ExpressiveAppWidget {
    suspend fun update(context: Context, id: Int) = Probe.render()
}
fun runSuspend(action: suspend () -> Unit) {
    var completed = false
    action.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); completed = true }
    })
    check(completed) { "Unexpected asynchronous fixture" }
}
object Expressive {
    const val LOG_ID = "expressive"
    val updating = AtomicBoolean(false)
    val updateAgain = AtomicBoolean(false)
    val executor = QueueExecutor()
    fun updateAll(context: Context) { executor.execute { runSuspend { performUpdate(context) } } }
    // EXPRESSIVE_METHOD
}
fun replay(name: String, flag: AtomicBoolean, executor: QueueExecutor, start: () -> Unit, request: () -> Unit) {
    Probe.reset(flag)
    start()
    val errors = ConcurrentLinkedQueue<Throwable>()
    fun launch(action: () -> Unit) = thread(isDaemon = true) {
        try { action() } catch (t: Throwable) { errors.add(t) }
    }
    val worker = launch { executor.queue.remove().run() }
    check(Probe.enteredRender.await(5, TimeUnit.SECONDS))
    val requester = launch(request)
    check(Probe.failedRequest.await(5, TimeUnit.SECONDS))
    Probe.finishRender.countDown()
    // The old worker finishes while the requester is paused. The fixed worker
    // waits on the shared state monitor until that requester records its rerun.
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (worker.isAlive && worker.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
        Thread.yield()
    }
    Probe.resumeRequest.countDown()
    requester.join(5_000)
    worker.join(5_000)
    check(!requester.isAlive && !worker.isAlive) { "$name deadlocked" }
    check(errors.isEmpty()) { errors.toString() }
    executor.drain()
    check(Probe.renders.get() == 2) { "$name lost the final update: renders=${Probe.renders.get()}" }
    println("PASS: $name delivers request racing with renderer completion")
}
fun main() {
    val context = Context()
    val cases = listOf<() -> Unit>(
        { replay("home", Home.pending, Home.executor, { Home.requestUpdateAll(context) }) { Home.requestUpdateAll(context) } },
        { replay("expressive", Expressive.updating, Expressive.executor, { Expressive.updateAll(context) }) {
            runSuspend { Expressive.performUpdate(context) }
        } },
    )
    var failed = false
    cases.forEach { case ->
        try { case() } catch (t: Throwable) { failed = true; t.printStackTrace() }
    }
    check(!failed) { "Widget refresh race regression" }
}
'''

glance = '''
package androidx.glance.appwidget
class GlanceAppWidgetManager(context: replay.Context) {
    suspend fun getGlanceIds(type: Class<*>) = listOf(1)
}
'''

with tempfile.TemporaryDirectory() as directory:
    tmp = Path(directory)
    (tmp / "Replay.kt").write_text(
        fixture.replace("// HOME_METHOD", updater)
        .replace("// EXPRESSIVE_METHOD", expressive.replace("private suspend", "suspend", 1))
    )
    (tmp / "Glance.kt").write_text(glance)
    jar = str(tmp / "replay.jar")
    subprocess.run(
        ["kotlinc", str(tmp / "Replay.kt"), str(tmp / "Glance.kt"), "-nowarn", "-include-runtime", "-d", jar],
        check=True,
    )
    subprocess.run(["java", "-jar", jar], check=True, timeout=30)
