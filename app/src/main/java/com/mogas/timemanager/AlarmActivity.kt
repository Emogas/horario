package com.mogas.timemanager

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {
    private val anims = mutableListOf<ObjectAnimator>()
    private var closing = false

    private val done = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            close()
        }
    }

    private fun close() {
        if (closing) return
        closing = true
        findViewById<View>(android.R.id.content).animate().alpha(0f).setDuration(220)
            .withEndAction { finish() }.start()
    }

    private fun round(c: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c)
        g.cornerRadius = dp(34).toFloat()
        return g
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#12151C")
        window.navigationBarColor = Color.parseColor("#0B0D11")
        val id = intent.getIntExtra("id", -1)
        val a = Store.get(this, id) ?: Alarm(id, 0, 0, "Alarme")
        val bg = Color.parseColor("#0F1115")
        val acc = Color.parseColor("#4FC3F7")
        val acc2 = Color.parseColor("#8E7CFF")

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1B2030"), Color.parseColor("#0B0D11"))
        )
        root.setPadding(dp(28), dp(28), dp(28), dp(36))

        // Zona central: ondas a pulsar + sino a abanar
        val centre = LinearLayout(this)
        centre.orientation = LinearLayout.VERTICAL
        centre.gravity = Gravity.CENTER
        root.addView(centre, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val stage = FrameLayout(this)
        for (i in 0..2) {
            val ring = View(this)
            ring.background = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.argb(38, 79, 195, 247))
                it.setStroke(dp(2), Color.argb(150, 79, 195, 247))
            }
            stage.addView(ring, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER))
            val an = ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.55f, 1.55f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.55f, 1.55f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f)
            )
            an.duration = 2400
            an.startDelay = i * 800L
            an.repeatCount = ValueAnimator.INFINITE
            an.interpolator = DecelerateInterpolator()
            an.start()
            anims.add(an)
        }
        val bell = TextView(this)
        bell.text = "⏰"
        bell.textSize = 64f
        bell.gravity = Gravity.CENTER
        stage.addView(bell, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER))
        val wob = ObjectAnimator.ofFloat(
            bell, View.ROTATION, 0f, -16f, 16f, -12f, 12f, -7f, 7f, 0f, 0f, 0f, 0f, 0f
        )
        wob.duration = 2000
        wob.repeatCount = ValueAnimator.INFINITE
        wob.start()
        anims.add(wob)
        centre.addView(stage, LinearLayout.LayoutParams(dp(300), dp(300)))

        if (a.lead > 0) {
            val cap = TextView(this)
            cap.text = "COMEÇA ÀS"
            cap.textSize = 13f
            cap.letterSpacing = 0.15f
            cap.setTextColor(Color.parseColor("#8A92A0"))
            cap.gravity = Gravity.CENTER
            centre.addView(cap)
        }
        val time = TextView(this)
        time.text = a.timeText()
        time.textSize = 84f
        time.setTextColor(Color.WHITE)
        time.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        time.gravity = Gravity.CENTER
        centre.addView(time)

        val label = TextView(this)
        label.text = if (a.label.isEmpty()) "Alarme" else a.label
        label.textSize = 22f
        label.setTextColor(acc)
        label.gravity = Gravity.CENTER
        centre.addView(label)

        // Botões
        val snooze = TextView(this)
        snooze.text = "Adiar ${a.snooze} min"
        snooze.textSize = 18f
        snooze.gravity = Gravity.CENTER
        snooze.setTextColor(Color.WHITE)
        snooze.background = round(Color.parseColor("#2A2F3A"))
        snooze.pressable()
        snooze.setOnClickListener {
            startService(Intent(this, AlarmService::class.java).setAction("SNOOZE").putExtra("id", a.id))
            close()
        }
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        slp.bottomMargin = dp(14)
        root.addView(snooze, slp)

        val stop = TextView(this)
        stop.text = "Parar"
        stop.textSize = 24f
        stop.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stop.gravity = Gravity.CENTER
        stop.setTextColor(bg)
        stop.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(acc, acc2)).also {
            it.cornerRadius = dp(40).toFloat()
        }
        stop.pressable()
        stop.setOnClickListener {
            startService(Intent(this, AlarmService::class.java).setAction("STOP"))
            close()
        }
        root.addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)))

        setContentView(root)

        stage.enter(0)
        time.enter(120)
        label.enter(220)
        snooze.enter(340)
        stop.enter(440)

        val f = IntentFilter("com.mogas.timemanager.DONE")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(done, f)
    }

    override fun onDestroy() {
        for (an in anims) an.cancel()
        try {
            unregisterReceiver(done)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
