package com.teeup.android.data

/**
 * The most strokes a golfer may record on a hole: par + [EXTRA_STROKES] (a triple bogey), putts
 * included. There's no separate putt limit: a golfer on a par 4 green in 2 may putt until they
 * reach 7. Each hole's par comes from the club's scorecard ([CoursePars]).
 */
object HolePar {
    const val EXTRA_STROKES = 3
    /** Pars the score sheet offers when a course's card isn't in [CoursePars]. */
    val OPTIONS = listOf(3, 4, 5)

    fun maxStrokes(par: Int): Int = par + EXTRA_STROKES
}

/** The outcome of checking a score entered for one hole; each failure carries what the golfer
 *  needs to be told. */
sealed interface ScoreCheck {
    data class Valid(val entry: HoleScoreInput) : ScoreCheck
    /** A number is missing, zero strokes, or negative putts. */
    data object Invalid : ScoreCheck
    /** The course's card isn't known and the golfer hasn't picked the hole's par yet. */
    data object ParMissing : ScoreCheck
    data class TooManyStrokes(val par: Int, val max: Int) : ScoreCheck
    /** Putts are strokes too, so there can't be more of them than [strokes]. */
    data class PuttsExceedStrokes(val strokes: Int) : ScoreCheck
}
