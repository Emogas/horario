package com.mogas.timemanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

data class Alarm(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var label: String = "",
    var days: Int = 0,
    var enabled: Boolean = true,
    var snooze: Int = 10,
    var dur: Int = 0,
    var kind: Int = 0,
    var lead: Int = 0
) {
    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun daysText(): String {
        if (days == 0) return "Uma vez"
        if (days == 127) return "Todos os dias"
        val n = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
        val idx = (0..6).filter { (days and (1 shl it)) != 0 }
        if (idx.size == 2) return n[idx[0]] + " e " + n[idx[1]]
        if (idx.size >= 3 && idx.last() - idx.first() == idx.size - 1) return n[idx.first()] + " a " + n[idx.last()]
        return idx.joinToString(" ") { n[it] }
    }
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("mtm", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<Alarm> {
        val out = mutableListOf<Alarm>()
        try {
            val arr = JSONArray(p(c).getString("alarms", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Alarm(
                        o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optString("l", ""),
                        o.optInt("d", 0), o.optBoolean("e", true), o.optInt("s", 10), o.optInt("du", 0), o.optInt("k", 0), o.optInt("ld", 0)
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun save(c: Context, list: List<Alarm>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(
                JSONObject().put("id", a.id).put("h", a.hour).put("m", a.minute)
                    .put("l", a.label).put("d", a.days).put("e", a.enabled).put("s", a.snooze).put("du", a.dur).put("k", a.kind).put("ld", a.lead)
            )
        }
        p(c).edit().putString("alarms", arr.toString()).apply()
    }

    fun get(c: Context, id: Int): Alarm? {
        if (id == 0) { // alarme de teste
            val now = java.util.Calendar.getInstance()
            return Alarm(0, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), "Teste", 0, false, 5)
        }
        return all(c).firstOrNull { it.id == id }
    }

    fun upsert(c: Context, a: Alarm) {
        val l = all(c)
        val i = l.indexOfFirst { it.id == a.id }
        if (i >= 0) l[i] = a else l.add(a)
        save(c, l)
    }

    fun remove(c: Context, id: Int) {
        save(c, all(c).filter { it.id != id })
    }

    fun newId(c: Context): Int {
        val n = p(c).getInt("next", 1)
        p(c).edit().putInt("next", n + 1).apply()
        return n
    }
}
