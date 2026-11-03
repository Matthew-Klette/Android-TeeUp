package com.teeup.android.data

/** Connect/read timeout for a normal API call. */
const val DEFAULT_TIMEOUT_MS = 10_000

/** Timeout for a retry. The Azure App Service sleeps when idle, and the first request after
 *  that can take well over 10 s while it starts up and opens its Supabase connection. */
const val COLD_START_TIMEOUT_MS = 30_000

/** Pause between attempts, so the retry doesn't land on the same half-started instance. */
const val RETRY_PAUSE_MS = 1_000L

private val GATEWAY_ERRORS = setOf(502, 503, 504)

fun timeoutForAttempt(attempt: Int): Int = if (attempt <= 1) DEFAULT_TIMEOUT_MS else COLD_START_TIMEOUT_MS

/** True for failures a sleeping or starting server produces: no answer at all (statusCode null)
 *  or a gateway error from Azure's front end. A real 4xx/500 answer is final. */
private fun isWakeUpFailure(e: ApiException): Boolean =
    e.statusCode == null || e.statusCode in GATEWAY_ERRORS

/**
 * Runs [block] and, if the API looks asleep, runs it again (up to [maxAttempts] in total).
 * [block] gets the 1-based attempt number so it can use [timeoutForAttempt].
 * Only use this for idempotent calls: a timed-out attempt may still have reached the server.
 * Blocking; call off the main thread.
 */
fun <T> retryWhileWakingUp(
    maxAttempts: Int = 2,
    pause: (Long) -> Unit = Thread::sleep,
    block: (attempt: Int) -> T
): T {
    var attempt = 1
    while (true) {
        try {
            return block(attempt)
        } catch (e: ApiException) {
            if (attempt >= maxAttempts || !isWakeUpFailure(e)) throw e
            pause(RETRY_PAUSE_MS)
            attempt++
        }
    }
}
