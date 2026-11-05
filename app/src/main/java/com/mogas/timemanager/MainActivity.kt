package com.mogas.timemanager

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {
    private val BG = Color.parseColor("#0F1115")
    private val CARD = Color.parseColor("#1A1D24")
    private val LINE = Color.parseColor("#262B36")
    private val ACC = Color.parseColor("#4FC3F7")
    private val ACC2 = Color.parseColor("#8E7CFF")
    private val GREEN = Color.parseColor("#4DD0A1")
    private val TXT = Color.WHITE
    private val DIM = Color.parseColor("#8A92A0")
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private val dayNames = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    private val dayFull = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

    private lateinit var list: LinearLayout
    private lateinit var tabsRow: LinearLayout
    private lateinit var segDia: TextView
    private lateinit var segSem: TextView
    private lateinit var dateTv: TextView
    private lateinit var heroLabel: TextView
    private lateinit var heroBig: TextView
    private lateinit var heroSmall: TextView
    private lateinit var dot: View
    private lateinit var fab: TextView
    private lateinit var verify: TextView
    private lateinit var focusPill: TextView
    private val tabViews = ArrayList<LinearLayout>()
    private var pulse: ObjectAnimator? = null
    private var dlg: AlertDialog? = null
    private var askedNotif = false
    private var skipped = false
    private var firstShow = true
    private var selDay = 0
    private var mode = 0 // 0 = Dia, 1 = Semana

    private lateinit var gd: GestureDetector
    private val tick = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (mode == 0 && selDay == todayIdx()) render(false) else updateHero()
            tick.postDelayed(this, 30000L)
        }
    }

    // ---------- helpers ----------
    private fun shape(fill: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun grad(r: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACC, ACC2))
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    private fun todayIdx() = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
    private fun hhmm(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)
    private fun fmtDur(m: Int) = if (m >= 60) "${m / 60} h" + (if (m % 60 > 0) " ${m % 60} min" else "") else "$m min"
    private fun kindColor(k: Int) = when (k) {
        1 -> ACC2
        2 -> ACC
        3 -> GREEN
        else -> DIM
    }

    private fun kindName(k: Int) = when (k) {
        1 -> "Estudo"
        2 -> "Aula"
        3 -> "Rotina"
        else -> "Outro"
    }

    private fun effDays(a: Alarm): Int {
        if (a.days != 0) return a.days
        val c = Calendar.getInstance()
        c.timeInMillis = Scheduler.next(a) + a.lead * 60000L
        return 1 shl ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    }

    private fun blocksFor(day: Int, all: List<Alarm>): List<Alarm> =
        all.filter { (effDays(it) and (1 shl day)) != 0 }.sortedWith(compareBy({ it.hour }, { it.minute }))

    private fun abbr(l: String): String = l.split("+").joinToString("\n+") { part ->
        part.trim().split(" ").filter { it.isNotEmpty() && (it[0].isUpperCase() || it.length > 3) }
            .map { it[0].uppercaseChar() }.joinToString("")
    }

    // ---------- UI ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Schedule.seedOnce(this)
        selDay = todayIdx()
        gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || mode != 0) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(vx) > 900 && abs(dx) > dp(80) && abs(dy) < abs(dx) / 1.5f) {
                    selDay = (selDay + (if (dx < 0) 1 else 6)) % 7
                    paintTabs()
                    render(true)
                }
                return false
            }
        })
        window.statusBarColor = Color.parseColor("#12151C")
        window.navigationBarColor = Color.parseColor("#0B0D11")

        val frame = FrameLayout(this)
        frame.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#12151C"), Color.parseColor("#0B0D11"))
        )
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        frame.addView(col, FrameLayout.LayoutParams(MP, MP))

        // Cabeçalho
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(22), dp(22), dp(18), dp(8))
        val titleCol = LinearLayout(this)
        titleCol.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "Horário"
        title.textSize = 30f
        title.setTextColor(TXT)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        dateTv = TextView(this)
        dateTv.textSize = 13f
        dateTv.setTextColor(DIM)
        titleCol.addView(title)
        title.setOnClickListener {
            selDay = todayIdx()
            mode = 0
            applyMode()
        }
        titleCol.addView(dateTv)
        head.addView(titleCol, LinearLayout.LayoutParams(0, WC, 1f))

        focusPill = pill("Foco", ACC, Color.argb(110, 79, 195, 247))
        focusPill.setOnClickListener { startActivity(Intent(this, FocusActivity::class.java)) }
        head.addView(focusPill, LinearLayout.LayoutParams(WC, WC).also { it.rightMargin = dp(8) })
        verify = pill("Verificar", DIM, LINE)
        verify.setOnClickListener { startActivity(Intent(this, HealthActivity::class.java)) }
        head.addView(verify)
        col.addView(head)

        // Cartão "Agora / A seguir"
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(dp(20), dp(14), dp(20), dp(16))
        hero.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2)).also {
            it.cornerRadius = dp(24).toFloat()
        }
        val heroTop = LinearLayout(this)
        heroTop.orientation = LinearLayout.HORIZONTAL
        heroTop.gravity = Gravity.CENTER_VERTICAL
        dot = View(this)
        dot.background = GradientDrawable().also { it.shape = GradientDrawable.OVAL; it.setColor(BG) }
        heroTop.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)).also { it.rightMargin = dp(8) })
        heroLabel = TextView(this)
        heroLabel.textSize = 12f
        heroLabel.letterSpacing = 0.12f
        heroLabel.setTextColor(Color.argb(190, 15, 17, 21))
        heroTop.addView(heroLabel)
        hero.addView(heroTop)
        heroBig = TextView(this)
        heroBig.textSize = 23f
        heroBig.setTextColor(BG)
        heroBig.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        heroBig.setPadding(0, dp(4), 0, 0)
        heroBig.maxLines = 2
        hero.addView(heroBig)
        heroSmall = TextView(this)
        heroSmall.textSize = 14f
        heroSmall.setTextColor(Color.argb(210, 15, 17, 21))
        hero.addView(heroSmall)
        val hlp = LinearLayout.LayoutParams(MP, WC)
        hlp.setMargins(dp(18), dp(4), dp(18), dp(10))
        col.addView(hero, hlp)
        hero.enter(0)
        pulse = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f).also {
            it.duration = 900
            it.repeatMode = ValueAnimator.REVERSE
            it.repeatCount = ValueAnimator.INFINITE
            it.start()
        }

        // Seletor Dia / Semana
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = shape(CARD, 20, LINE)
        seg.setPadding(dp(3), dp(3), dp(3), dp(3))
        segDia = segItem("Dia")
        segSem = segItem("Semana")
        segDia.setOnClickListener { mode = 0; applyMode() }
        segSem.setOnClickListener { mode = 1; applyMode() }
        seg.addView(segDia, LinearLayout.LayoutParams(0, dp(36), 1f))
        seg.addView(segSem, LinearLayout.LayoutParams(0, dp(36), 1f))
        val slp = LinearLayout.LayoutParams(MP, WC)
        slp.setMargins(dp(18), dp(2), dp(18), dp(10))
        col.addView(seg, slp)

        // Dias da semana
        tabsRow = LinearLayout(this)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        tabsRow.setPadding(dp(15), 0, dp(15), dp(6))
        val today = todayIdx()
        for (i in 0..6) {
            val c2 = Calendar.getInstance()
            c2.add(Calendar.DAY_OF_YEAR, i - today)
            val tab = LinearLayout(this)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER
            val n = TextView(this)
            n.text = dayNames[i].uppercase()
            n.textSize = 11f
            n.letterSpacing = 0.08f
            n.gravity = Gravity.CENTER
            val d = TextView(this)
            d.text = c2.get(Calendar.DAY_OF_MONTH).toString()
            d.textSize = 18f
            d.gravity = Gravity.CENTER
            d.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            tab.addView(n)
            tab.addView(d)
            tab.pressable()
            tab.setOnClickListener {
                selDay = i
                paintTabs()
                render(true)
            }
            val lp = LinearLayout.LayoutParams(0, dp(60), 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            tabsRow.addView(tab, lp)
            tabViews.add(tab)
        }
        col.addView(tabsRow)

        // Conteúdo
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(18), dp(4), dp(18), dp(110))
        scroll.addView(list)
        col.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))

        // Botão flutuante
        fab = TextView(this)
        fab.text = "+"
        fab.textSize = 34f
        fab.gravity = Gravity.CENTER
        fab.setTextColor(BG)
        fab.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ACC, ACC2)).also {
            it.shape = GradientDrawable.OVAL
        }
        fab.elevation = dp(10).toFloat()
        fab.pressable()
        fab.setOnClickListener {
            fab.animate().rotation(90f).setDuration(180).start()
            edit(null)
        }
        val flp = FrameLayout.LayoutParams(dp(66), dp(66), Gravity.BOTTOM or Gravity.END)
        flp.setMargins(0, 0, dp(24), dp(28))
        frame.addView(fab, flp)
        fab.scaleX = 0f
        fab.scaleY = 0f
        fab.animate().scaleX(1f).scaleY(1f).setStartDelay(350).setDuration(450)
            .setInterpolator(OvershootInterpolator(2.2f)).start()

        setContentView(frame)
        paintSeg()
        paintTabs()
    }

    private fun pill(text: String, color: Int, stroke: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTextColor(color)
        t.setPadding(dp(12), dp(8), dp(12), dp(8))
        t.background = shape(Color.TRANSPARENT, 20, stroke)
        t.pressable()
        return t
    }

    private fun segItem(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    private fun paintSeg() {
        segDia.background = if (mode == 0) grad(17) else null
        segSem.background = if (mode == 1) grad(17) else null
        segDia.setTextColor(if (mode == 0) BG else DIM)
        segSem.setTextColor(if (mode == 1) BG else DIM)
    }

    private fun paintTabs() {
        val today = todayIdx()
        for (i in 0..6) {
            val sel = i == selDay
            val v = tabViews[i]
            v.background = if (sel) grad(16) else shape(CARD, 16, if (i == today) Color.argb(110, 79, 195, 247) else LINE)
            (v.getChildAt(0) as TextView).setTextColor(if (sel) BG else (if (i == today) ACC else DIM))
            (v.getChildAt(1) as TextView).setTextColor(if (sel) BG else TXT)
        }
    }

    private fun applyMode() {
        paintSeg()
        paintTabs()
        tabsRow.visibility = if (mode == 0) View.VISIBLE else View.GONE
        render(true)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gd.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        Scheduler.rescheduleAll(this)
        focusPill.text = if (Pomo.phase(this) != 0) "Foco ●" else "Foco"
        val bad = Health.issues(this) > 0
        verify.text = if (bad) "Verificar ●" else "Verificar"
        verify.setTextColor(if (bad) Color.parseColor("#FF8A65") else DIM)
        dateTv.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "PT")).format(Date())
            .replaceFirstChar { it.uppercase() }
        render(firstShow)
        firstShow = false
        tick.removeCallbacks(ticker)
        tick.postDelayed(ticker, 30000L)
        checkPerms()
    }

    override fun onPause() {
        tick.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        pulse?.cancel()
        super.onDestroy()
    }

    // ---------- Agora / A seguir ----------
    private fun updateHero() {
        val all = Store.all(this)
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = todayIdx()
        var label = "SEM BLOCOS"
        var big = "Horário vazio"
        var small = "Toca em + para adicionar"
        var found = false
        for (off in 0..7) {
            if (found) break
            val d = (today + off) % 7
            for (a in blocksFor(d, all)) {
                val s = a.hour * 60 + a.minute
                val e = s + a.dur
                if (off == 0) {
                    if (a.dur > 0 && nowMin >= s && nowMin < e) {
                        label = "AGORA"
                        big = a.label
                        small = "até " + hhmm(e) + " · faltam " + Scheduler.fmtIn((e - nowMin) * 60000L)
                        found = true
                        break
                    }
                    if (s <= nowMin) continue
                    label = "A SEGUIR"
                    big = a.label
                    small = a.timeText() + " · daqui a " + Scheduler.fmtIn((s - nowMin) * 60000L)
                    found = true
                    break
                } else {
                    label = if (off == 1) "A SEGUIR · AMANHÃ" else "A SEGUIR · " + dayFull[d].uppercase()
                    big = a.label
                    small = a.timeText() + (if (a.dur > 0) "–" + hhmm(e) else "") + " · " + kindName(a.kind)
                    found = true
                    break
                }
            }
        }
        heroLabel.text = label
        heroBig.text = big
        heroSmall.text = small
        dot.visibility = if (found) View.VISIBLE else View.INVISIBLE
    }

    // ---------- Render ----------
    private fun render(animate: Boolean) {
        val all = Store.all(this)
        list.removeAllViews()
        if (mode == 0) renderDay(all, animate) else renderWeek(all, animate)
        updateHero()
    }

    private fun renderDay(all: List<Alarm>, animate: Boolean) {
        val blocks = blocksFor(selDay, all)
        val isToday = selDay == todayIdx()
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val h = TextView(this)
        h.text = dayFull[selDay] + (if (isToday) " · hoje" else "")
        h.textSize = 13f
        h.setTextColor(DIM)
        h.setPadding(dp(4), dp(2), 0, dp(6))
        list.addView(h)

        if (blocks.isEmpty()) {
            val e = TextView(this)
            e.text = "🌿\n\nDia livre.\nSem blocos neste dia."
            e.setTextColor(DIM)
            e.textSize = 16f
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(60), 0, 0)
            list.addView(e)
            if (animate) e.enter(150)
            return
        }
        for ((i, a) in blocks.withIndex()) {
            val v = block(a, isToday, nowMin)
            list.addView(v)
            if (animate) v.enter(80L + minOf(i, 8) * 60L)
        }
    }

    private fun block(a: Alarm, isToday: Boolean, nowMin: Int): View {
        val kc = kindColor(a.kind)
        val s = a.hour * 60 + a.minute
        val e = s + a.dur
        val live = isToday && a.dur > 0 && nowMin >= s && nowMin < e
        val past = isToday && !live && (if (a.dur > 0) nowMin >= e else nowMin > s)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        val t1 = TextView(this)
        t1.text = a.timeText()
        t1.textSize = 16f
        t1.setTextColor(TXT)
        t1.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val t2 = TextView(this)
        t2.text = if (a.dur > 0) hhmm(e) else ""
        t2.textSize = 13f
        t2.setTextColor(DIM)
        tcol.addView(t1)
        tcol.addView(t2)
        tcol.alpha = if (past) 0.55f else 1f
        row.addView(tcol, LinearLayout.LayoutParams(dp(54), WC))

        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = shape(if (live) withAlpha(kc, 38) else CARD, 20, if (live) kc else LINE)
        card.setPadding(dp(12), dp(14), dp(8), dp(14))
        card.alpha = if (past) 0.55f else 1f

        val bar = View(this)
        bar.background = shape(kc, 3)
        card.addView(bar, LinearLayout.LayoutParams(dp(4), MP).also { it.rightMargin = dp(12) })

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.alpha = if (a.enabled) 1f else 0.5f
        val tag = TextView(this)
        tag.text = kindName(a.kind).uppercase() + (if (live) "  ·  AGORA" else "")
        tag.textSize = 10.5f
        tag.letterSpacing = 0.1f
        tag.setTextColor(kc)
        tag.setTypeface(null, Typeface.BOLD)
        val name = TextView(this)
        name.text = a.label.ifEmpty { "Sem nome" }
        name.textSize = 17f
        name.setTextColor(TXT)
        name.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val sub = TextView(this)
        sub.text = (if (a.dur > 0) fmtDur(a.dur) + " · " else "") + a.daysText() +
            (if (a.lead > 0) " · avisa ${a.lead} min antes" else "")
        sub.textSize = 13f
        sub.setTextColor(DIM)
        content.addView(tag)
        content.addView(name)
        content.addView(sub)
        if (a.kind == 1 && live) {
            val f = TextView(this)
            f.text = "▶  Iniciar foco"
            f.textSize = 12f
            f.setTextColor(kc)
            f.setPadding(dp(12), dp(6), dp(12), dp(6))
            f.background = shape(Color.TRANSPARENT, 16, kc)
            f.pressable()
            f.setOnClickListener { startActivity(Intent(this, FocusActivity::class.java).putExtra("label", a.label)) }
            content.addView(f, LinearLayout.LayoutParams(WC, WC).also { it.topMargin = dp(10) })
        }
        if (live) {
            val pb = LinearLayout(this)
            pb.orientation = LinearLayout.HORIZONTAL
            pb.background = shape(LINE, 2)
            val done = View(this)
            done.background = shape(kc, 2)
            val frac = ((nowMin - s).toFloat() / a.dur).coerceIn(0.03f, 1f)
            pb.addView(done, LinearLayout.LayoutParams(0, MP, frac))
            pb.addView(View(this), LinearLayout.LayoutParams(0, MP, 1f - frac))
            content.addView(pb, LinearLayout.LayoutParams(MP, dp(4)).also { it.topMargin = dp(10) })
        }
        card.addView(content, LinearLayout.LayoutParams(0, WC, 1f))

        val bell = TextView(this)
        bell.text = if (a.enabled) "🔔" else "🔕"
        bell.textSize = 22f
        bell.setPadding(dp(10), dp(8), dp(10), dp(8))
        bell.setOnClickListener {
            a.enabled = !a.enabled
            Store.upsert(this, a)
            Scheduler.schedule(this, a)
            bell.text = if (a.enabled) "🔔" else "🔕"
            content.animate().alpha(if (a.enabled) 1f else 0.5f).setDuration(200).start()
            bell.animate().scaleX(1.3f).scaleY(1.3f).setDuration(90).withEndAction {
                bell.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator(3f)).start()
            }.start()
            updateHero()
            if (a.enabled) toastNext(a)
        }
        card.addView(bell)

        card.pressable()
        card.setOnClickListener { edit(a) }
        row.addView(card, LinearLayout.LayoutParams(0, WC, 1f))
        row.layoutParams = LinearLayout.LayoutParams(MP, WC).also { it.setMargins(0, dp(6), 0, dp(6)) }
        return row
    }

    private fun renderWeek(all: List<Alarm>, animate: Boolean) {
        val today = todayIdx()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(View(this), LinearLayout.LayoutParams(dp(46), dp(1)))
        for (d in 0..6) {
            val tv = TextView(this)
            tv.text = dayNames[d].uppercase()
            tv.gravity = Gravity.CENTER
            tv.textSize = 11f
            tv.letterSpacing = 0.06f
            tv.setTextColor(if (d == today) ACC else DIM)
            tv.setPadding(0, dp(8), 0, dp(8))
            tv.setOnClickListener {
                selDay = d
                mode = 0
                applyMode()
            }
            head.addView(tv, LinearLayout.LayoutParams(0, WC, 1f))
        }
        list.addView(head)

        val starts = all.map { it.hour * 60 + it.minute }.distinct().sorted()
        for ((ri, t) in starts.withIndex()) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            val tl = TextView(this)
            tl.text = hhmm(t)
            tl.textSize = 11f
            tl.setTextColor(DIM)
            r.addView(tl, LinearLayout.LayoutParams(dp(46), WC))
            for (d in 0..6) {
                val a = all.firstOrNull { (it.hour * 60 + it.minute) == t && (effDays(it) and (1 shl d)) != 0 }
                val cell = TextView(this)
                cell.gravity = Gravity.CENTER
                cell.textSize = 10f
                cell.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                if (a != null) {
                    val kc = kindColor(a.kind)
                    cell.text = abbr(a.label)
                    cell.setTextColor(kc)
                    cell.background = shape(withAlpha(kc, if (a.enabled) 70 else 28), 10)
                    cell.alpha = if (a.enabled) 1f else 0.6f
                    cell.setOnClickListener {
                        selDay = d
                        mode = 0
                        applyMode()
                    }
                } else {
                    cell.background = shape(withAlpha(CARD, 130), 10)
                }
                val lp = LinearLayout.LayoutParams(0, dp(54), 1f)
                lp.setMargins(dp(2), dp(2), dp(2), dp(2))
                r.addView(cell, lp)
            }
            list.addView(r)
            if (animate) r.enter(80L + minOf(ri, 8) * 60L)
        }

        val leg = LinearLayout(this)
        leg.orientation = LinearLayout.HORIZONTAL
        leg.gravity = Gravity.CENTER
        leg.setPadding(0, dp(18), 0, 0)
        for (k in intArrayOf(2, 1, 3)) {
            val x = TextView(this)
            x.text = "●  " + kindName(k)
            x.textSize = 13f
            x.setTextColor(kindColor(k))
            x.setPadding(dp(12), 0, dp(12), 0)
            leg.addView(x)
        }
        list.addView(leg)
        val hint = TextView(this)
        hint.text = "Toca num bloco para ver o dia"
        hint.textSize = 12f
        hint.setTextColor(DIM)
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, dp(8), 0, 0)
        list.addView(hint)
    }

    private fun confirmSchedule() {
        val d = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Repor horário LEE1TA")
            .setMessage("Apaga os blocos atuais e recria os ${Schedule.count()} blocos do horário de aulas e estudo.")
            .setPositiveButton("Repor") { _, _ ->
                Schedule.install(this, true)
                render(true)
            }
            .setNegativeButton("Cancelar", null)
            .show()
        d.window?.setBackgroundDrawable(shape(CARD, 26))
    }

    private fun toastNext(a: Alarm) {
        val ms = Scheduler.next(a) - System.currentTimeMillis()
        Toast.makeText(this, "Toca daqui a " + Scheduler.fmtIn(ms), Toast.LENGTH_SHORT).show()
    }

    // ---------- Editor ----------
    private fun edit(old: Alarm?) {
        val v = layoutInflater.inflate(R.layout.dialog_alarm, null)
        val tp = v.findViewById<TimePicker>(R.id.tp)
        val et = v.findViewById<EditText>(R.id.label)
        val etDur = v.findViewById<EditText>(R.id.dur)
        val spKind = v.findViewById<Spinner>(R.id.kind)
        val daysRow = v.findViewById<LinearLayout>(R.id.days)
        val sp = v.findViewById<Spinner>(R.id.snooze)

        tp.setIs24HourView(true)
        val now = Calendar.getInstance()
        tp.hour = old?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        tp.minute = old?.minute ?: now.get(Calendar.MINUTE)
        et.setText(old?.label ?: "")
        val od = old?.dur ?: 0
        etDur.setText(if (od > 0) od.toString() else "")
        spKind.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Outro", "Estudo", "Aula", "Rotina"))
        spKind.setSelection(old?.kind ?: (if (old == null) 1 else 0))

        var mask = old?.days ?: (if (mode == 0) 1 shl selDay else 0)
        val letters = listOf("S", "T", "Q", "Q", "S", "S", "D")
        for (i in 0..6) {
            val tv = TextView(this)
            tv.text = letters[i]
            tv.gravity = Gravity.CENTER
            tv.textSize = 15f
            tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fun paint() {
                val on = (mask and (1 shl i)) != 0
                tv.background = GradientDrawable().also {
                    it.shape = GradientDrawable.OVAL
                    it.setColor(if (on) ACC else LINE)
                }
                tv.setTextColor(if (on) BG else TXT)
            }
            paint()
            tv.setOnClickListener {
                mask = mask xor (1 shl i)
                paint()
                tv.animate().scaleX(0.75f).scaleY(0.75f).setDuration(70).withEndAction {
                    tv.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                }.start()
            }
            val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            daysRow.addView(tv, lp)
        }

        val snoozes = intArrayOf(5, 10, 15)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("5 min", "10 min", "15 min"))
        val si = snoozes.indexOf(old?.snooze ?: 10)
        sp.setSelection(if (si >= 0) si else 1)

        val spLead = v.findViewById<Spinner>(R.id.lead)
        val leads = intArrayOf(0, 5, 10, 15, 30)
        spLead.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Sem aviso", "5 min antes", "10 min antes", "15 min antes", "30 min antes")
        )
        spLead.setSelection(maxOf(0, leads.indexOf(old?.lead ?: 0)))

        val b = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (old == null) "Novo bloco" else "Editar bloco")
            .setView(v)
            .setPositiveButton("Guardar") { _, _ ->
                tp.clearFocus()
                val a = old ?: Alarm(Store.newId(this), 0, 0)
                a.hour = tp.hour
                a.minute = tp.minute
                a.label = et.text.toString().trim()
                a.days = mask
                a.dur = (etDur.text.toString().toIntOrNull() ?: 0).coerceIn(0, 1439)
                a.kind = spKind.selectedItemPosition
                a.snooze = snoozes[sp.selectedItemPosition]
                a.lead = leads[spLead.selectedItemPosition]
                a.enabled = true
                Store.upsert(this, a)
                Scheduler.schedule(this, a)
                toastNext(a)
                render(true)
            }
            .setNegativeButton("Cancelar", null)
        if (old != null) {
            b.setNeutralButton("Apagar") { _, _ ->
                Scheduler.cancel(this, old.id)
                Store.remove(this, old.id)
                render(true)
            }
        }
        val d = b.show()
        d.window?.setBackgroundDrawable(shape(CARD, 26))
        d.setOnDismissListener { fab.animate().rotation(0f).setDuration(180).start() }
    }

    // ---------- Permissões ----------
    private fun ask(msg: String, intent: Intent) {
        dlg = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Permissão necessária")
            .setMessage(msg)
            .setPositiveButton("Abrir definições") { _, _ ->
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton("Agora não") { _, _ -> skipped = true }
            .show()
        dlg?.window?.setBackgroundDrawable(shape(CARD, 26))
    }

    @SuppressLint("BatteryLife")
    private fun checkPerms() {
        if (dlg?.isShowing == true) return
        val pkg = Uri.parse("package:$packageName")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!askedNotif) {
                askedNotif = true
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                return
            }
            if (!skipped) {
                ask(
                    "Ativa as notificações para o alarme aparecer no ecrã.",
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
            return
        }
        if (skipped) return

        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            ask("Permite “Alarmes e lembretes” para os alarmes tocarem à hora certa.", Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            return
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            ask("Permite notificações em ecrã inteiro para o alarme abrir com o telemóvel bloqueado.", Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
            return
        }
        val prefs = getSharedPreferences("mtm", MODE_PRIVATE)
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("batAsked", false)) {
            prefs.edit().putBoolean("batAsked", true).apply()
            ask("Desativa a otimização de bateria para esta app, para o alarme nunca ser bloqueado.", Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        }
    }
}
