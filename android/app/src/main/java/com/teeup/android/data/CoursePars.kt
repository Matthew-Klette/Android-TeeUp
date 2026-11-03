package com.teeup.android.data

/**
 * Hole-by-hole par for every course in the catalogue, from the clubs' published scorecards
 * (men's tees), checked against at least one independent scorecard listing where the club's own
 * card wasn't online (sources beside each course, accessed 3 Nov. 2026). Par sets the most
 * strokes a golfer may record on a hole ([HolePar.maxStrokes]). Keyed by course name, like
 * [HoleLayoutSource], since the API's row and MockCatalog's offline copy have different ids.
 * A 9-hole course has 9 pars; an 18-hole round on it plays the nine twice.
 */
object CoursePars {
    private val PARS: Map<String, IntArray> = mapOf(
        // Par 72 (medium confidence): https://www.golfpass.com/courses/18109-arabella-golf-club/scorecard-and-layout; https://www.hole19golf.com/courses/arabella-golf-course
        "Arabella Golf Club" to intArrayOf(4, 5, 4, 4, 3, 4, 3, 5, 4, 4, 4, 4, 5, 3, 4, 4, 3, 5),
        // Par 72 (high confidence): https://www.golfpass.com/courses/17913-durban-country-club-the-course-country-club/scorecard-and-layout; https://hole19golf.com/south-africa/durban-country-club-durban
        "Durban Country Club" to intArrayOf(4, 3, 5, 3, 4, 4, 4, 5, 4, 5, 4, 3, 4, 5, 3, 4, 4, 4),
        // Par 73 (high confidence): https://18birdies.com/golf-courses/club/bd7277d0-86ac-11e4-8c28-020000005b00/east-london-golf-club; https://www.golfpass.com/travel-advisor/courses/17749-east-london-golf-club
        "East London Golf Club" to intArrayOf(5, 3, 5, 4, 4, 4, 4, 4, 4, 3, 5, 4, 4, 4, 5, 4, 3, 4),
        // Par 72 (high confidence): https://www.golfpass.com/courses/18086-erinvale-golf-club/scorecard-and-layout; https://www.golfingrecord.com/golf-scorecard/4fe66951-bffc-4101-85e5-90313bad0a6e
        "Erinvale Golf Club" to intArrayOf(4, 3, 5, 4, 4, 4, 5, 3, 4, 4, 4, 3, 5, 3, 4, 5, 4, 4),
        // Par 73 (medium confidence): https://www.golfpass.com/courses/18117-fancourt-hotel-and-country-club-estate-the-links-course/scorecard-and-layout; https://compleatgolfer.com/?p=322
        "Fancourt Links" to intArrayOf(4, 3, 4, 4, 5, 4, 4, 3, 5, 4, 3, 4, 5, 4, 4, 5, 3, 5),
        // Par 72 (high confidence): https://www.golfpass.com/courses/18021-gary-player-country-club/scorecard-and-layout; https://www.hole19golf.com/courses/gary-player-country-club
        "Gary Player Country Club" to intArrayOf(4, 5, 4, 3, 4, 4, 3, 4, 5, 5, 4, 3, 4, 5, 4, 3, 4, 4),
        // Par 72 (high confidence): https://www.golfpass.com/courses/17725-humewood-golf-club/scorecard-and-layout; https://18birdies.com/golf-courses/club/bcc39a30-86ac-11e4-8c28-020000005b00/humewood-golf-club
        "Humewood Golf Club" to intArrayOf(4, 4, 3, 4, 4, 3, 5, 4, 4, 4, 5, 3, 4, 3, 5, 4, 5, 4),
        // Par 72 (high confidence): https://www.golfpass.com/courses/17994-leopard-creek-golf-club/scorecard-and-layout; https://hole19golf.com/courses/leopard-creek-country-club
        "Leopard Creek Country Club" to intArrayOf(4, 5, 4, 4, 3, 4, 3, 4, 4, 4, 4, 3, 5, 4, 5, 3, 4, 5),
        // Par 72 (medium confidence): https://18birdies.com/golf-courses/club/bcce96b0-86ac-11e4-8c28-020000005b00/little-walmer-golf-course; https://www.golfingrecord.com/golf-scorecard/fdeec6e0-d4e2-4b25-aa89-b0e56afc41ba
        "Little Walmer Golf Club" to intArrayOf(4, 4, 4, 3, 4, 3, 5, 5, 4, 4, 4, 4, 3, 4, 3, 5, 5, 4),
        // Par 72 (high confidence): https://www.pearlvalley.co.za/course-layout/; https://www.golfpass.com/courses/18143-pearl-valley-golf-country-estate/scorecard-and-layout
        "Pearl Valley Golf Estate" to intArrayOf(4, 4, 3, 5, 4, 3, 4, 4, 5, 4, 4, 5, 3, 5, 4, 4, 3, 4),
        // Par 72 (high confidence): https://pinnaclepointestate.co.za/course-overview-2/; https://www.golfpass.com/courses/18074-pinnacle-point-golf-club/scorecard-and-layout
        "Pinnacle Point Golf Estate" to intArrayOf(4, 4, 5, 4, 5, 4, 3, 4, 3, 4, 4, 4, 3, 4, 4, 5, 3, 5),
        // Par 72 (high confidence): https://18birdies.com/golf-courses/club/bccdac50-86ac-11e4-8c28-020000005b00/port-elizabeth-golf-club; https://www.golfpass.com/travel-advisor/courses/17719-port-elizabeth-golf-club; https://www.hole19golf.com/courses/port-elizabeth-golf-club
        "Port Elizabeth Golf Club" to intArrayOf(5, 3, 4, 4, 3, 4, 4, 5, 4, 4, 4, 4, 5, 3, 4, 4, 3, 5),
        // Par 72 (high confidence): https://www.golfpass.com/courses/17848-royal-johannesburg-kensington-golf-club-east-course/scorecard-and-layout; https://hole19golf.com/courses/royal-johannesburg-kensington-golf-club-east
        "Royal Johannesburg and Kensington Golf Club" to intArrayOf(5, 3, 4, 4, 3, 5, 4, 5, 4, 4, 4, 3, 4, 4, 4, 3, 4, 5),
        // Par 32 (medium confidence): https://www.golfpass.com/travel-advisor/courses/35074-sardinia-bay-golf-course/scorecard-and-layout; https://www.hole19golf.com/courses/sardinia-bay-golf-estate
        "Sardinia Bay Golf Estate" to intArrayOf(4, 3, 4, 3, 4, 4, 3, 3, 4),
        // Par 72 (high confidence): https://www.golfpass.com/courses/17910-selborne-country-club/scorecard-and-layout; https://www.hole19golf.com/courses/selborne-park-golf-estate
        "Selborne Golf Estate" to intArrayOf(5, 4, 5, 3, 4, 3, 4, 4, 4, 5, 3, 5, 4, 4, 3, 4, 4, 4),
        // Par 72 (medium confidence): https://www.golfpass.com/courses/17834-silver-lakes-country-club/scorecard-and-layout; https://hole19golf.com/courses/silver-lakes-golf-estate
        "Silver Lakes Golf Estate" to intArrayOf(4, 5, 3, 4, 4, 4, 5, 4, 3, 4, 4, 4, 3, 5, 4, 3, 4, 5),
        // Par 72 (high confidence): https://www.golfpass.com/courses/18093-simola-golf-and-country-estate/scorecard-and-layout; https://www.golfingrecord.com/golf-scorecard/e60ee1b8-ad55-b07f-6a7a-02e395b80b8e
        "Simola Golf Club" to intArrayOf(5, 4, 4, 4, 4, 3, 5, 4, 3, 4, 3, 5, 4, 3, 5, 4, 3, 5),
        // Par 72 (high confidence): https://18birdies.com/golf-courses/club/bcd12ec0-86ac-11e4-8c28-020000005b00/st-francis-links; https://hole19golf.com/courses/st-francis-links
        "St Francis Links" to intArrayOf(4, 4, 5, 3, 4, 5, 3, 4, 4, 4, 4, 4, 5, 3, 4, 5, 3, 4),
        // Par 72 (high confidence): https://steenberggolfclub.co.za/golf-course; https://www.golfpass.com/courses/18139-steenberg-golf-club/scorecard-and-layout
        "Steenberg Golf Club" to intArrayOf(4, 3, 4, 4, 5, 4, 3, 5, 4, 4, 4, 5, 4, 3, 4, 4, 3, 5),
        // Par 71 (high confidence): https://www.golfpass.com/courses/17817-wanderers-golf-club/scorecard-and-layout; https://hole19golf.com/courses/the-wanderers-golf-club
        "Wanderers Golf Club" to intArrayOf(5, 4, 4, 4, 4, 3, 4, 4, 4, 5, 4, 3, 5, 4, 3, 4, 3, 4),
        // Par 72 (high confidence): https://18birdies.com/golf-courses/club/5a6d5030-6d89-11e7-932b-0680a328ea36/wedgewood-golf-and-country-estate; https://www.golfpass.com/courses/17711-wedgewood-park-country-club/scorecard-and-layout
        "Wedgewood Golf Estate" to intArrayOf(4, 5, 3, 5, 4, 4, 3, 4, 4, 4, 4, 3, 4, 3, 5, 4, 4, 5),
        // Par 71 (medium confidence): https://www.golfpass.com/courses/17825-zwartkop-country-club/scorecard-and-layout; https://18birdies.com/golf-courses/club/bd36f570-86ac-11e4-8c28-020000005b00/zwartkop-country-club
        "Zwartkop Country Club" to intArrayOf(4, 4, 3, 4, 4, 3, 5, 3, 5, 4, 5, 3, 4, 4, 3, 5, 3, 5)
    )

    /** Names the deployed database used before infra/courses/seed-courses.sql renamed courses. */
    private val FORMER_NAMES = mapOf(
        "Little Walmer Golf Course" to "Little Walmer Golf Club",
        "Wedgewood Golf & Country Estate" to "Wedgewood Golf Estate"
    )

    /** The card for [courseName], or null if the course isn't in the catalogue. */
    fun cardFor(courseName: String?): IntArray? =
        courseName?.let { PARS[FORMER_NAMES[it] ?: it] }

    /** Par for [hole] (1-based), or null when the course's card isn't known. */
    fun parFor(courseName: String?, hole: Int): Int? {
        val card = cardFor(courseName) ?: return null
        if (hole < 1) return null
        return card[(hole - 1) % card.size]
    }

    /** Every course with a card, for tests and the catalogue check. */
    val courseNames: Set<String> get() = PARS.keys
}
