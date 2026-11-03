package com.teeup.android.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CrackPatternTest {
    private val width = 1080f
    private val height = 2232f
    private val impactX = width * 0.53f
    private val impactY = height * 0.33f
    private val coreRadius = 170f

    private fun pattern(seed: Long = 11L) =
        CrackPattern.generate(width, height, impactX, impactY, coreRadius, seed)

    @Test
    fun sameSeedGivesIdenticalShards() {
        val first = pattern().shards.map { it.points.toList() }
        val second = pattern().shards.map { it.points.toList() }

        assertEquals(first, second)
    }

    @Test
    fun everyShardIsAPolygonWithArea() {
        pattern().shards.forEach { shard ->
            assertTrue(shard.points.size >= 6)
            assertTrue("degenerate shard", abs(signedArea(shard.points)) > 1f)
        }
    }

    @Test
    fun coreShardsStayInsideTheCrushedCore() {
        val core = pattern().shards.filter { it.isCore }

        assertTrue(core.isNotEmpty())
        core.forEach { shard ->
            forEachPoint(shard.points) { x, y ->
                assertTrue(hypot(x - impactX, y - impactY) <= coreRadius * 1.25f)
            }
        }
    }

    @Test
    fun shardsTileTheWholeScreenWithoutGapsOrOverlaps() {
        val shards = pattern().shards
        var checked = 0

        for (gy in 0 until 60) {
            for (gx in 0 until 30) {
                val x = (gx + 0.37f) * width / 30f
                val y = (gy + 0.61f) * height / 60f
                if (shards.any { distanceToEdges(it.points, x, y) < 1f }) continue

                val owners = shards.count { contains(it.points, x, y) }
                assertEquals("point ($x, $y) covered by $owners shards", 1, owners)
                checked++
            }
        }
        assertTrue("too few points checked: $checked", checked > 1500)
    }

    @Test
    fun someConcentricCracksStopShortOfAFullRing() {
        // A plain web cell has 8 outline points; one spanning a skipped ring has more.
        val fused = pattern().shards.filter { !it.isCore && it.points.size / 2 > 8 }

        assertTrue(fused.isNotEmpty())
    }

    @Test
    fun distanceRankRunsFromImpactOutward() {
        val shards = pattern().shards.filterNot { it.isCore }
        val nearest = shards.minByOrNull { it.distanceRank }!!
        val farthest = shards.maxByOrNull { it.distanceRank }!!

        assertTrue(nearest.ring <= farthest.ring)
        assertTrue(shards.all { it.distanceRank in 0f..1f })
    }

    @Test
    fun microCracksSitAroundTheCoreRim() {
        val cracks = pattern().microCracks

        assertTrue(cracks.isNotEmpty())
        cracks.forEach { crack ->
            val start = hypot(crack[0] - impactX, crack[1] - impactY)
            assertTrue(start >= coreRadius * 0.95f && start <= coreRadius * 1.8f)
        }
    }

    private fun forEachPoint(points: FloatArray, block: (Float, Float) -> Unit) {
        for (i in points.indices step 2) block(points[i], points[i + 1])
    }

    private fun signedArea(points: FloatArray): Float {
        var area = 0f
        val n = points.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[2 * i] * points[2 * j + 1] - points[2 * j] * points[2 * i + 1]
        }
        return area / 2f
    }

    private fun contains(points: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = points.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = points[2 * i]
            val yi = points[2 * i + 1]
            val xj = points[2 * j]
            val yj = points[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun distanceToEdges(points: FloatArray, x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        val n = points.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            best = minOf(best, segmentDistance(x, y, points[2 * i], points[2 * i + 1], points[2 * j], points[2 * j + 1]))
        }
        return best
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
