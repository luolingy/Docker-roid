package com.dockerroid

import com.dockerroid.data.vm.BootStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BootStage 串口标记解析单测。
 * 保证 guest init 脚本打印的标记与 App 解析严格一致（verify.ps1 也校验）。
 */
class BootStageTest {

    @Test
    fun `exact markers match`() {
        assertEquals(BootStage.KERNEL, BootStage.match("docker-roid: kernel up"))
        assertEquals(BootStage.BOOTSTRAP, BootStage.match("docker-roid: bootstrap done"))
        assertEquals(BootStage.NETWORK, BootStage.match("docker-roid: network up"))
        assertEquals(BootStage.ENGINE, BootStage.match("docker-roid: engine starting"))
        assertEquals(BootStage.READY, BootStage.match("docker-roid: READY"))
    }

    @Test
    fun `markers embedded in log lines still match`() {
        assertNotNull(BootStage.match("[    0.123] docker-roid: kernel up"))
        assertNotNull(BootStage.match("openrc: docker-roid: READY"))
        assertNotNull(BootStage.match("docker-roid: READY  (took 3.2s)"))
    }

    @Test
    fun `non-markers return null`() {
        assertNull(BootStage.match("eth0: link up"))
        assertNull(BootStage.match("docker-roid: unknown-stage"))
        assertNull(BootStage.match(""))
    }

    @Test
    fun `kernel panic style lines are not boot stages`() {
        assertNull(BootStage.match("Kernel panic - not syncing: attempted to kill init!"))
    }

    @Test
    fun `enum order matches boot sequence`() {
        val order = BootStage.entries
        assertEquals(BootStage.KERNEL, order[0])
        assertEquals(BootStage.READY, order.last())
    }
}
