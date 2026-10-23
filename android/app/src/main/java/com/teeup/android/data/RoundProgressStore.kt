package com.teeup.android.data

import android.content.Context

/** Where the golfer left a round: the hole on screen, the layup target dragged on it, and the
 *  scores entered but not yet sent to the server. */
data class RoundProgress(val hole: Int, val target: GeoPoint?, val pending: List<HoleScoreInput>)

/** The round the Scorecard tab reopens until it's finished or deleted. */
data class ActiveRound(val teeTimeId: String, val holes: Int)

/** Plain-text encodings for [RoundProgressStore], kept separate so they're unit tested. Bad or
 *  partial input decodes to nothing rather than throwing, since it comes back from disk. */
object RoundProgressCodec {
    fun encodeEntries(entries: List<HoleScoreInput>): String =
        entries.joinToString(";") { "${it.holeNumber}:${it.strokes}:${it.putts}" }

    fun decodeEntries(text: String?): List<HoleScoreInput> =
        text.orEmpty().split(";").mapNotNull { part ->
            val numbers = part.split(":").map { it.toIntOrNull() }
            if (numbers.size != 3 || numbers.any { it == null }) return@mapNotNull null
            RoundScoring.entryOrNull(numbers[0]!!, numbers[1], numbers[2])
        }

    fun encodePoint(point: GeoPoint?): String? = point?.let { "${it.latitude},${it.longitude}" }

    fun decodePoint(text: String?): GeoPoint? {
        val parts = text?.split(",")?.map { it.toDoubleOrNull() } ?: return null
        if (parts.size != 2) return null
        val (latitude, longitude) = parts
        return if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
    }
}

/**
 * Keeps each round's [RoundProgress] on the device, so leaving the in-round map (another tab,
 * the app closed, the process killed) and coming back lands on the same hole with nothing lost.
 * Scores only go to the server when the round is finished, so until then this is their one copy.
 */
class RoundProgressStore(context: Context) {
    private companion object {
        const val PREFS = "round_progress"
        const val ACTIVE_TEE_TIME = "active.teeTimeId"
        const val ACTIVE_HOLES = "active.holes"
        fun key(teeTimeId: String, field: String) = "round.$teeTimeId.$field"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(teeTimeId: String): RoundProgress? {
        val hole = prefs.getInt(key(teeTimeId, "hole"), 0)
        if (hole == 0) return null
        return RoundProgress(
            hole = hole,
            target = RoundProgressCodec.decodePoint(prefs.getString(key(teeTimeId, "target"), null)),
            pending = RoundProgressCodec.decodeEntries(prefs.getString(key(teeTimeId, "pending"), null))
        )
    }

    fun save(teeTimeId: String, progress: RoundProgress) {
        prefs.edit()
            .putInt(key(teeTimeId, "hole"), progress.hole)
            .putString(key(teeTimeId, "target"), RoundProgressCodec.encodePoint(progress.target))
            .putString(key(teeTimeId, "pending"), RoundProgressCodec.encodeEntries(progress.pending))
            .apply()
    }

    fun activeRound(): ActiveRound? {
        val teeTimeId = prefs.getString(ACTIVE_TEE_TIME, null) ?: return null
        return ActiveRound(teeTimeId, prefs.getInt(ACTIVE_HOLES, 18))
    }

    fun setActive(round: ActiveRound) {
        prefs.edit().putString(ACTIVE_TEE_TIME, round.teeTimeId).putInt(ACTIVE_HOLES, round.holes).apply()
    }

    /** Forgets the round once it's finished or deleted, and stops the Scorecard tab reopening it. */
    fun clear(teeTimeId: String) {
        val editor = prefs.edit()
            .remove(key(teeTimeId, "hole"))
            .remove(key(teeTimeId, "target"))
            .remove(key(teeTimeId, "pending"))
        if (prefs.getString(ACTIVE_TEE_TIME, null) == teeTimeId) editor.remove(ACTIVE_TEE_TIME).remove(ACTIVE_HOLES)
        editor.apply()
    }
}
