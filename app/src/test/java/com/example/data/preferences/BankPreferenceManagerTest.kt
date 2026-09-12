package com.example.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankPreferenceManagerTest {

    private lateinit var context: Context
    private lateinit var manager: BankPreferenceManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        manager = BankPreferenceManager(context)
        manager.resetToDefault()
    }

    @Test
    fun testInitialState() = runBlocking {
        val initial = manager.activeBanksFlow.first()
        val configured = manager.isUserConfiguredFlow.first()
        assertNull(initial)
        assertFalse(configured)
    }

    @Test
    fun testSaveActiveBanks() = runBlocking {
        val selected = setOf("KOTAK", "HDFC", "SBI")
        manager.saveActiveBanks(selected)

        val retrieved = manager.activeBanksFlow.first()
        val configured = manager.isUserConfiguredFlow.first()
        assertEquals(selected, retrieved)
        assertTrue(configured)
    }

    @Test
    fun testToggleBank() = runBlocking {
        manager.saveActiveBanks(setOf("KOTAK", "SBI"))

        // Toggle KOTAK off
        manager.toggleBank("KOTAK", setOf("KOTAK", "SBI"))
        val afterRemove = manager.activeBanksFlow.first()
        assertEquals(setOf("SBI"), afterRemove)

        // Toggle HDFC on
        manager.toggleBank("HDFC", afterRemove!!)
        val afterAdd = manager.activeBanksFlow.first()
        assertEquals(setOf("SBI", "HDFC"), afterAdd)
    }

    @Test
    fun testClearAllAndSelectAll() = runBlocking {
        val all = listOf("SBI", "HDFC", "ICICI", "AXIS", "KOTAK")
        manager.selectAll(all)
        assertEquals(all.toSet(), manager.activeBanksFlow.first())

        manager.clearAll()
        assertEquals(emptySet<String>(), manager.activeBanksFlow.first())
    }
}
