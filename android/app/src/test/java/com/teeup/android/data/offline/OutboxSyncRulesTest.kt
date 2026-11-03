package com.teeup.android.data.offline

import com.teeup.android.data.ApiException
import com.teeup.android.data.HoleScoreInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline-mode sync rules: what gets re-sent, and which failures retry vs. stop. */
class OutboxSyncRulesTest {
    private fun hole(n: Int) = HoleScoreInput(n, 4, 2)

    @Test
    fun `holesToSend skips holes the server already has`() {
        // Arrange: holes 1-3 queued offline, hole 2 already uploaded before the response was lost
        val queued = listOf(hole(1), hole(2), hole(3))

        // Act
        val toSend = OutboxSyncRules.holesToSend(queued, alreadyOnServer = setOf(2))

        // Assert
        assertEquals(listOf(1, 3), toSend.map { it.holeNumber })
    }

    @Test
    fun `holesToSend returns nothing when every hole is already on the server`() {
        val toSend = OutboxSyncRules.holesToSend(listOf(hole(1), hole(2)), alreadyOnServer = setOf(1, 2))
        assertTrue(toSend.isEmpty())
    }

    @Test
    fun `holesToSend orders holes by number`() {
        val toSend = OutboxSyncRules.holesToSend(listOf(hole(9), hole(1), hole(5)), alreadyOnServer = emptySet())
        assertEquals(listOf(1, 5, 9), toSend.map { it.holeNumber })
    }

    @Test
    fun `no connection retries later`() {
        assertEquals(OutboxSyncRules.Outcome.RETRY_LATER, OutboxSyncRules.classify(ApiException("offline")))
    }

    @Test
    fun `expired session, timeouts, rate limits and server errors retry later`() {
        listOf(401, 408, 429, 500, 503).forEach { code ->
            assertEquals("HTTP $code", OutboxSyncRules.Outcome.RETRY_LATER, OutboxSyncRules.classify(ApiException("x", code)))
        }
    }

    @Test
    fun `client errors are rejected rather than retried forever`() {
        listOf(400, 403, 404, 409).forEach { code ->
            assertEquals("HTTP $code", OutboxSyncRules.Outcome.REJECTED, OutboxSyncRules.classify(ApiException("x", code)))
        }
    }
}
