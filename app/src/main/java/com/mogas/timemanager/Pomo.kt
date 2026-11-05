package com.mogas.timemanager

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Temporizador de foco (Pomodoro). O estado vive em SharedPreferences e o fim de cada fase é um alarme exato. */
object Pomo {
    const val FOCUS = 1
    const val SHORT = 2
    const val LONG = 3
    private const val EVERY = 4
    val presets = listOf(intArrayOf(25, 5, 15), intArrayOf(50, 10, 20), intArrayOf(45, 10, 20), intArrayOf(30, 5, 15))

    private fun p(c: Context) = c.getSharedPreferences("pomo", Context.MODE_PRIVATE)
    fun phase(c: Context) = p(c).getInt("phase", 0)
    fun paused(c: Context) = p(c).getBoolean("paused", false)
    fun endAt(c: Context) = p(c).getLong("endAt", 0L)
    fun remaining(c: Context) = p(c).getLong("rem", 0L)
    fun label(c: Context): String = p(c).getString("label", "") ?: ""
    fun lastLabel(c: Context): String = p(c).getString("lastLabel", "") ?: ""
    fun cycles(c: Context) = p(c).getInt("cycles", 0)
    fun preset(c: Context) = p(c).getInt("pre", 0).coerceIn(0, presets.size - 1)
    fun nextPreset(c: Context) {
        p(c).edit().putInt("pre", (preset(c) + 1) % presets.size).apply()
    }

    fun dur(c: Context, ph: Int): Int = presets[preset(c)][if (ph == FOCUS) 0 else if (ph == SHORT) 1 else 2]

    // ---------- controlo ----------
    fun start(c: Context, label: String) {
        val end = System.currentTimeMillis() + dur(c, FOCUS) * 60000L
        p(c).edit().putInt("phase", FOCUS).putBoolean("paused", false).putLong("endAt", end)
            .putInt("cycles", 0).putString("label", label).putString("lastLabel", label).apply()
        schedule(c, end)
        notifyOngoing(c)
    }

    fun pause(c: Context) {
        if (phase(c) == 0 || paused(c)) return
        val rem = maxOf(0L, endAt(c) - System.currentTimeMillis())
        p(c).edit().putBoolean("paused", true).putLong("rem", rem).apply()
        cancelAlarm(c)
        notifyOngoing(c)
    }

    fun resume(c: Context) {
        if (phase(c) == 0 || !paused(c)) return
        val end = System.currentTimeMillis() + remaining(c)
        p(c).edit().putBoolean("paused", false).putLong("endAt", end).apply()
        schedule(c, end)
        notifyOngoing(c)
    }

    fun stop(c: Context) {
        p(c).edit().putInt("phase", 0).putBoolean("paused", false).putInt("cycles", 0).apply()
        cancelAlarm(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.cancel(2)
    }

    /** Fim de fase. natural = o tempo acabou; false = o utilizador saltou a fase. */
    fun advance(c: Context, natural: Boolean) {
        val ph = phase(c)
        if (ph == 0) return
        var cycles = cycles(c)
        val next: Int
        val title: String
        val msg: String
        if (ph == FOCUS) {
            if (natural) {
                log(c, label(c), dur(c, FOCUS))
                cycles++
            }
            next = if (natural && cycles % EVERY == 0) LONG else SHORT
            title = if (natural) "Foco concluído" else "Fase saltada"
            msg = "Pausa de " + dur(c, next) + " min" + (if (next == LONG) " (pausa longa)" else "")
        } else {
            next = FOCUS
            title = "Pausa terminada"
            msg = "Hora de focar: " + dur(c, FOCUS) + " min"
        }
        val end = System.currentTimeMillis() + dur(c, next) * 60000L
        p(c).edit().putInt("phase", next).putBoolean("paused", false).putLong("endAt", end).putInt("cycles", cycles).apply()
        schedule(c, end)
        notifyOngoing(c)
        if (natural) alert(c, title, msg)
    }

    /** Recupera o estado depois de reiniciar o telemóvel ou de um alarme perdido. */
    fun restore(c: Context) {
        if (phase(c) == 0) return
        if (paused(c)) {
            notifyOngoing(c)
            return
        }
        val now = System.currentTimeMillis()
        val end = endAt(c)
        when {
            end > now -> {
                schedule(c, end)
                notifyOngoing(c)
            }
            end > now - 120000L -> advance(c, true)
            else -> stop(c)
        }
    }

    // ---------- alarme ----------
    private fun endPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 7001, Intent(c, PomoReceiver::class.java).setAction("END"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(c: Context, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endPi(c))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endPi(c))
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endPi(c))
        }
    }

    private fun cancelAlarm(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(endPi(c))
    }

    // ---------- notificações ----------
    private fun act(c: Context, a: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        c, code, Intent(c, PomoReceiver::class.java).setAction(a),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openPi(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 5, Intent(c, FocusActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun notifyOngoing(c: Context) {
        val ph = phase(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        if (ph == 0) {
            nm.cancel(2)
            return
        }
        val ch = NotificationChannel("pomo", "Temporizador de foco", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
        val title = when (ph) {
            FOCUS -> "Foco" + (if (label(c).isNotEmpty()) " · " + label(c) else "")
            SHORT -> "Pausa curta"
            else -> "Pausa longa"
        }
        val b = Notification.Builder(c, "pomo")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi(c))
        if (paused(c)) {
            b.setContentText("Em pausa")
            @Suppress("DEPRECATION")
            b.addAction(0, "Retomar", act(c, "RESUME", 7002))
        } else {
            b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true).setWhen(endAt(c))
            b.setContentText("Tempo restante")
            @Suppress("DEPRECATION")
            b.addAction(0, "Pausar", act(c, "PAUSE", 7003))
        }
        @Suppress("DEPRECATION")
        b.addAction(0, "Parar", act(c, "STOP", 7004))
        nm.notify(2, b.build())
    }

    private fun alert(c: Context, title: String, msg: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel("pomo_end", "Fim de fase (foco)", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
        val n = Notification.Builder(c, "pomo_end")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)
            .setTimeoutAfter(15000L)
            .setContentIntent(openPi(c))
            .build()
        nm.notify(3, n)
    }

    // ---------- estatísticas ----------
    private fun logArr(c: Context): JSONArray = try {
        JSONArray(p(c).getString("log", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun log(c: Context, label: String, min: Int) {
        val now = System.currentTimeMillis()
        val old = logArr(c)
        val keep = JSONArray()
        for (i in 0 until old.length()) {
            val o = old.getJSONObject(i)
            if (o.getLong("t") >= now - 90L * 86400000L) keep.put(o)
        }
        keep.put(JSONObject().put("t", now).put("l", if (label.isEmpty()) "Sem nome" else label).put("m", min))
        p(c).edit().putString("log", keep.toString()).apply()
    }

    private fun weekStart(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Minutos de foco por disciplina nesta semana (maior primeiro). */
    fun weekStats(c: Context): List<Pair<String, Int>> {
        val ws = weekStart()
        val map = LinkedHashMap<String, Int>()
        val arr = logArr(c)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("t") >= ws) map[o.getString("l")] = (map[o.getString("l")] ?: 0) + o.getInt("m")
        }
        return map.entries.map { Pair(it.key, it.value) }.sortedByDescending { it.second }
    }

    /** Ciclos e minutos de hoje. */
    fun today(c: Context): Pair<Int, Int> {
        val ds = dayStart()
        var n = 0
        var m = 0
        val arr = logArr(c)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("t") >= ds) {
                n++
                m += o.getInt("m")
            }
        }
        return Pair(n, m)
    }

    fun weekTotal(c: Context): Int = weekStats(c).sumOf { it.second }
}

class PomoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            "END" -> if (Pomo.phase(c) != 0 && !Pomo.paused(c) && System.currentTimeMillis() >= Pomo.endAt(c) - 3000L) Pomo.advance(c, true)
            "PAUSE" -> Pomo.pause(c)
            "RESUME" -> Pomo.resume(c)
            "STOP" -> Pomo.stop(c)
        }
    }
}
