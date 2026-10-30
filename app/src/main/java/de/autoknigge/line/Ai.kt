package de.autoknigge.line

import kotlin.random.Random

/** Computergegner: Alpha-Beta-Suche mit Zeitlimit; die Stufe bestimmt Tiefe und Fehlerquote. */
object Ai {
    private const val WIN = 1_000_000

    /** level: 0 = leicht, 1 = normal, 2 = stark. Gibt die gewählte Spalte zurück. */
    fun choose(game: Game, level: Int): Int {
        val cols = game.cols
        val rows = game.rows
        val need = game.need
        val b = Array(rows) { game.grid[it].copyOf() }
        val me = game.current + 1
        val order = (0 until cols).sortedBy { kotlin.math.abs(it - (cols - 1) / 2.0) }
        val legal = order.filter { b[0][it] == 0 }
        if (legal.size == 1) return legal[0]

        // sofort gewinnen oder sofort verhindern
        for (c in legal) if (wins(b, c, me, need)) return c
        val opp = 3 - me
        val blocks = legal.filter { wins(b, it, opp, need) }
        if (level == 0 && Random.nextInt(100) < 25) return legal.random()
        if (blocks.isNotEmpty()) return blocks[0]

        val maxDepth = when (level) { 0 -> 2; 1 -> if (need >= 5) 4 else 5; else -> if (need >= 5) 6 else 8 }
        val budget = when (level) { 0 -> 200L; 1 -> 700L; else -> 1800L }
        val deadline = System.currentTimeMillis() + budget
        var best = legal[0]
        var timeUp = false
        for (depth in 1..maxDepth) {
            var bestScore = Int.MIN_VALUE
            var bestHere = best
            var alpha = -WIN - 100
            val candidates = (listOf(best) + legal).distinct()
            for (c in candidates) {
                val r = drop(b, c, me)
                val s = if (isWin(b, r, c, need)) WIN + depth else
                    -search(b, opp, depth - 1, -WIN - 100, -alpha, need, me, deadline) { timeUp = true }
                undo(b, r, c)
                if (timeUp) break
                if (s > bestScore) { bestScore = s; bestHere = c }
                if (s > alpha) alpha = s
            }
            if (timeUp) break
            best = bestHere
            if (bestScore >= WIN) break
        }
        return if (level == 0 && Random.nextInt(100) < 15) legal.random() else best
    }

    private fun search(b: Array<IntArray>, turn: Int, depth: Int, alphaIn: Int, beta: Int, need: Int, me: Int, deadline: Long, stop: () -> Unit): Int {
        if (System.currentTimeMillis() > deadline) { stop(); return 0 }
        val cols = b[0].size
        if (depth == 0) return eval(b, me, need) * (if (turn == me) 1 else -1)
        var alpha = alphaIn
        var any = false
        var best = -WIN - 100
        for (k in 0 until cols) {
            val c = (cols - 1) / 2 + (if (k % 2 == 0) k / 2 else -(k + 1) / 2)
            if (c !in 0 until cols || b[0][c] != 0) continue
            any = true
            val r = drop(b, c, turn)
            val s = if (isWin(b, r, c, need)) WIN + depth else
                -search(b, 3 - turn, depth - 1, -beta, -alpha, need, me, deadline, stop)
            undo(b, r, c)
            if (s > best) best = s
            if (s > alpha) alpha = s
            if (alpha >= beta) break
        }
        return if (any) best else 0
    }

    private fun drop(b: Array<IntArray>, c: Int, p: Int): Int {
        var r = b.size - 1
        while (b[r][c] != 0) r--
        b[r][c] = p
        return r
    }

    private fun undo(b: Array<IntArray>, r: Int, c: Int) { b[r][c] = 0 }

    private fun wins(b: Array<IntArray>, c: Int, p: Int, need: Int): Boolean {
        if (b[0][c] != 0) return false
        val r = drop(b, c, p)
        val w = isWin(b, r, c, need)
        undo(b, r, c)
        return w
    }

    private fun isWin(b: Array<IntArray>, r: Int, c: Int, need: Int): Boolean {
        val p = b[r][c]
        val rows = b.size
        val cols = b[0].size
        for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
            var n = 1
            var rr = r + dr; var cc = c + dc
            while (rr in 0 until rows && cc in 0 until cols && b[rr][cc] == p) { n++; rr += dr; cc += dc }
            rr = r - dr; cc = c - dc
            while (rr in 0 until rows && cc in 0 until cols && b[rr][cc] == p) { n++; rr -= dr; cc -= dc }
            if (n >= need) return true
        }
        return false
    }

    /** Stellung aus Sicht von [me]: offene Reihen zählen, Mitte bevorzugen. */
    private fun eval(b: Array<IntArray>, me: Int, need: Int): Int {
        val rows = b.size
        val cols = b[0].size
        var score = 0
        val mid = (cols - 1) / 2
        for (r in 0 until rows) {
            val v = b[r][mid]
            if (v == me) score += 3 else if (v != 0) score -= 3
        }
        for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
            for (r in 0 until rows) for (c in 0 until cols) {
                val er = r + dr * (need - 1)
                val ec = c + dc * (need - 1)
                if (er !in 0 until rows || ec !in 0 until cols) continue
                var mine = 0
                var theirs = 0
                for (k in 0 until need) {
                    val v = b[r + dr * k][c + dc * k]
                    if (v == me) mine++ else if (v != 0) theirs++
                }
                if (mine > 0 && theirs > 0) continue
                score += weight(mine, need) - weight(theirs, need)
            }
        }
        return score
    }

    private fun weight(n: Int, need: Int): Int = when (need - n) {
        1 -> 60
        2 -> 8
        3 -> 2
        else -> 0
    }
}
