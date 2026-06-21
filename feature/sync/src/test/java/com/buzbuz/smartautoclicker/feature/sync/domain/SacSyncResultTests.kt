/*
 * Copyright (C) 2026 Nicolas Espinoza
 */
package com.buzbuz.smartautoclicker.feature.sync.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SacSyncResultTests {

    @Test
    fun displayStatus_reportsSkippedWhenNotConfigured() {
        val result = SacSyncResult(skipped = true, statusMessage = "sync not configured")
        assertEquals("sync not configured", result.displayStatus())
    }

    @Test
    fun displayStatus_reportsErrorMessage() {
        val result = SacSyncResult(errorMessage = "network down")
        assertEquals("sync failed: network down", result.displayStatus())
    }

    @Test
    fun didWork_isTrueWhenAnyCounterMoved() {
        assertTrue(SacSyncResult(scenariosPushed = 1).didWork)
        assertTrue(SacSyncResult(catchNeedlesPulled = 2).didWork)
    }

    @Test
    fun didWork_isFalseForNoOpSuccess() {
        assertFalse(SacSyncResult().didWork)
    }
}
