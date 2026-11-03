package com.teeup.android.ui.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * One piece of the broken screen as a closed polygon (x0, y0, x1, y1, …) in
 * view pixels. [ring] counts outward from the impact (-1 = the crushed core
 * right under the ball); [distanceRank] is 0 at the impact and 1 at the
 * farthest shard.
 */
internal class ShardShape(
    val points: FloatArray,
    val ring: Int,
    val isCore: Boolean,
    val distanceRank: Float,
) {
    val centroidX: Float get() = points.filterIndexed { i, _ -> i % 2 == 0 }.average().toFloat()
    val centroidY: Float get() = points.filterIndexed { i, _ -> i % 2 == 1 }.average().toFloat()
}

/**
 * Spider-web fracture of a screen: radial cracks out from the impact, crossed
 * by concentric cracks that only sometimes span a whole sector, with every
 * shared edge kinked once so it reads as glass rather than a dartboard.
 * Shards tile the whole view with no gaps, which the flip-to-login reveal
 * depends on. Plain Kotlin (no android.graphics) so the geometry is
 * unit-testable.
 */
internal class CrackPattern private constructor(
    val shards: List<ShardShape>,
    /** Short hairline cracks around the crushed core, each a 3-point polyline. */
    val microCracks: List<FloatArray>,
) {
    companion object {
        private const val RADIAL_COUNT = 14
        private const val RING_GROWTH_MIN = 1.7f
        private const val RING_GROWTH_MAX = 2.0f
        private const val COVER_MARGIN = 1.35f
        private const val ANGLE_JITTER = 0.22f
        private const val RING_WANDER = 0.05f
        private const val RADIUS_JITTER = 0.1f
        private const val CORE_RADIUS_JITTER = 0.18f
        private const val KINK = 0.06f
        private const val SPLIT_CHANCE = 0.55f
        private const val RING_SKIP_CHANCE = 0.4f
        private const val MICRO_CRACK_COUNT = 36

        fun generate(
            width: Float,
            height: Float,
            impactX: Float,
            impactY: Float,
            coreRadius: Float,
            seed: Long = 11L,
        ): CrackPattern {
            val rnd = Random(seed)
            val reach = maxOf(
                hypot(impactX, impactY),
                hypot(width - impactX, impactY),
                hypot(impactX, height - impactY),
                hypot(width - impactX, height - impactY),
            )
            val radii = ringRadii(coreRadius, reach * COVER_MARGIN, rnd)
            val vertices = ringVertices(impactX, impactY, radii, rnd)
            val lastRing = radii.lastIndex

            val ringMids = List(radii.size) { k ->
                List(RADIAL_COUNT) { i ->
                    val a = vertices[k][i]
                    val b = vertices[k][(i + 1) % RADIAL_COUNT]
                    kinkedMidpoint(a, b, if (k == lastRing) 0f else KINK, rnd)
                }
            }
            val radialMids = List(lastRing) { k ->
                List(RADIAL_COUNT) { i -> kinkedMidpoint(vertices[k][i], vertices[k + 1][i], KINK, rnd) }
            }

            val pieces = mutableListOf<Triple<FloatArray, Int, Boolean>>()
            pieces += corePieces(impactX, impactY, vertices[0], ringMids[0], rnd)
            for (i in 0 until RADIAL_COUNT) {
                val i1 = (i + 1) % RADIAL_COUNT
                // Concentric cracks rarely close a full circle: past the first ring, some
                // sectors skip a ring and their two cells fuse into one longer shard.
                val kept = (0..lastRing).filter { k -> k <= 1 || k == lastRing || rnd.nextFloat() >= RING_SKIP_CHANCE }
                kept.zipWithNext { k0, k1 ->
                    if (k1 == k0 + 1) {
                        val cell = Cell(
                            a = vertices[k0][i], ringMidInner = ringMids[k0][i], b = vertices[k0][i1],
                            radialMidRight = radialMids[k0][i1], c = vertices[k1][i1],
                            ringMidOuter = ringMids[k1][i], d = vertices[k1][i], radialMidLeft = radialMids[k0][i],
                        )
                        cell.split(k0 >= 1 && rnd.nextFloat() < SPLIT_CHANCE, rnd.nextBoolean())
                            .forEach { pieces += Triple(it, k0, false) }
                    } else {
                        pieces += Triple(fusedCell(vertices, ringMids, radialMids, i, k0, k1), k0, false)
                    }
                }
            }

            val distances = pieces.map { (pts, _, _) -> centroidDistance(pts, impactX, impactY) }
            val farthest = distances.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val shards = pieces.mapIndexed { index, (pts, ring, core) ->
                ShardShape(pts, if (core) -1 else ring, core, distances[index] / farthest)
            }
            return CrackPattern(shards, microCracks(impactX, impactY, coreRadius, rnd))
        }

        private fun ringRadii(coreRadius: Float, cover: Float, rnd: Random): List<Float> {
            val radii = mutableListOf(coreRadius)
            while (radii.last() * RING_GROWTH_MIN < cover) {
                radii += radii.last() * (RING_GROWTH_MIN + rnd.nextFloat() * (RING_GROWTH_MAX - RING_GROWTH_MIN))
            }
            radii += max(radii.last() * RING_GROWTH_MIN, cover)
            return radii
        }

        private fun ringVertices(cx: Float, cy: Float, radii: List<Float>, rnd: Random): List<List<Point>> {
            val sector = 2f * PI.toFloat() / RADIAL_COUNT
            val baseAngles = List(RADIAL_COUNT) { i -> i * sector + rnd.signed() * ANGLE_JITTER * sector }
            return radii.mapIndexed { k, radius ->
                val isOuter = k == radii.lastIndex
                baseAngles.map { base ->
                    val angle = if (isOuter) base else base + rnd.signed() * RING_WANDER
                    // The crushed core gets the roughest rim so it never reads as a clean disc.
                    val jitter = if (k == 0) CORE_RADIUS_JITTER else RADIUS_JITTER
                    val r = if (isOuter) radius else radius * (1f + rnd.signed() * jitter)
                    Point(cx + r * cos(angle), cy + r * sin(angle))
                }
            }
        }

        /** A cell spanning rings [k0]..[k1] of sector [i], walking every vertex on both radials. */
        private fun fusedCell(
            vertices: List<List<Point>>,
            ringMids: List<List<Point>>,
            radialMids: List<List<Point>>,
            i: Int,
            k0: Int,
            k1: Int,
        ): FloatArray {
            val i1 = (i + 1) % RADIAL_COUNT
            val outline = mutableListOf(vertices[k0][i], ringMids[k0][i], vertices[k0][i1])
            for (k in k0 until k1) outline += listOf(radialMids[k][i1], vertices[k + 1][i1])
            outline += listOf(ringMids[k1][i], vertices[k1][i])
            for (k in k1 - 1 downTo k0) {
                outline += radialMids[k][i]
                if (k > k0) outline += vertices[k][i]
            }
            return polygon(*outline.toTypedArray())
        }

        /** The crushed disc under the ball: each sector split into an inner chip and an outer chip. */
        private fun corePieces(
            cx: Float,
            cy: Float,
            rim: List<Point>,
            rimMids: List<Point>,
            rnd: Random,
        ): List<Triple<FloatArray, Int, Boolean>> {
            val impact = Point(cx, cy)
            val splits = rim.map { p ->
                val f = 0.4f + rnd.nextFloat() * 0.2f
                Point(cx + (p.x - cx) * f, cy + (p.y - cy) * f)
            }
            return (0 until RADIAL_COUNT).flatMap { i ->
                val i1 = (i + 1) % RADIAL_COUNT
                listOf(
                    Triple(polygon(impact, splits[i], splits[i1]), -1, true),
                    Triple(polygon(splits[i], rim[i], rimMids[i], rim[i1], splits[i1]), -1, true),
                )
            }
        }

        private fun microCracks(cx: Float, cy: Float, coreRadius: Float, rnd: Random): List<FloatArray> =
            List(MICRO_CRACK_COUNT) {
                val angle = rnd.nextFloat() * 2f * PI.toFloat()
                val start = coreRadius * (1f + rnd.nextFloat() * 0.7f)
                val length = coreRadius * (0.15f + rnd.nextFloat() * 0.3f)
                val heading = angle + rnd.signed() * 1.4f
                val x0 = cx + start * cos(angle)
                val y0 = cy + start * sin(angle)
                val kink = heading + rnd.signed() * 0.5f
                val x1 = x0 + length * 0.5f * cos(heading)
                val y1 = y0 + length * 0.5f * sin(heading)
                floatArrayOf(x0, y0, x1, y1, x1 + length * 0.5f * cos(kink), y1 + length * 0.5f * sin(kink))
            }

        private fun kinkedMidpoint(a: Point, b: Point, kink: Float, rnd: Random): Point {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val offset = rnd.signed() * kink
            return Point((a.x + b.x) / 2f - dy * offset, (a.y + b.y) / 2f + dx * offset)
        }

        private fun centroidDistance(points: FloatArray, cx: Float, cy: Float): Float {
            val n = points.size / 2
            val x = (0 until n).sumOf { points[2 * it].toDouble() } / n
            val y = (0 until n).sumOf { points[2 * it + 1].toDouble() } / n
            return hypot(x.toFloat() - cx, y.toFloat() - cy)
        }

        private fun Random.signed(): Float = nextFloat() * 2f - 1f

        private fun polygon(vararg points: Point): FloatArray =
            FloatArray(points.size * 2) { i -> if (i % 2 == 0) points[i / 2].x else points[i / 2].y }
    }

    private data class Point(val x: Float, val y: Float)

    /** One web cell between two radials and two rings, corners a→b→c→d. */
    private class Cell(
        val a: Point, val ringMidInner: Point, val b: Point, val radialMidRight: Point,
        val c: Point, val ringMidOuter: Point, val d: Point, val radialMidLeft: Point,
    ) {
        fun split(shouldSplit: Boolean, alongRadial: Boolean): List<FloatArray> = when {
            !shouldSplit -> listOf(polygon(a, ringMidInner, b, radialMidRight, c, ringMidOuter, d, radialMidLeft))
            alongRadial -> listOf(
                polygon(a, ringMidInner, ringMidOuter, d, radialMidLeft),
                polygon(ringMidInner, b, radialMidRight, c, ringMidOuter),
            )
            else -> listOf(
                polygon(a, ringMidInner, b, radialMidRight, radialMidLeft),
                polygon(radialMidLeft, radialMidRight, c, ringMidOuter, d),
            )
        }
    }
}
