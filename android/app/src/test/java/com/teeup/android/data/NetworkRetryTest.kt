package com.teeup.android.data

import org.junit.Assert.*
import org.junit.Test

class NetworkRetryTest {
    private val pauses = mutableListOf<Long>()
    private val recordPause: (Long) -> Unit = { pauses += it }

    @Test fun returnsFirstResultWithoutRetrying() {
        val attempts = mutableListOf<Int>()
        val result = retryWhileWakingUp(pause = recordPause) { attempt -> attempts += attempt; "ok" }
        assertEquals("ok", result)
        assertEquals(listOf(1), attempts)
        assertTrue(pauses.isEmpty())
    }

    @Test fun retriesOnceWhenServerUnreachableThenSucceeds() {
        val attempts = mutableListOf<Int>()
        val result = retryWhileWakingUp(pause = recordPause) { attempt ->
            attempts += attempt
            if (attempt == 1) throw ApiException("Could not connect") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf(1, 2), attempts)
        assertEquals(listOf(RETRY_PAUSE_MS), pauses)
    }

    @Test fun retriesGatewayErrorsFromColdStart() {
        listOf(502, 503, 504).forEach { code ->
            var calls = 0
            val result = retryWhileWakingUp(pause = recordPause) {
                calls++
                if (calls == 1) throw ApiException("gateway", code) else "ok"
            }
            assertEquals("HTTP $code", "ok", result)
            assertEquals("HTTP $code", 2, calls)
        }
    }

    @Test fun doesNotRetryClientOrServerErrors() {
        listOf(400, 401, 403, 404, 409, 500).forEach { code ->
            var calls = 0
            val error = assertThrows(ApiException::class.java) {
                retryWhileWakingUp(pause = recordPause) { calls++; throw ApiException("nope", code) }
            }
            assertEquals(code, error.statusCode)
            assertEquals("HTTP $code", 1, calls)
        }
        assertTrue(pauses.isEmpty())
    }

    @Test fun givesUpAfterMaxAttemptsAndRethrowsLastError() {
        var calls = 0
        val error = assertThrows(ApiException::class.java) {
            retryWhileWakingUp(maxAttempts = 2, pause = recordPause) {
                calls++
                throw ApiException("attempt $calls")
            }
        }
        assertEquals(2, calls)
        assertEquals("attempt 2", error.message)
    }

    @Test fun nonApiExceptionsAreNotRetried() {
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            retryWhileWakingUp(pause = recordPause) { calls++; throw IllegalStateException("bug") }
        }
        assertEquals(1, calls)
    }

    @Test fun laterAttemptsGetTheLongerColdStartTimeout() {
        assertEquals(DEFAULT_TIMEOUT_MS, timeoutForAttempt(1))
        assertEquals(COLD_START_TIMEOUT_MS, timeoutForAttempt(2))
        assertTrue(COLD_START_TIMEOUT_MS > DEFAULT_TIMEOUT_MS)
    }
}
