/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("unused")

package kotlinx.coroutines.linearizability

import com.devexperts.dxlab.lincheck.*
import com.devexperts.dxlab.lincheck.annotations.*
import com.devexperts.dxlab.lincheck.paramgen.*
import com.devexperts.dxlab.lincheck.strategy.stress.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.*
import java.io.*

/**
 * This is stress test that is fine-tuned to catch the problem
 * [#1419](https://github.com/Kotlin/kotlinx.coroutines/issues/1419)
 */
@Param(name = "value", gen = IntGen::class, conf = "2:2")
@OpGroupConfig.OpGroupConfigs(
    OpGroupConfig(name = "send", nonParallel = true),
    OpGroupConfig(name = "receive", nonParallel = true),
    OpGroupConfig(name = "close", nonParallel = true)
)
class ChannelCloseLCStressTest : TestBase() {

    private companion object {
        // Emulating ctor argument for lincheck
        var capacity = 0
    }

    private val lt = LinTesting()
    private var channel: Channel<Int> = Channel(capacity)

    @Operation(runOnce = true, group = "send")
    fun send1(@Param(name = "value") value: Int) = lt.run("send1") { channel.send(value) }

    @Operation(runOnce = true, group = "send")
    fun send2(@Param(name = "value") value: Int) = lt.run("send2") { channel.send(value) }

    @Operation(runOnce = true, group = "receive")
    fun receive1() = lt.run("receive1") { channel.receive() }

    @Operation(runOnce = true, group = "receive")
    fun receive2() = lt.run("receive2") { channel.receive() }

    @Operation(runOnce = true, group = "close")
    fun close1() = lt.run("close1") { channel.close(IOException("close1")) }

    @Operation(runOnce = true, group = "close")
    fun close2() = lt.run("close2") { channel.close(IOException("close2")) }

    @Test
    fun testRendezvousChannelLinearizability() {
        runTest(0)
    }

    @Test
    fun testArrayChannelLinearizability() {
        for (i in listOf(1, 2, 16)) {
            runTest(i)
        }
    }

    @Test
    fun testConflatedChannelLinearizability() = runTest(Channel.CONFLATED)

    @Test
    fun testUnlimitedChannelLinearizability() = runTest(Channel.UNLIMITED)

    private fun runTest(capacity: Int) {
        ChannelCloseLCStressTest.capacity = capacity
        val options = StressOptions()
            .iterations(1) // only one iteration -- test scenario is fixed
            .invocationsPerIteration(10_000 * stressTestMultiplierSqrt)
            .threads(3)
            .verifier(LinVerifier::class.java)
        LinChecker.check(ChannelCloseLCStressTest::class.java, options)
    }
}
