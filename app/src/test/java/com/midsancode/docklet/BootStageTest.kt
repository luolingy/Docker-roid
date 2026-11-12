package com.midsancode.docklet

import com.midsancode.docklet.data.vm.BootStage
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
        assertEquals(BootStage.KERNEL, BootStage.match("Docklet: kernel up"))
        assertEquals(BootStage.BOOTSTRAP, BootStage.match("Docklet: bootstrap done"))
        assertEquals(BootStage.NETWORK, BootStage.match("Docklet: network up"))
        assertEquals(BootStage.ENGINE, BootStage.match("Docklet: engine starting"))
        assertEquals(BootStage.READY, BootStage.match("Docklet: READY"))
    }

    @Test
    fun `markers embedded in log lines still match`() {
        assertNotNull(BootStage.match("[    0.123] Docklet: kernel up"))
        assertNotNull(BootStage.match("openrc: Docklet: READY"))
        assertNotNull(BootStage.match("Docklet: READY  (took 3.2s)"))
    }

    @Test
    fun `non-markers return null`() {
        assertNull(BootStage.match("eth0: link up"))
        assertNull(BootStage.match("Docklet: unknown-stage"))
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
