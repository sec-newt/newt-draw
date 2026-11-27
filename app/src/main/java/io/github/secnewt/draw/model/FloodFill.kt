package io.github.secnewt.draw.model

/**
 * Works out what a fill-bucket tap covers, from the ink as pixels (ARGB, see-through where
 * there's no ink).
 *
 * 1. The area is every pixel connected to the tapped one that looks the same: empty paper if
 *    you tapped empty paper, or the same color if you tapped a color.
 * 2. Finger lines rarely close exactly. To keep the fill from leaking out through a small
 *    gap, the lines are first thickened by [GAP] pixels on each side, which closes gaps up
 *    to twice that wide, and the area is found inside the thickened lines.
 * 3. Then the area grows back out by the same amount (never through a line), so it
 *    reaches the real lines, and narrow parts that are closed off (a thin arm or tail) are
 *    added back. A tap right next to a line starts from the nearest wide-open spot.
 *
 * Filling empty paper goes *behind* the ink, one pixel under the lines too, so the lines
 * stay crisp on top with no white edge between line and fill.
 */
object FloodFill {
    /** Gaps up to twice this (in paper pixels) count as closed. */
    const val GAP = 12

    /** Colors this close (out of 255 per channel) count as the same. */
    private const val SAME = 40

    sealed interface Result {
        class Filled(val fill: Fill) : Result

        /** The tapped area is already that color. */
        data object AlreadyThatColor : Result

        /** The tap wasn't on the paper. */
        data object OffPaper : Result
    }

    fun fill(pixels: IntArray, width: Int, height: Int, x: Int, y: Int, color: Int, gap: Int = GAP): Result {
        if (x !in 0 until width || y !in 0 until height) return Result.OffPaper
        val n = width * height
        require(pixels.size == n) { "Expected $n pixels" }
        val seed = pixels[y * width + x]
        val onEmpty = alpha(seed) < 128
        if (!onEmpty && sameColor(seed, color)) return Result.AlreadyThatColor

        // open: pixels that look like the tapped one.
        val open = BooleanArray(n) { looksLike(pixels[it], seed, onEmpty) }

        // Distance (in pixels, up to a cap) from each pixel to the nearest line.
        val core = if (gap > 0) {
            val distance = distanceToWalls(open, width, height, cap = gap + 1)
            BooleanArray(n) { open[it] && distance[it] > gap }
        } else {
            open
        }
        val inside = BooleanArray(n)
        // Tapped close to a line, the tap itself is in the thickened lines: start from the
        // nearest wide-open spot instead, so gap protection still applies.
        val start = if (gap > 0) nearestCore(core, open, width, height, y * width + x, steps = gap + 1) else -1
        if (start >= 0) {
            scanlineFill(core, inside, width, height, start % width, start / width)
            addClosedNarrowParts(inside, core, open, width, height)
            growWithin(inside, open, width, height, steps = gap + 1)
        } else {
            // No wide-open spot nearby: a small area, filled as it is.
            scanlineFill(open, inside, width, height, x, y)
        }
        if (onEmpty) growWithin(inside, BooleanArray(n) { true }, width, height, steps = 1)
        return Result.Filled(Fill(spans(inside, width, height), color, behind = onEmpty))
    }

    /**
     * The nearest [core] pixel to [from], at most [steps] moves away through [open] pixels,
     * or -1 if there isn't one.
     */
    private fun nearestCore(core: BooleanArray, open: BooleanArray, width: Int, height: Int, from: Int, steps: Int): Int {
        if (core[from]) return from
        val seen = HashSet<Int>()
        var frontier = listOf(from)
        seen.add(from)
        repeat(steps) {
            val next = ArrayList<Int>()
            for (i in frontier) {
                val x = i % width
                val y = i / width
                for ((dx, dy) in FOUR) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val j = ny * width + nx
                    if (!open[j] || !seen.add(j)) continue
                    if (core[j]) return j
                    next.add(j)
                }
            }
            frontier = next
        }
        return -1
    }

    /**
     * Thickening the lines also shuts off narrow parts of the shape (a thin arm or tail),
     * not just gaps. A narrow part is added back if it's closed off: it touches the tapped
     * area's wide-open middle and no other. One that reaches another wide-open area (through
     * a gap to the outside, say) stays out, so gaps still don't leak.
     */
    private fun addClosedNarrowParts(inside: BooleanArray, core: BooleanArray, open: BooleanArray, width: Int, height: Int) {
        val n = width * height
        val visited = BooleanArray(n)
        val part = IntStack()
        val queue = IntStack()
        for (i in 0 until n) {
            if (!inside[i]) continue
            val x = i % width
            val y = i / width
            for ((dx, dy) in FOUR) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val j = ny * width + nx
                if (!open[j] || core[j] || visited[j]) continue
                // Collect this narrow part and see what wide areas it touches.
                part.clear()
                queue.clear()
                visited[j] = true
                queue.push(j)
                var reachesElsewhere = false
                while (queue.isNotEmpty()) {
                    val k = queue.pop()
                    part.push(k)
                    val kx = k % width
                    val ky = k / width
                    for ((ex, ey) in FOUR) {
                        val mx = kx + ex
                        val my = ky + ey
                        if (mx !in 0 until width || my !in 0 until height) continue
                        val m = my * width + mx
                        if (!open[m]) continue
                        if (core[m]) {
                            if (!inside[m]) reachesElsewhere = true
                        } else if (!visited[m]) {
                            visited[m] = true
                            queue.push(m)
                        }
                    }
                }
                if (!reachesElsewhere) part.forEach { inside[it] = true }
            }
        }
    }

    private val FOUR = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private fun alpha(c: Int) = c ushr 24

    private fun sameColor(a: Int, b: Int): Boolean {
        if (kotlin.math.abs(alpha(a) - alpha(b)) > SAME) return false
        for (shift in intArrayOf(16, 8, 0)) {
            if (kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) > SAME) return false
        }
        return true
    }

    private fun looksLike(pixel: Int, seed: Int, onEmpty: Boolean) =
        if (onEmpty) alpha(pixel) < 128 else alpha(pixel) >= 128 && sameColor(pixel, seed)

    /**
     * Chessboard distance to the nearest closed pixel, capped at [cap]. Off the paper
     * counts as open, so a fill can reach the paper's edge.
     */
    private fun distanceToWalls(open: BooleanArray, width: Int, height: Int, cap: Int): ByteArray {
        val d = ByteArray(width * height) { if (open[it]) cap.toByte() else 0 }
        fun at(x: Int, y: Int) = if (x in 0 until width && y in 0 until height) d[y * width + x].toInt() else cap
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                if (d[i].toInt() == 0) continue
                val best = minOf(at(x - 1, y), at(x - 1, y - 1), at(x, y - 1), at(x + 1, y - 1)) + 1
                if (best < d[i]) d[i] = best.toByte()
            }
        }
        for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                val i = y * width + x
                if (d[i].toInt() == 0) continue
                val best = minOf(at(x + 1, y), at(x + 1, y + 1), at(x, y + 1), at(x - 1, y + 1)) + 1
                if (best < d[i]) d[i] = best.toByte()
            }
        }
        return d
    }

    /** Marks in [inside] every [open] pixel 4-connected to (x, y), a row at a time. */
    private fun scanlineFill(open: BooleanArray, inside: BooleanArray, width: Int, height: Int, x: Int, y: Int) {
        val stack = IntStack()
        stack.push(y * width + x)
        while (stack.isNotEmpty()) {
            val start = stack.pop()
            if (inside[start] || !open[start]) continue
            val row = start / width
            var left = start % width
            var right = left
            while (left > 0 && open[row * width + left - 1] && !inside[row * width + left - 1]) left--
            while (right < width - 1 && open[row * width + right + 1] && !inside[row * width + right + 1]) right++
            for (cx in left..right) inside[row * width + cx] = true
            for (next in intArrayOf(row - 1, row + 1)) {
                if (next !in 0 until height) continue
                var cx = left
                while (cx <= right) {
                    val i = next * width + cx
                    if (open[i] && !inside[i]) {
                        stack.push(i)
                        // Skip the rest of this run; the pushed pixel's fill covers it.
                        while (cx <= right && open[next * width + cx] && !inside[next * width + cx]) cx++
                    } else {
                        cx++
                    }
                }
            }
        }
    }

    /**
     * Grows [inside] by up to [steps] pixels (8 directions) into [open] pixels only. Diagonal
     * steps need an open pixel beside them, so growth never squeezes through a corner.
     */
    private fun growWithin(inside: BooleanArray, open: BooleanArray, width: Int, height: Int, steps: Int) {
        var frontier = IntStack()
        for (i in inside.indices) {
            if (!inside[i]) continue
            val x = i % width
            val y = i / width
            if ((x > 0 && !inside[i - 1]) || (x < width - 1 && !inside[i + 1]) ||
                (y > 0 && !inside[i - width]) || (y < height - 1 && !inside[i + width])
            ) {
                frontier.push(i)
            }
        }
        repeat(steps) {
            val next = IntStack()
            while (frontier.isNotEmpty()) {
                val i = frontier.pop()
                val x = i % width
                val y = i / width
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val j = ny * width + nx
                    if (inside[j] || !open[j]) continue
                    // A diagonal step needs an open pixel beside it, so the fill can't slip
                    // between two pixels that only touch at a corner.
                    if (dx != 0 && dy != 0 && !open[y * width + nx] && !open[ny * width + x]) continue
                    inside[j] = true
                    next.push(j)
                }
            }
            frontier = next
        }
    }

    private fun spans(inside: BooleanArray, width: Int, height: Int): IntArray {
        val out = IntStack()
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                if (!inside[y * width + x]) {
                    x++
                    continue
                }
                val first = x
                while (x < width && inside[y * width + x]) x++
                out.push(y)
                out.push(first)
                out.push(x - 1)
            }
        }
        return out.toArray()
    }

    /** A growable stack of ints, without boxing. */
    private class IntStack {
        var items = IntArray(1024)
            private set
        var size = 0
            private set

        fun push(v: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = v
        }

        fun pop() = items[--size]

        fun isNotEmpty() = size > 0

        fun toArray() = items.copyOf(size)

        fun clear() {
            size = 0
        }

        inline fun forEach(action: (Int) -> Unit) {
            for (i in 0 until size) action(items[i])
        }
    }
}
