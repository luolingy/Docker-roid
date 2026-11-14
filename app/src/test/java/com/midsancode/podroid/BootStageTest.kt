package com.midsancode.podroid

import com.midsancode.podroid.data.vm.BootStage
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
        assertEquals(BootStage.KERNEL, BootStage.match("Podroid: kernel up"))
        assertEquals(BootStage.BOOTSTRAP, BootStage.match("Podroid: bootstrap done"))
        assertEquals(BootStage.NETWORK, BootStage.match("Podroid: network up"))
        assertEquals(BootStage.ENGINE, BootStage.match("Podroid: engine starting"))
        assertEquals(BootStage.READY, BootStage.match("Podroid: READY"))
    }

    @Test
    fun `markers embedded in log lines still match`() {
        assertNotNull(BootStage.match("[    0.123] Podroid: kernel up"))
        assertNotNull(BootStage.match("openrc: Podroid: READY"))
        assertNotNull(BootStage.match("Podroid: READY  (took 3.2s)"))
    }

    @Test
    fun `non-markers return null`() {
        assertNull(BootStage.match("eth0: link up"))
        assertNull(BootStage.match("Podroid: unknown-stage"))
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
