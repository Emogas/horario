package com.mogas.timemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val a = Store.get(c, id) ?: return
        val t = i.getLongExtra("t", 0L)
        val now = System.currentTimeMillis()
        c.getSharedPreferences("mtm", Context.MODE_PRIVATE).edit()
            .putLong("last", now).putLong("lastDelay", if (t > 0) now - t else 0L)
            .putString("lastLabel", a.label).apply()
        if (id > 0 && !i.getBooleanExtra("snooze", false)) {
            if (a.days == 0) {
                a.enabled = false
                Store.upsert(c, a)
            } else {
                Scheduler.schedule(c, a)
            }
        }
        c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("id", id))
    }
}
