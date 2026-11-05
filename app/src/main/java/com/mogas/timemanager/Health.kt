package com.mogas.timemanager

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Health {
    fun notifOk(c: Context) = c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun exactOk(c: Context) = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun fullOk(c: Context) = Build.VERSION.SDK_INT < 34 || c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun battOk(c: Context) = c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    fun issues(c: Context): Int = listOf(notifOk(c), exactOk(c), fullOk(c), battOk(c)).count { !it }
}

class HealthActivity : Activity() {
    private val BG = Color.parseColor("#0F1115")
    private val CARD = Color.parseColor("#1A1D24")
    private val LINE = Color.parseColor("#262B36")
    private val ACC = Color.parseColor("#4FC3F7")
    private val ACC2 = Color.parseColor("#8E7CFF")
    private val OK = Color.parseColor("#4DD0A1")
    private val BAD = Color.parseColor("#FF8A65")
    private val DIM = Color.parseColor("#8A92A0")
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private lateinit var box: LinearLayout

    private val oemScreens = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
    )

    private fun shape(fill: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#12151C")
        window.navigationBarColor = Color.parseColor("#0B0D11")
        val scroll = ScrollView(this)
        scroll.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#12151C"), Color.parseColor("#0B0D11"))
        )
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(24), dp(18), dp(32))
        scroll.addView(box)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun go(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
            }
        }
    }

    private fun openOem() {
        for ((p, cl) in oemScreens) {
            try {
                startActivity(Intent().setClassName(p, cl))
                return
            } catch (e: Exception) {
            }
        }
        go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** state: 0 = problema, 1 = ok, 2 = informação */
    private fun item(state: Int, title: String, desc: String, action: String?, run: (() -> Unit)?): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(16), dp(14), dp(14), dp(14))
        card.background = shape(CARD, 20, if (state == 0) Color.argb(140, 255, 138, 101) else LINE)

        val ic = TextView(this)
        ic.text = when (state) {
            0 -> "!"
            1 -> "✓"
            else -> "i"
        }
        ic.textSize = 17f
        ic.gravity = Gravity.CENTER
        ic.setTextColor(BG)
        ic.setTypeface(null, Typeface.BOLD)
        ic.background = GradientDrawable().also {
            it.shape = GradientDrawable.OVAL
            it.setColor(when (state) { 0 -> BAD; 1 -> OK; else -> ACC })
        }
        card.addView(ic, LinearLayout.LayoutParams(dp(32), dp(32)).also { it.rightMargin = dp(14) })

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = title
        t.textSize = 16f
        t.setTextColor(Color.WHITE)
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val d = TextView(this)
        d.text = desc
        d.textSize = 13f
        d.setTextColor(DIM)
        col.addView(t)
        col.addView(d)
        card.addView(col, LinearLayout.LayoutParams(0, WC, 1f))

        if (state != 1 && action != null && run != null) {
            val btn = TextView(this)
            btn.text = action
            btn.textSize = 13f
            btn.setTextColor(BG)
            btn.setTypeface(null, Typeface.BOLD)
            btn.setPadding(dp(14), dp(9), dp(14), dp(9))
            btn.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ACC, ACC2)).also {
                it.cornerRadius = dp(18).toFloat()
            }
            btn.pressable()
            btn.setOnClickListener { run() }
            card.addView(btn, LinearLayout.LayoutParams(WC, WC).also { it.leftMargin = dp(8) })
        }
        card.layoutParams = LinearLayout.LayoutParams(MP, WC).also { it.setMargins(0, dp(6), 0, dp(6)) }
        return card
    }

    private fun section(text: String): View {
        val t = TextView(this)
        t.text = text.uppercase()
        t.textSize = 12f
        t.letterSpacing = 0.12f
        t.setTextColor(DIM)
        t.setPadding(dp(4), dp(18), 0, dp(4))
        return t
    }

    @SuppressLint("BatteryLife")
    private fun build() {
        box.removeAllViews()
        var n = 0
        fun add(v: View) {
            box.addView(v)
            v.enter(50L * n)
            n++
        }
        val pkg = Uri.parse("package:$packageName")

        val title = TextView(this)
        title.text = "Fiabilidade"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val sub = TextView(this)
        sub.text = "Para o alarme tocar sempre, tudo aqui tem de estar certo."
        sub.textSize = 14f
        sub.setTextColor(DIM)
        box.addView(title)
        box.addView(sub)

        add(section("Permissões"))
        add(item(if (Health.notifOk(this)) 1 else 0, "Notificações", "Necessárias para o ecrã do alarme.", "Permitir") {
            go(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        })
        add(item(if (Health.exactOk(this)) 1 else 0, "Alarmes exatos", "Para tocar à hora certa, ao segundo.", "Permitir") {
            go(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        })
        add(item(if (Health.fullOk(this)) 1 else 0, "Ecrã inteiro", "Para abrir por cima do ecrã de bloqueio.", "Permitir") {
            go(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        })
        add(item(if (Health.battOk(this)) 1 else 0, "Bateria sem restrições", "Evita que o sistema bloqueie a app.", "Permitir") {
            go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        })

        add(section("Fabricante: " + Build.MANUFACTURER))
        val m = Build.MANUFACTURER.lowercase()
        val tip = when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                "Ativa o Arranque automático e põe a Poupança de bateria em Sem restrições."
            m.contains("samsung") ->
                "Em Bateria, tira a app das Apps em suspensão e escolhe Sem restrições."
            m.contains("huawei") || m.contains("honor") ->
                "Em Iniciar aplicações, escolhe Gerir manualmente e liga tudo para esta app."
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
                "Ativa o Arranque automático e permite atividade em segundo plano."
            m.contains("vivo") || m.contains("iqoo") ->
                "Ativa o Arranque automático e permite atividade em segundo plano."
            else -> "Em Definições > Apps > Mogas Time Manager > Bateria, escolhe Sem restrições."
        }
        add(item(2, "Arranque e segundo plano", tip, "Abrir") { openOem() })

        add(section("Horário"))
        add(item(2, "Repor horário LEE1TA", "Apaga os blocos atuais e recria os " + Schedule.count() + " do horário.", "Repor") {
            val dl = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Repor horário LEE1TA")
                .setMessage("Apaga os blocos atuais e recria o horário original.")
                .setPositiveButton("Repor") { _, _ ->
                    Schedule.install(this, true)
                    Toast.makeText(this, "Horário reposto", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            dl.window?.setBackgroundDrawable(shape(CARD, 26))
        })

        add(section("Testes"))
        add(item(2, "Tocar agora", "Simula um alarme já, para ouvires o som e veres o ecrã.", "Tocar") {
            startForegroundService(Intent(this, AlarmService::class.java).putExtra("id", 0))
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("id", 0))
        })
        add(item(2, "Testar com o ecrã bloqueado", "Toca daqui a 10 s. Bloqueia o telemóvel e espera.", "Iniciar") {
            Scheduler.at(this, 0, System.currentTimeMillis() + 10000L, false)
            Toast.makeText(this, "Bloqueia o ecrã e espera 10 segundos", Toast.LENGTH_LONG).show()
        })

        val p = getSharedPreferences("mtm", Context.MODE_PRIVATE)
        val last = p.getLong("last", 0L)
        if (last == 0L) {
            add(item(2, "Último alarme", "Ainda nenhum alarme tocou neste telemóvel.", null, null))
        } else {
            val delay = p.getLong("lastDelay", 0L) / 1000
            val whenTxt = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "PT")).format(Date(last))
            val late = delay > 60
            add(
                item(
                    if (late) 0 else 1, "Último alarme",
                    whenTxt + " · " + p.getString("lastLabel", "") + "\nAtraso: " + delay + " s" +
                        (if (late) " (o sistema atrasou o alarme)" else ""),
                    null, null
                )
            )
        }
    }
}
