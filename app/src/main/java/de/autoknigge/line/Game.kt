package de.autoknigge.line

enum class Kind { HUMAN, COMPUTER }

/**
 * Spielregeln für "n in einer Reihe". Spieler 0 und 1; die Steine fallen in die tiefste freie Zeile der Spalte.
 * Zeile 0 ist oben, Zeile rows-1 unten.
 */
class Game(val cols: Int, val rows: Int, val need: Int) {
    /** 0 = leer, 1 = Spieler 0, 2 = Spieler 1 */
    val grid = Array(rows) { IntArray(cols) }
    val moves = ArrayList<Int>()
    var current = 0
        private set
    var first = 0
    var winner = -1
        private set
    var draw = false
        private set
    var winLine: List<Pair<Int, Int>> = emptyList()
        private set

    companion object {
        fun sizeFor(need: Int): Pair<Int, Int> = if (need >= 5) 9 to 7 else 7 to 6
    }

    val finished get() = winner >= 0 || draw

    fun canDrop(col: Int) = col in 0 until cols && grid[0][col] == 0

    fun rowFor(col: Int): Int {
        for (r in rows - 1 downTo 0) if (grid[r][col] == 0) return r
        return -1
    }

    /** Wirft einen Stein ein und gibt die Zeile zurück, oder -1 wenn die Spalte voll ist oder das Spiel endete. */
    fun drop(col: Int): Int {
        if (finished || !canDrop(col)) return -1
        val r = rowFor(col)
        grid[r][col] = current + 1
        moves.add(col)
        val line = lineAt(r, col)
        if (line != null) {
            winner = current
            winLine = line
        } else if (moves.size == rows * cols) {
            draw = true
        } else {
            current = 1 - current
        }
        return r
    }

    /** Nimmt den letzten Zug zurück. */
    fun undo(): Boolean {
        if (moves.isEmpty()) return false
        val col = moves.removeAt(moves.size - 1)
        var r = 0
        while (r < rows && grid[r][col] == 0) r++
        val who = grid[r][col] - 1
        grid[r][col] = 0
        current = who
        winner = -1
        draw = false
        winLine = emptyList()
        return true
    }

    /** Beginn einer neuen Runde (gleiche Einstellungen). */
    fun reset(starter: Int) {
        for (row in grid) row.fill(0)
        moves.clear()
        first = starter
        current = starter
        winner = -1
        draw = false
        winLine = emptyList()
    }

    private fun lineAt(r: Int, c: Int): List<Pair<Int, Int>>? {
        val me = grid[r][c]
        for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
            val cells = ArrayList<Pair<Int, Int>>()
            cells.add(r to c)
            var rr = r + dr
            var cc = c + dc
            while (rr in 0 until rows && cc in 0 until cols && grid[rr][cc] == me) {
                cells.add(rr to cc); rr += dr; cc += dc
            }
            rr = r - dr
            cc = c - dc
            while (rr in 0 until rows && cc in 0 until cols && grid[rr][cc] == me) {
                cells.add(rr to cc); rr -= dr; cc -= dc
            }
            if (cells.size >= need) return cells
        }
        return null
    }
}
