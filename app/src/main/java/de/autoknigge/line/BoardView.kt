package de.autoknigge.line

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.BounceInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * Spielbrett als Canvas: Die Steine liegen hinter dem Brett und sind nur durch die Löcher zu sehen.
 * Designs: 0 klassisch (blau), 1 dunkles Holz, 2 helles Holz, 3 roter Granit, 4 Sandstein.
 */
class BoardView(context: Context) : View(context) {

    companion object {
        const val DESIGNS = 5
        val DESIGN_NAMES = arrayOf("A (klassisch, blau)", "B (dunkles Holz)", "C (helles Holz)", "D (roter Granit)", "E (Sandstein)")
        val PIECE_NAMES = arrayOf(
            arrayOf("Rot", "Gelb"),
            arrayOf("Kirsche", "Ahorn"),
            arrayOf("Kirsche", "Nussbaum"),
            arrayOf("Marmor", "Basalt"),
            arrayOf("Porphyr", "Basalt")
        )
        /** Farben für Punkte und Rahmen in der Oberfläche. */
        val PIECE_COLORS = arrayOf(
            intArrayOf(0xFFD94B45.toInt(), 0xFFF1C232.toInt()),
            intArrayOf(0xFFB5502D.toInt(), 0xFFE9CB93.toInt()),
            intArrayOf(0xFFB5502D.toInt(), 0xFF6B4528.toInt()),
            intArrayOf(0xFFEFEBE0.toInt(), 0xFF59647A.toInt()),
            intArrayOf(0xFFB5493F.toInt(), 0xFF59647A.toInt())
        )
        private val PIECE_RES = arrayOf(
            intArrayOf(0, 0),
            intArrayOf(R.drawable.wood_kirsche, R.drawable.wood_ahorn),
            intArrayOf(R.drawable.wood_kirsche, R.drawable.wood_nussbaum),
            intArrayOf(R.drawable.stone_marmor, R.drawable.stone_basalt),
            intArrayOf(R.drawable.stone_porphyr, R.drawable.stone_basalt)
        )
        private val BOARD_RES = intArrayOf(0, R.drawable.wood_board_d, R.drawable.wood_board_hell, R.drawable.stone_granit, R.drawable.stone_sand_board)
    }

    var game: Game? = null
        set(v) {
            field = v
            hidden = null
            invalidate()
        }

    var style = 0
        set(v) {
            field = v
            invalidate()
        }

    /** Wird mit der Spalte aufgerufen, wenn der Finger über einer Spalte losgelassen wird. */
    var onColumn: ((Int) -> Unit)? = null
    var inputEnabled = true
        set(v) {
            field = v
            if (!v) { hover = -1; invalidate() }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val bitmaps = HashMap<Int, Bitmap>()
    private var hover = -1
    private var hidden: Pair<Int, Int>? = null
    private var fallPlayer = 0
    private var fallY = 0f
    private var fallCol = 0
    private var falling = false
    private var pulse = 0f
    private var reducedMotion = false
    private var dropAnim: ValueAnimator? = null
    private val pulser = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 700
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            pulse = it.animatedValue as Float
            val g = game
            if (g != null && g.winLine.isNotEmpty()) invalidate()
        }
    }

    // Geometrie
    private var cs = 0f
    private var bl = 0f
    private var bt = 0f
    private var pad = 0f

    private fun layout() {
        val g = game ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        cs = min(w / (g.cols + 0.7f), h / (g.rows + 0.7f + 1.1f))
        pad = cs * 0.22f
        val plateH = g.rows * cs + 2 * pad
        val total = plateH + cs * 1.1f
        bl = (w - g.cols * cs) / 2f
        bt = (h - total) / 2f + cs * 1.1f + pad
    }

    private fun cx(col: Int) = bl + (col + 0.5f) * cs
    private fun cy(row: Int) = bt + (row + 0.5f) * cs

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reducedMotion = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
        pulser.start()
    }

    override fun onDetachedFromWindow() {
        pulser.cancel()
        dropAnim?.cancel()
        super.onDetachedFromWindow()
    }

    /** Lässt einen Stein in [col] bis [row] fallen; [done] wird nach der Landung aufgerufen. */
    fun animateDrop(col: Int, row: Int, player: Int, done: () -> Unit) {
        layout()
        dropAnim?.cancel()
        hidden = row to col
        fallPlayer = player
        fallCol = col
        falling = true
        val startY = bt - pad - cs * 0.55f
        val endY = cy(row)
        if (reducedMotion || cs == 0f) {
            falling = false
            hidden = null
            invalidate()
            done()
            return
        }
        dropAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L + row * 75L
            interpolator = BounceInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                fallY = startY + (endY - startY) * t
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    falling = false
                    hidden = null
                    invalidate()
                    if (!cancelled) done()
                }
            })
            start()
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    private fun bmp(res: Int): Bitmap = bitmaps.getOrPut(res) { BitmapFactory.decodeResource(resources, res) }

    private fun tex(res: Int, scale: Float, dx: Float, dy: Float): BitmapShader {
        val sh = BitmapShader(bmp(res), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(dx, dy)
        sh.setLocalMatrix(m)
        return sh
    }

    private fun shade(col: Int, f: Float): Int = Color.rgb(
        (Color.red(col) * f).toInt().coerceIn(0, 255),
        (Color.green(col) * f).toInt().coerceIn(0, 255),
        (Color.blue(col) * f).toInt().coerceIn(0, 255)
    )

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        layout()
        if (cs <= 0f) return
        val left = bl - pad
        val top = bt - pad
        val right = bl + g.cols * cs + pad
        val bottom = bt + g.rows * cs + pad
        val corner = cs * 0.45f

        // Rückwand hinter den Löchern
        rect.set(left, top, right, bottom)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = if (style == 0) 0xFF0A1A33.toInt() else 0xFF120A06.toInt()
        canvas.drawRoundRect(rect, corner, corner, paint)

        // Steine (liegen hinter dem Brett)
        val hid = hidden
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val v = g.grid[r][c]
            if (v == 0) continue
            if (hid != null && hid.first == r && hid.second == c) continue
            piece(canvas, cx(c), cy(r), cs * 0.405f, v - 1, 255)
        }
        if (falling) piece(canvas, cx(fallCol), fallY, cs * 0.405f, fallPlayer, 255)
        if (hover >= 0 && !falling && !g.finished) {
            piece(canvas, cx(hover), top - cs * 0.55f, cs * 0.405f, g.current, 150)
        }

        // Brett mit Löchern
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRoundRect(RectF(left, top, right, bottom), corner, corner, Path.Direction.CW)
        for (r in 0 until g.rows) for (c in 0 until g.cols) path.addCircle(cx(c), cy(r), cs * 0.43f, Path.Direction.CCW)
        paint.style = Paint.Style.FILL
        if (style == 0) {
            paint.shader = LinearGradient(left, top, right, bottom, 0xFF3566D8.toInt(), 0xFF173B94.toInt(), Shader.TileMode.CLAMP)
        } else {
            val sc = max(right - left, bottom - top) / 1024f
            paint.shader = tex(BOARD_RES[style], sc, left, top)
        }
        canvas.drawPath(path, paint)
        // sanfte Abdunklung zum Rand
        paint.shader = RadialGradient(
            (left + right) / 2, (top + bottom) / 2, max(right - left, bottom - top) * 0.75f,
            0x00000000, if (style == 2 || style == 4) 0x44502A0A else 0x66000000, Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        // Rand des Bretts
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = cs * 0.05f
        paint.color = if (style == 0) 0xFF0C2466.toInt() else 0xFF1A0F08.toInt()
        rect.set(left + cs * 0.02f, top + cs * 0.02f, right - cs * 0.02f, bottom - cs * 0.02f)
        canvas.drawRoundRect(rect, corner, corner, paint)

        // Lochränder
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val x = cx(c)
            val y = cy(r)
            paint.strokeWidth = cs * 0.035f
            paint.color = if (style == 0) 0x55AFC6FF else 0x66F3D9A8
            canvas.drawCircle(x, y + cs * 0.035f, cs * 0.43f, paint)
            paint.strokeWidth = cs * 0.06f
            paint.color = if (style == 0) 0xCC0A1E5C.toInt() else 0xCC140B05.toInt()
            canvas.drawCircle(x, y, cs * 0.43f, paint)
            if (style == 3) {
                paint.strokeWidth = cs * 0.035f
                paint.color = 0xD7D2AE5E.toInt()
                canvas.drawCircle(x, y, cs * 0.46f, paint)
            }
        }

        // Letzter Zug und Gewinnreihe
        paint.style = Paint.Style.STROKE
        if (g.winLine.isNotEmpty()) {
            for ((r, c) in g.winLine) {
                paint.strokeWidth = cs * 0.1f
                paint.color = Color.argb((140 + 115 * pulse).toInt(), 255, 236, 140)
                canvas.drawCircle(cx(c), cy(r), cs * 0.43f, paint)
                paint.strokeWidth = cs * 0.04f
                paint.color = Color.argb(230, 255, 255, 255)
                canvas.drawCircle(cx(c), cy(r), cs * 0.34f, paint)
            }
        } else if (g.moves.isNotEmpty() && !falling) {
            val col = g.moves.last()
            var row = 0
            while (row < g.rows && g.grid[row][col] == 0) row++
            if (row < g.rows) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(200, 255, 236, 140)
                canvas.drawCircle(cx(col), cy(row), cs * 0.07f, paint)
            }
        }
    }

    /** Ein Stein (Scheibe) mit Farbverlauf oder Material und kleinem Glanz. */
    private fun piece(canvas: Canvas, x: Float, y: Float, r: Float, p: Int, alpha: Int) {
        paint.style = Paint.Style.FILL
        val base = PIECE_COLORS[style][p]
        if (style == 0) {
            val light = if (p == 0) 0xFFF4806A.toInt() else 0xFFFFF39A.toInt()
            paint.shader = RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.5f, light, base, Shader.TileMode.CLAMP)
            paint.alpha = alpha
            canvas.drawCircle(x, y, r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.12f
            paint.color = shade(base, 0.7f)
            paint.alpha = alpha
            canvas.drawCircle(x, y, r * 0.66f, paint)
        } else {
            paint.shader = tex(PIECE_RES[style][p], r * 2.6f / 512f, x - r * 1.3f, y - r * 1.3f)
            paint.alpha = alpha
            canvas.drawCircle(x, y, r, paint)
            paint.shader = RadialGradient(
                x - r * 0.35f, y - r * 0.4f, r * 1.5f,
                intArrayOf(0x55FFFFFF, 0x00000000, 0x66000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            paint.alpha = alpha
            canvas.drawCircle(x, y, r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.07f
            paint.color = shade(base, 0.5f)
            paint.alpha = min(190, alpha)
            canvas.drawCircle(x, y, r * 0.7f, paint)
            // kleine Goldumrandung hilft bei dunklen Steinen
            if (Color.red(base) + Color.green(base) + Color.blue(base) < 480) {
                paint.strokeWidth = r * 0.08f
                paint.color = 0xFFDDB75E.toInt()
                paint.alpha = alpha
                canvas.drawCircle(x, y, r * 0.96f, paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = 0x88FFFFFF.toInt()
        paint.alpha = min(136, alpha)
        canvas.drawCircle(x - r * 0.38f, y - r * 0.46f, r * 0.14f, paint)
        paint.alpha = 255
        paint.shader = null
    }

    // ------------------------------------------------------------------ Eingabe

    private fun colAt(x: Float): Int {
        val g = game ?: return -1
        return ((x - bl) / cs).toInt().coerceIn(0, g.cols - 1)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!inputEnabled || game == null || cs <= 0f) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val c = colAt(e.x)
                if (c != hover) {
                    hover = c
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val c = colAt(e.x)
                hover = -1
                invalidate()
                if (c >= 0) onColumn?.invoke(c)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                hover = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
