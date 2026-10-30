package de.autoknigge.line

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject

/** Line – vier oder fünf in einer Reihe für zwei Personen an einem Gerät oder gegen den Computer. */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val URL_HOME = "https://autoknigge.de"
        private const val URL_APPS = "https://autoknigge.de/apps/"
        private const val URL_IMPRINT = "https://autoknigge.de/impressum"
        private const val URL_PRIVACY = "https://autoknigge.de/line/datenschutz.html"
        private val GOLD = Color.parseColor("#D4AF37")
        private val TEXT = Color.parseColor("#F8E7A4")
        private val DIM = Color.parseColor("#CFC38E")
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var adManager: AdManager

    // Einrichtung
    private val kinds = arrayOf(Kind.HUMAN, Kind.COMPUTER)
    private val names = arrayOf("", "")
    private var need = 4
    private var level = 1
    private lateinit var setupView: ScrollView
    private lateinit var kindButtons: List<TextView>
    private lateinit var nameFields: List<EditText>
    private lateinit var modeButton: TextView
    private lateinit var levelButton: TextView
    private lateinit var designButton: TextView
    private val seatDots = ArrayList<View>()
    private lateinit var continueButton: TextView

    // Spiel
    private lateinit var gameView: LinearLayout
    private lateinit var board: BoardView
    private lateinit var status: TextView
    private lateinit var turnText: TextView
    private lateinit var pulseDot: View
    private lateinit var chipBoxes: List<LinearLayout>
    private lateinit var chipNames: List<TextView>
    private lateinit var chipSubs: List<TextView>
    private lateinit var chipDots: List<View>
    private lateinit var undoButton: TextView
    private var game: Game? = null
    private val wins = intArrayOf(0, 0)
    private var busy = false
    private var session = 0
    private var clickSound: android.media.SoundPool? = null
    private var clickId = 0
    private lateinit var music: MusicPlayer
    private val musicListener: () -> Unit = {
        runOnUiThread {
            musicRefresh?.invoke()
            if (::musicTop.isInitialized) musicTop.alpha = if (music.playing) 1f else 0.65f
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private var footerView: View? = null

    private val design get() = prefs.getInt("design", 0).coerceIn(0, BoardView.DESIGNS - 1)

    private fun playerName(p: Int): String {
        val n = names[p]
        return if (n.isBlank()) BoardView.PIECE_NAMES[design][p] else n
    }

    // ------------------------------------------------------------------ Aufbau

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = getSharedPreferences("line", MODE_PRIVATE)
        music = MusicHub.get(this)
        music.addListener(musicListener)
        loadSetup()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_screen)
        }
        Ui.insets(root)
        val content = FrameLayout(this)
        buildSetup()
        buildGame()
        content.addView(setupView, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(gameView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val adSettings = link("Werbe-Einstellungen", null)
        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }
        footer.addView(link("Datenschutz", URL_PRIVACY))
        footer.addView(dot())
        footer.addView(link("Impressum", URL_IMPRINT))
        footer.addView(dot())
        footer.addView(adSettings)
        footer.addView(dot())
        footer.addView(link("Weitere Apps", URL_APPS))
        root.addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))
        footerView = footer

        val adBox = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_ad)
            minimumHeight = dp(60)
        }
        root.addView(adBox, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        adManager = AdManager(this, adBox, adSettings, true)

        clickSound = android.media.SoundPool.Builder().setMaxStreams(3).build().also {
            clickId = it.load(this, R.raw.click, 1)
        }
        showSetup()
        showIntro { adManager.start() }
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun link(label: String, url: String?): TextView = text(label, 12f, GOLD).apply {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(6), dp(8), dp(6), dp(8))
        maxLines = 1
        if (url != null) setOnClickListener { Ui.openUrl(this@MainActivity, url) }
    }

    private fun dot() = text("·", 12f, GOLD)

    private fun goldButton(label: String, onClick: () -> Unit): TextView = text(label, 20f, Color.parseColor("#1B3A2B"), true).apply {
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_btn_gold)
        setPadding(dp(16), dp(14), dp(16), dp(20))
        minHeight = dp(64)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun darkButton(label: String, onClick: () -> Unit): TextView = text(label, 15f, TEXT, true).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#1B2E22"))
            setStroke(dp(2), Color.parseColor("#C9A648"))
            cornerRadius = dp(14).toFloat()
        }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        minHeight = dp(44)
        isClickable = true
        setOnClickListener { onClick() }
    }


    private fun showIntro(then: () -> Unit) {
        if (prefs.getBoolean("intro_seen", false)) {
            then()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Kostenlos dank Werbung")
            .setMessage(
                "Diese App ist kostenlos und finanziert sich durch Werbung: eine kurze Anzeige beim Start und ein Banner am unteren Rand.\n\n" +
                    "Im nächsten Schritt kannst du der Nutzung deiner Daten für Werbezwecke zustimmen und damit die kostenlose App unterstützen. " +
                    "Deine Auswahl kannst du jederzeit unter „Werbe-Einstellungen“ ändern."
            )
            .setCancelable(false)
            .setPositiveButton("Weiter") { _, _ ->
                prefs.edit().putBoolean("intro_seen", true).apply()
                then()
            }
            .show()
    }

    private fun showMusic() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }
        val now = text("", 16f, TEXT, true).apply { maxLines = 2 }
        box.addView(now)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val play = darkButton("▶") { music.toggle() }
        controls.addView(darkButton("⏮") { music.previous() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        controls.addView(play, LinearLayout.LayoutParams(0, WRAP, 1.4f).apply { rightMargin = dp(6) })
        controls.addView(darkButton("⏭") { music.next() }, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(controls, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val shuffle = darkButton("") { music.shuffle = !music.shuffle }
        val repeat = darkButton("") { music.repeat = !music.repeat }
        modes.addView(shuffle, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        modes.addView(repeat, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(modes, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        box.addView(text("Lautstärke der Musik", 13f, DIM), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        val vol = android.widget.SeekBar(this).apply {
            max = 100
            progress = (music.volume * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(GOLD)
            thumbTintList = android.content.res.ColorStateList.valueOf(GOLD)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar, p: Int, user: Boolean) {
                    if (user) music.volume = p / 100f
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar) {}
            })
        }
        box.addView(vol)
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(listBox) }
        box.addView(scroll, LinearLayout.LayoutParams(MATCH, dp(180)).apply { topMargin = dp(8) })
        box.addView(darkButton("＋  Dateien hinzufügen (Musik oder Video)") {
            pickMusic.launch(arrayOf("audio/*", "video/*"))
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        box.addView(text("Es läuft nur der Ton. Die Musik läuft auch weiter, wenn du eine andere App öffnest. In der Benachrichtigung steuerst du sie.", 12f, DIM), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })

        val refresh = {
            val t = music.current()
            now.text = if (t == null) "Noch keine Titel gewählt" else (if (music.playing) "▶  " else "⏸  ") + t.name
            play.text = if (music.playing) "⏸" else "▶"
            shuffle.text = if (music.shuffle) "🔀 Zufall: an" else "🔀 Zufall: aus"
            repeat.text = if (music.repeat) "🔁 Wiederholen: an" else "🔁 Wiederholen: aus"
            listBox.removeAllViews()
            for ((i, tr) in music.tracks.withIndex()) {
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, dp(2))
                }
                row.addView(text(tr.name, 15f, if (i == music.index) GOLD else TEXT, i == music.index).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, dp(8))
                    setOnClickListener { music.play(i) }
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(text("✕", 17f, DIM).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Titel entfernen"
                    setOnClickListener { music.remove(i) }
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
                listBox.addView(row)
            }
        }
        refresh()
        musicRefresh = refresh
        AlertDialog.Builder(this)
            .setTitle("🎵  Musik")
            .setView(box)
            .setPositiveButton("Fertig", null)
            .setNeutralButton("Liste leeren") { _, _ -> music.clear() }
            .setOnDismissListener { musicRefresh = null }
            .show()
    }


    private fun frameBox(label: String, onClick: () -> Unit): TextView = text(label, 24f, Color.parseColor("#F3DF9A")).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#123425"))
            setStroke(dp(1), Color.parseColor("#496D53"))
            cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { onClick() }
    }

    /** Ab Android 13 braucht die Benachrichtigung mit den Musik-Knöpfen eine Erlaubnis (einmal fragen). */
    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notifications", false)
        ) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val askNotifications = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    private var musicRefresh: (() -> Unit)? = null
    private lateinit var musicTop: TextView
    private val pickMusic = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            askNotificationPermission()
            music.add(uris)
        }
    }



    private fun buildSetup() {
        setupView = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        val logoBox = FrameLayout(this)
        logoBox.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.logo_line)
            adjustViewBounds = true
            contentDescription = "Line"
        }, FrameLayout.LayoutParams(dp(250), WRAP))
        val ver = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        logoBox.addView(
            text(ver, 10f, Color.parseColor("#FFE07A")),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(2)
                rightMargin = dp(2)
            }
        )
        col.addView(logoBox, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(text("Vier oder fünf in einer Reihe", 15f, DIM).apply { gravity = Gravity.CENTER })

        col.addView(text("Wer spielt mit?", 17f, TEXT, true), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(22) })
        val kb = ArrayList<TextView>()
        val nf = ArrayList<EditText>()
        for (i in 0 until 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(View(this).apply {
                seatDots.add(this)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BoardView.PIECE_COLORS[0][i])
                    setStroke(dp(2), Color.WHITE)
                }
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
            val et = EditText(this).apply {
                hint = BoardView.PIECE_NAMES[0][i]
                setText(names[i])
                setTextColor(TEXT)
                setHintTextColor(Color.parseColor("#8E8A6A"))
                textSize = 17f
                maxLines = 1
                filters = arrayOf(InputFilter.LengthFilter(12))
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                backgroundTintList = android.content.res.ColorStateList.valueOf(GOLD)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }
            nf.add(et)
            row.addView(et, LinearLayout.LayoutParams(0, WRAP, 1f))
            val b = darkButton("", {}).apply { minWidth = dp(112) }
            b.setOnClickListener { cycleKind(i) }
            kb.add(b)
            row.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(10) })
            col.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        }
        kindButtons = kb
        nameFields = nf

        modeButton = darkButton("") { need = if (need == 4) 5 else 4; updateSetup() }
        col.addView(modeButton, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })
        levelButton = darkButton("") { level = (level + 1) % 3; updateSetup() }
        col.addView(levelButton, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        designButton = darkButton("") { cycleDesign() }
        col.addView(designButton, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

        col.addView(goldButton("Neues Spiel starten") { startNewGame() }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(22) })
        continueButton = goldButton("Spiel fortsetzen") { resumeGame() }
        col.addView(continueButton, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        col.addView(darkButton("🎵  Musik") { showMusic() }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) })
        col.addView(darkButton("Spielregeln") { showRules() }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

        col.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        col.addView(link("Eine Idee von autoknigge.de", URL_HOME), LinearLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
        })
        setupView.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
    }

    private fun cycleDesign() {
        val v = (design + 1) % BoardView.DESIGNS
        prefs.edit().putInt("design", v).apply()
        board.style = v
        clickSound?.play(clickId, 0.7f, 0.7f, 1, 0, 1f)
        updateSetup()
        if (game != null) updateChips()
    }

    private fun cycleKind(i: Int) {
        syncNames()
        kinds[i] = if (kinds[i] == Kind.HUMAN) Kind.COMPUTER else Kind.HUMAN
        updateSetup()
    }

    private fun updateSetup() {
        for (i in 0 until 2) {
            kindButtons[i].text = if (kinds[i] == Kind.HUMAN) "👤 Mensch" else "🤖 Computer"
            nameFields[i].hint = BoardView.PIECE_NAMES[design][i]
            (seatDots[i].background as? GradientDrawable)?.setColor(BoardView.PIECE_COLORS[design][i])
        }
        val size = Game.sizeFor(need)
        modeButton.text = "Spielart: $need in einer Reihe (${size.first} × ${size.second})"
        val anyComputer = kinds.any { it == Kind.COMPUTER }
        levelButton.visibility = if (anyComputer) View.VISIBLE else View.GONE
        levelButton.text = "Computer-Stufe: " + when (level) { 0 -> "Leicht"; 1 -> "Normal"; else -> "Stark" }
        designButton.text = "Brett-Design: " + BoardView.DESIGN_NAMES[design]
        continueButton.visibility = if (prefs.contains("game")) View.VISIBLE else View.GONE
    }

    private fun syncNames() {
        for (i in 0 until 2) names[i] = nameFields[i].text.toString().trim()
    }

    private fun showSetup() {
        updateSetup()
        gameView.visibility = View.GONE
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupView.visibility = View.VISIBLE
        footerView?.visibility = View.VISIBLE
    }

    private fun showRules() {
        AlertDialog.Builder(this)
            .setTitle("Spielregeln")
            .setMessage(
                "Ziel: Bringe als Erste oder Erster vier Steine (bei der großen Spielart fünf) in eine Reihe: waagerecht, senkrecht oder schräg.\n\n" +
                    "• Ihr werft abwechselnd einen Stein ein. Er fällt in der gewählten Spalte nach unten.\n" +
                    "• Tippe auf das Brett und halte den Finger auf einer Spalte, um zu sehen, wo der Stein landet. Beim Loslassen fällt er.\n" +
                    "• „Zug zurück“ nimmt deinen letzten Zug zurück (gegen den Computer auch dessen Antwort).\n" +
                    "• Ist das Brett voll und niemand hat eine Reihe, endet die Runde unentschieden.\n\n" +
                    "Tipp: Mit „Design“ wechselst du zwischen den fünf Brettern, auch mitten im Spiel."
            )
            .setPositiveButton("Verstanden", null)
            .show()
    }

    // ------------------------------------------------------------------ Spielbildschirm

    private fun buildGame() {
        gameView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(6))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(frameBox("‹") { leaveGame() }.apply { contentDescription = "Zurück zum Menü" }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        heading.addView(text("LINE", 10f, Color.parseColor("#D7B45A"), true).apply {
            letterSpacing = 0.19f
            gravity = Gravity.CENTER
        })
        status = text("", 22f, Color.parseColor("#F7EFD9"), true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(13, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        heading.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))
        top.addView(heading, LinearLayout.LayoutParams(0, WRAP, 1f))
        musicTop = frameBox("♫") { showMusic() }.apply {
            alpha = 0.65f
            contentDescription = "Musik"
        }
        top.addView(musicTop, LinearLayout.LayoutParams(dp(42), dp(42)))
        gameView.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val turn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        pulseDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#D94B45"))
            }
        }
        turn.addView(pulseDot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })
        turnText = text("", 13f, Color.parseColor("#E6E4CF")).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        turn.addView(turnText, LinearLayout.LayoutParams(0, WRAP, 1f))
        gameView.addView(turn, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8); bottomMargin = dp(4) })
        android.animation.ObjectAnimator.ofFloat(pulseDot, "alpha", 1f, 0.25f).apply {
            duration = 800
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }

        board = BoardView(this)
        board.style = design
        board.onColumn = { c -> onColumnTapped(c) }
        gameView.addView(board, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Spielerkarten mit Punktestand
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val boxes = ArrayList<LinearLayout>()
        val nm = ArrayList<TextView>()
        val sb = ArrayList<TextView>()
        val dt = ArrayList<View>()
        for (i in 0 until 2) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val d = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            }
            line.addView(d, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(5) })
            val n = text("", 14f, Color.parseColor("#F7EFD9"), true).apply { maxLines = 1 }
            line.addView(n)
            box.addView(line)
            val s = text("", 12f, Color.parseColor("#C7D2C9")).apply { gravity = Gravity.CENTER }
            box.addView(s)
            boxes.add(box); nm.add(n); sb.add(s); dt.add(d)
            chipRow.addView(box, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        chipBoxes = boxes; chipNames = nm; chipSubs = sb; chipDots = dt
        gameView.addView(chipRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })

        // Knöpfe: Zug zurück, Design, neue Runde
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#173D2B"), Color.parseColor("#0C2419"))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#B18C3D"))
            }
        }
        undoButton = darkButton("↶  Zug zurück") { undoMove() }
        bar.addView(undoButton, LinearLayout.LayoutParams(0, WRAP, 1.3f).apply { rightMargin = dp(6) })
        bar.addView(darkButton("🎨  Design") { cycleDesign() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        bar.addView(darkButton("Neu") { newRound(false) }, LinearLayout.LayoutParams(0, WRAP, 0.7f))
        gameView.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        gameView.visibility = View.GONE
    }

    private fun leaveGame() {
        val g = game
        if (g != null && !g.finished && g.moves.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Zum Menü?")
                .setMessage("Das Spiel wird gespeichert, du kannst es später fortsetzen.")
                .setPositiveButton("Zum Menü") { _, _ -> saveGame(); leaveToMenu() }
                .setNegativeButton("Weiterspielen", null)
                .show()
        } else {
            leaveToMenu()
        }
    }

    private fun leaveToMenu() {
        session++
        busy = false
        board.inputEnabled = false
        showSetup()
    }

    private fun enterGame() {
        setupView.visibility = View.GONE
        gameView.visibility = View.VISIBLE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        board.style = design
        updateChips()
    }

    private fun updateChips() {
        val g = game
        for (i in 0 until 2) {
            val col = BoardView.PIECE_COLORS[design][i]
            chipNames[i].text = playerName(i) + if (kinds[i] == Kind.COMPUTER) " 🤖" else ""
            chipSubs[i].text = "Siege: ${wins[i]}"
            (chipDots[i].background as? GradientDrawable)?.setColor(col)
            val active = g != null && !g.finished && g.current == i
            chipBoxes[i].background = GradientDrawable().apply {
                setColor(Color.parseColor("#17241C"))
                setStroke(dp(if (active) 3 else 1), if (active) col else Color.parseColor("#3A4C40"))
                cornerRadius = dp(14).toFloat()
            }
        }
        if (g != null) (pulseDot.background as? GradientDrawable)?.setColor(BoardView.PIECE_COLORS[design][g.current])
    }

    // ------------------------------------------------------------------ Spielablauf

    private fun startNewGame() {
        syncNames()
        saveSetup()
        val size = Game.sizeFor(need)
        game = Game(size.first, size.second, need)
        wins[0] = 0
        wins[1] = 0
        prefs.edit().remove("game").apply()
        board.game = game
        enterGame()
        beginTurn()
    }

    private fun resumeGame() {
        if (!loadGame()) return
        board.game = game
        enterGame()
        beginTurn()
    }

    private fun newRound(afterEnd: Boolean) {
        val g = game ?: return
        if (!afterEnd && g.moves.isNotEmpty() && !g.finished) {
            AlertDialog.Builder(this)
                .setTitle("Neue Runde?")
                .setMessage("Die laufende Runde wird abgebrochen.")
                .setPositiveButton("Neue Runde") { _, _ -> newRound(true) }
                .setNegativeButton("Weiterspielen", null)
                .show()
            return
        }
        session++
        busy = false
        g.reset(1 - g.first)
        board.game = g
        prefs.edit().remove("game").apply()
        beginTurn()
    }

    private fun beginTurn() {
        val g = game ?: return
        updateChips()
        undoButton.alpha = if (g.moves.isEmpty()) 0.45f else 1f
        if (g.finished) {
            finishRound(g)
            return
        }
        val who = g.current
        status.text = "${playerName(who)} ist am Zug"
        if (kinds[who] == Kind.COMPUTER) {
            turnText.text = "${playerName(who)} denkt nach …"
            board.inputEnabled = false
            busy = true
            val my = session
            Thread {
                val started = System.currentTimeMillis()
                val col = Ai.choose(g, level)
                val wait = 450 - (System.currentTimeMillis() - started)
                main.postDelayed({
                    if (my == session && game === g && !g.finished) {
                        busy = false
                        play(col)
                    }
                }, wait.coerceAtLeast(0))
            }.start()
        } else {
            turnText.text = "Tippe auf eine Spalte, um einen Stein einzuwerfen"
            board.inputEnabled = true
            busy = false
        }
    }

    private fun onColumnTapped(col: Int) {
        val g = game ?: return
        if (busy || g.finished || kinds[g.current] == Kind.COMPUTER) return
        if (!g.canDrop(col)) {
            turnText.text = "Diese Spalte ist voll"
            return
        }
        play(col)
    }

    private fun play(col: Int) {
        val g = game ?: return
        val who = g.current
        val row = g.drop(col)
        if (row < 0) {
            beginTurn()
            return
        }
        busy = true
        board.inputEnabled = false
        val my = session
        board.animateDrop(col, row, who) {
            if (my == session) {
                clickSound?.play(clickId, 0.6f, 0.6f, 1, 0, 1.2f)
                busy = false
                board.invalidate()
                saveGame()
                beginTurn()
            }
        }
    }

    private fun finishRound(g: Game) {
        board.inputEnabled = false
        prefs.edit().remove("game").apply()
        val title: String
        val msg: String
        if (g.winner >= 0) {
            wins[g.winner]++
            updateChips()
            title = "🏆  ${playerName(g.winner)} gewinnt!"
            msg = "${g.need} in einer Reihe. Stand: ${playerName(0)} ${wins[0]} : ${wins[1]} ${playerName(1)}"
            status.text = "${playerName(g.winner)} hat gewonnen!"
            turnText.text = "Glückwunsch!"
        } else {
            title = "Unentschieden"
            msg = "Das Brett ist voll. Stand: ${playerName(0)} ${wins[0]} : ${wins[1]} ${playerName(1)}"
            status.text = "Unentschieden"
            turnText.text = "Keine Reihe möglich"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("Noch eine Runde") { _, _ -> newRound(true) }
            .setNegativeButton("Brett ansehen", null)
            .setNeutralButton("Zum Menü") { _, _ -> leaveToMenu() }
            .show()
    }

    private fun undoMove() {
        val g = game ?: return
        if (busy || g.finished || g.moves.isEmpty()) return
        // Gegen den Computer: auch dessen Antwort zurücknehmen, damit wieder der Mensch dran ist.
        g.undo()
        if (kinds[g.current] == Kind.COMPUTER && kinds.any { it == Kind.HUMAN } && g.moves.isNotEmpty()) g.undo()
        session++
        board.game = g
        saveGame()
        beginTurn()
    }

    // ------------------------------------------------------------------ Speichern

    private fun saveSetup() {
        val a = JSONArray()
        for (i in 0 until 2) a.put(JSONObject().put("k", kinds[i].name).put("n", names[i]))
        prefs.edit().putString("setup", a.toString()).putInt("need", need).putInt("level", level).apply()
    }

    private fun loadSetup() {
        need = if (prefs.getInt("need", 4) == 5) 5 else 4
        level = prefs.getInt("level", 1).coerceIn(0, 2)
        try {
            val a = JSONArray(prefs.getString("setup", null) ?: return)
            for (i in 0 until 2) {
                val o = a.getJSONObject(i)
                kinds[i] = Kind.valueOf(o.getString("k"))
                names[i] = o.optString("n", "")
            }
        } catch (e: Exception) {
            // Standardwerte
        }
    }

    private fun saveGame() {
        val g = game ?: return
        if (g.finished || g.moves.isEmpty()) {
            if (g.finished) prefs.edit().remove("game").apply()
            return
        }
        val o = JSONObject()
        o.put("need", g.need)
        o.put("first", g.first)
        o.put("moves", JSONArray().apply { g.moves.forEach { put(it) } })
        o.put("kinds", JSONArray().apply { kinds.forEach { put(it.name) } })
        o.put("names", JSONArray().apply { names.forEach { put(it) } })
        o.put("wins", JSONArray().apply { wins.forEach { put(it) } })
        prefs.edit().putString("game", o.toString()).putInt("level", level).apply()
    }

    private fun loadGame(): Boolean = try {
        val o = JSONObject(prefs.getString("game", null) ?: return false)
        need = o.getInt("need")
        val size = Game.sizeFor(need)
        val g = Game(size.first, size.second, need)
        g.reset(o.getInt("first"))
        val ka = o.getJSONArray("kinds")
        val na = o.getJSONArray("names")
        val wa = o.getJSONArray("wins")
        for (i in 0 until 2) {
            kinds[i] = Kind.valueOf(ka.getString(i))
            names[i] = na.optString(i, "")
            wins[i] = wa.optInt(i, 0)
        }
        val ma = o.getJSONArray("moves")
        for (i in 0 until ma.length()) g.drop(ma.getInt(i))
        game = g
        true
    } catch (e: Exception) {
        false
    }

    override fun onPause() {
        if (gameView.visibility == View.VISIBLE) saveGame()
        if (::adManager.isInitialized) adManager.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::adManager.isInitialized) adManager.resume()
    }

    override fun onDestroy() {
        if (::music.isInitialized) music.removeListener(musicListener)
        clickSound?.release()
        clickSound = null
        if (::adManager.isInitialized) adManager.destroy()
        super.onDestroy()
    }
}
