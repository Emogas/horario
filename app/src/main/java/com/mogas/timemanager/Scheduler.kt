package com.mogas.timemanager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun pi(c: Context, id: Int, snooze: Boolean, t: Long = 0L): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze).putExtra("t", t)
        val code = if (snooze) id + 100000 else id
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showPi(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun next(a: Alarm, now: Long = System.currentTimeMillis()): Long {
        for (d in 0..7) {
            val c = Calendar.getInstance()
            c.timeInMillis = now
            c.add(Calendar.DAY_OF_YEAR, d)
            c.set(Calendar.HOUR_OF_DAY, a.hour)
            c.set(Calendar.MINUTE, a.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Seg=0 ... Dom=6
            val trigger = c.timeInMillis - a.lead * 60000L
            if (trigger > now && (a.days == 0 || (a.days and (1 shl idx)) != 0)) return trigger
        }
        return now + 86400000L
    }

    fun at(c: Context, id: Int, t: Long, snooze: Boolean) {
        try {
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(t, showPi(c)), pi(c, id, snooze, t))
        } catch (e: SecurityException) {
        }
    }

    fun schedule(c: Context, a: Alarm) {
        if (!a.enabled) {
            cancel(c, a.id)
            return
        }
        at(c, a.id, next(a), false)
    }

    fun snooze(c: Context, a: Alarm) {
        at(c, a.id, System.currentTimeMillis() + a.snooze * 60000L, true)
    }

    fun cancel(c: Context, id: Int) {
        am(c).cancel(pi(c, id, false))
        am(c).cancel(pi(c, id, true))
    }

    fun rescheduleAll(c: Context) {
        for (a in Store.all(c)) schedule(c, a)
    }

    fun fmtIn(ms: Long): String {
        val m = (ms + 59999) / 60000
        val d = m / 1440
        val h = (m % 1440) / 60
        val mm = m % 60
        val p = mutableListOf<String>()
        if (d > 0) p.add("$d d")
        if (h > 0) p.add("$h h")
        if (mm > 0 || p.isEmpty()) p.add("$mm min")
        return p.joinToString(" ")
    }

    fun nextAlarm(c: Context): Pair<Alarm, Long>? {
        val now = System.currentTimeMillis()
        return Store.all(c).filter { it.enabled }.map { Pair(it, next(it, now)) }.minByOrNull { it.second }
    }

    fun nextIn(c: Context): String? {
        val now = System.currentTimeMillis()
        val t = Store.all(c).filter { it.enabled }.map { next(it, now) }.minOrNull() ?: return null
        return fmtIn(t - now)
    }
}
