package com.mogas.timemanager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Anel de progresso desenhado à mão. */
class RingView(c: Context) : View(c) {
    var progress = 1f
        set(v) {
            field = v
            invalidate()
        }
    var color = Color.parseColor("#4FC3F7")
        set(v) {
            field = v
            invalidate()
        }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val sw = context.dp(12).toFloat()
        track.style = Paint.Style.STROKE
        track.strokeWidth = sw
        track.color = Color.parseColor("#262B36")
        arc.style = Paint.Style.STROKE
        arc.strokeWidth = sw
        arc.strokeCap = Paint.Cap.ROUND
        arc.color = color
        val m = sw / 2 + context.dp(2)
        rect.set(m, m, width - m, height - m)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (progress > 0.001f) canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, arc)
    }
}

class FocusActivity : Activity() {
    private val BG = Color.parseColor("#0F1115")
    private val CARD = Color.parseColor("#1A1D24")
    private val LINE = Color.parseColor("#262B36")
    private val ACC = Color.parseColor("#4FC3F7")
    private val ACC2 = Color.parseColor("#8E7CFF")
    private val GREEN = Color.parseColor("#4DD0A1")
    private val DIM = Color.parseColor("#8A92A0")
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var ring: RingView
    private lateinit var timeTv: TextView
    private lateinit var phaseTv: TextView
    private lateinit var input: EditText
    private lateinit var btnMain: TextView
    private lateinit var btnStop: TextView
    private lateinit var skipTv: TextView
    private lateinit var presetTv: TextView
    private lateinit var statsBox: LinearLayout
    private val dots = ArrayList<View>()
    private var lastStat = -1
    private val h = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            update()
            h.postDelayed(this, 500L)
        }
    }

    private fun shape(fill: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun fmtMin(m: Int) = if (m >= 60) "${m / 60} h" + (if (m % 60 > 0) " ${m % 60} min" else "") else "$m min"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#12151C")
        window.navigationBarColor = Color.parseColor("#0B0D11")
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#12151C"), Color.parseColor("#0B0D11"))
        )
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(20), dp(24), dp(20), dp(32))
        scroll.addView(box, FrameLayout.LayoutParams(MP, WC))

        val title = TextView(this)
        title.text = "Foco"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        box.addView(title, LinearLayout.LayoutParams(MP, WC))

        // Disciplina / tarefa
        input = EditText(this)
        input.hint = "Disciplina ou tarefa"
        input.setHintTextColor(DIM)
        input.setTextColor(Color.WHITE)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.maxLines = 1
        input.setSingleLine(true)
        input.setPadding(dp(16), dp(12), dp(16), dp(12))
        input.background = shape(CARD, 16, LINE)
        input.setText(intent.getStringExtra("label") ?: Pomo.lastLabel(this))
        box.addView(input, LinearLayout.LayoutParams(MP, WC).also { it.topMargin = dp(12) })

        val labels = Store.all(this).filter { it.kind == 1 || it.kind == 2 }.map { it.label }
            .filter { it.isNotEmpty() }.distinct().sorted().take(10)
        if (labels.isNotEmpty()) {
            val hs = HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (l in labels) {
                val chip = TextView(this)
                chip.text = l
                chip.textSize = 12f
                chip.setTextColor(DIM)
                chip.setPadding(dp(12), dp(7), dp(12), dp(7))
                chip.background = shape(Color.TRANSPARENT, 16, LINE)
                chip.pressable()
                chip.setOnClickListener { if (Pomo.phase(this) == 0) input.setText(l) }
                row.addView(chip, LinearLayout.LayoutParams(WC, WC).also { it.rightMargin = dp(8) })
            }
            hs.addView(row)
            box.addView(hs, LinearLayout.LayoutParams(MP, WC).also { it.topMargin = dp(10) })
        }

        // Anel + tempo
        val stage = FrameLayout(this)
        ring = RingView(this)
        stage.addView(ring, FrameLayout.LayoutParams(MP, MP))
        val centre = LinearLayout(this)
        centre.orientation = LinearLayout.VERTICAL
        centre.gravity = Gravity.CENTER
        timeTv = TextView(this)
        timeTv.textSize = 56f
        timeTv.setTextColor(Color.WHITE)
        timeTv.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        timeTv.gravity = Gravity.CENTER
        phaseTv = TextView(this)
        phaseTv.textSize = 12f
        phaseTv.letterSpacing = 0.14f
        phaseTv.setTextColor(DIM)
        phaseTv.gravity = Gravity.CENTER
        centre.addView(timeTv)
        centre.addView(phaseTv)
        stage.addView(centre, FrameLayout.LayoutParams(MP, MP))
        box.addView(stage, LinearLayout.LayoutParams(dp(270), dp(270)).also { it.topMargin = dp(22) })

        // Pontos dos ciclos
        val dotRow = LinearLayout(this)
        dotRow.orientation = LinearLayout.HORIZONTAL
        dotRow.gravity = Gravity.CENTER
        for (i in 0..3) {
            val d = View(this)
            dots.add(d)
            dotRow.addView(d, LinearLayout.LayoutParams(dp(10), dp(10)).also { it.setMargins(dp(5), 0, dp(5), 0) })
        }
        box.addView(dotRow, LinearLayout.LayoutParams(WC, WC).also { it.topMargin = dp(16) })

        // Botões
        btnMain = TextView(this)
        btnMain.textSize = 20f
        btnMain.gravity = Gravity.CENTER
        btnMain.setTextColor(BG)
        btnMain.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        btnMain.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACC, ACC2)).also {
            it.cornerRadius = dp(32).toFloat()
        }
        btnMain.pressable()
        btnMain.setOnClickListener {
            val ph = Pomo.phase(this)
            if (ph == 0) Pomo.start(this, input.text.toString().trim())
            else if (Pomo.paused(this)) Pomo.resume(this)
            else Pomo.pause(this)
            update()
        }
        box.addView(btnMain, LinearLayout.LayoutParams(MP, dp(64)).also { it.topMargin = dp(20) })

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        btnStop = TextView(this)
        btnStop.text = "Parar"
        btnStop.textSize = 15f
        btnStop.gravity = Gravity.CENTER
        btnStop.setTextColor(Color.WHITE)
        btnStop.background = shape(Color.parseColor("#2A2F3A"), 26)
        btnStop.pressable()
        btnStop.setOnClickListener {
            Pomo.stop(this)
            update()
        }
        skipTv = TextView(this)
        skipTv.text = "Saltar fase"
        skipTv.textSize = 15f
        skipTv.gravity = Gravity.CENTER
        skipTv.setTextColor(DIM)
        skipTv.background = shape(Color.TRANSPARENT, 26, LINE)
        skipTv.pressable()
        skipTv.setOnClickListener {
            Pomo.advance(this, false)
            update()
        }
        row2.addView(btnStop, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.rightMargin = dp(6) })
        row2.addView(skipTv, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.leftMargin = dp(6) })
        box.addView(row2, LinearLayout.LayoutParams(MP, WC).also { it.topMargin = dp(12) })

        presetTv = TextView(this)
        presetTv.textSize = 13f
        presetTv.gravity = Gravity.CENTER
        presetTv.setTextColor(ACC)
        presetTv.setPadding(dp(14), dp(10), dp(14), dp(10))
        presetTv.pressable()
        presetTv.setOnClickListener {
            if (Pomo.phase(this) == 0) {
                Pomo.nextPreset(this)
                update()
            }
        }
        box.addView(presetTv, LinearLayout.LayoutParams(WC, WC).also { it.topMargin = dp(8) })

        // Estatísticas
        statsBox = LinearLayout(this)
        statsBox.orientation = LinearLayout.VERTICAL
        statsBox.setPadding(dp(18), dp(16), dp(18), dp(16))
        statsBox.background = shape(CARD, 22, LINE)
        box.addView(statsBox, LinearLayout.LayoutParams(MP, WC).also { it.topMargin = dp(18) })

        setContentView(scroll)
        ring.enter(0)
    }

    override fun onResume() {
        super.onResume()
        Pomo.restore(this)
        lastStat = -1
        update()
        h.removeCallbacks(loop)
        h.postDelayed(loop, 500L)
    }

    override fun onPause() {
        h.removeCallbacks(loop)
        super.onPause()
    }

    private fun update() {
        val ph = Pomo.phase(this)
        val running = ph != 0
        val paused = running && Pomo.paused(this)
        if (running && !paused && Pomo.endAt(this) < System.currentTimeMillis() - 5000L) Pomo.restore(this)
        val ph2 = Pomo.phase(this)
        val run2 = ph2 != 0
        val total = Pomo.dur(this, if (run2) ph2 else Pomo.FOCUS) * 60000L
        val rem = when {
            !run2 -> total
            Pomo.paused(this) -> Pomo.remaining(this)
            else -> maxOf(0L, Pomo.endAt(this) - System.currentTimeMillis())
        }
        ring.progress = if (run2) rem.toFloat() / total else 1f
        ring.color = if (!run2 || ph2 == Pomo.FOCUS) ACC else GREEN
        val sec = (rem + 999) / 1000
        timeTv.text = "%02d:%02d".format(sec / 60, sec % 60)
        phaseTv.text = when {
            !run2 -> "PRONTO"
            ph2 == Pomo.FOCUS -> "FOCO"
            ph2 == Pomo.SHORT -> "PAUSA CURTA"
            else -> "PAUSA LONGA"
        } + (if (run2 && Pomo.paused(this)) "  ·  EM PAUSA" else "")
        btnMain.text = if (!run2) "Iniciar foco" else if (Pomo.paused(this)) "Retomar" else "Pausar"
        btnStop.visibility = if (run2) View.VISIBLE else View.GONE
        skipTv.visibility = if (run2) View.VISIBLE else View.GONE
        input.isEnabled = !run2
        val pr = Pomo.presets[Pomo.preset(this)]
        presetTv.text = "Modelo  ${pr[0]} · ${pr[1]} · ${pr[2]} min   ›"
        presetTv.alpha = if (run2) 0.4f else 1f

        val cy = Pomo.cycles(this)
        val filled = if (cy == 0) 0 else ((cy - 1) % 4) + 1
        for (i in 0..3) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(if (i < filled) ACC else LINE)
            dots[i].background = g
        }

        val sig = Pomo.weekTotal(this)
        if (sig != lastStat) {
            lastStat = sig
            buildStats()
        }
    }

    private fun buildStats() {
        statsBox.removeAllViews()
        val head = TextView(this)
        head.text = "ESTA SEMANA"
        head.textSize = 12f
        head.letterSpacing = 0.12f
        head.setTextColor(DIM)
        statsBox.addView(head)
        val (n, m) = Pomo.today(this)
        val today = TextView(this)
        today.text = if (n == 0) "Hoje ainda sem ciclos" else "Hoje: $n ${if (n == 1) "ciclo" else "ciclos"} · ${fmtMin(m)}"
        today.textSize = 15f
        today.setTextColor(Color.WHITE)
        today.setPadding(0, dp(6), 0, dp(6))
        statsBox.addView(today)
        val st = Pomo.weekStats(this)
        if (st.isEmpty()) {
            val e = TextView(this)
            e.text = "Os ciclos concluídos aparecem aqui, por disciplina."
            e.textSize = 13f
            e.setTextColor(DIM)
            statsBox.addView(e)
            return
        }
        val max = st.maxOf { it.second }.coerceAtLeast(1)
        for ((l, mins) in st) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.VERTICAL
            r.setPadding(0, dp(6), 0, dp(2))
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            val name = TextView(this)
            name.text = l
            name.textSize = 14f
            name.setTextColor(Color.WHITE)
            val v = TextView(this)
            v.text = fmtMin(mins)
            v.textSize = 14f
            v.setTextColor(DIM)
            line.addView(name, LinearLayout.LayoutParams(0, WC, 1f))
            line.addView(v)
            r.addView(line)
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.background = shape(LINE, 2)
            val f = mins.toFloat() / max
            val fill = View(this)
            fill.background = shape(ACC, 2)
            bar.addView(fill, LinearLayout.LayoutParams(0, MP, f))
            bar.addView(View(this), LinearLayout.LayoutParams(0, MP, 1f - f))
            r.addView(bar, LinearLayout.LayoutParams(MP, dp(4)).also { it.topMargin = dp(5) })
            statsBox.addView(r)
        }
    }
}
