package com.mogas.timemanager

import android.content.Context

/** Horário LEE1TA (Turno Tarde) · 1º Ano / 1º Semestre · 2026-27 */
object Schedule {
    // Dias: Seg=1 Ter=2 Qua=4 Qui=8 Sex=16 Sáb=32 Dom=64
    // Tipo: 1 Estudo, 2 Aula, 3 Rotina
    private const val LEAD_AULA = 5

    private class S(val h: Int, val m: Int, val dur: Int, val label: String, val days: Int, val snooze: Int, val kind: Int)

    private val items = listOf(
        S(6, 0, 90, "Análise Matemática I", 1 or 2, 10, 1),
        S(6, 0, 90, "Intro. à Computação + Metodologia", 4, 10, 1),
        S(6, 0, 90, "Intro. Electrónica e Telecom. + Inglês", 8 or 16, 10, 1),

        S(13, 0, 90, "Análise Matemática I", 1, 5, 2),
        S(13, 0, 90, "Introdução à Computação", 2 or 4, 5, 2),
        S(13, 0, 90, "Intro. à Electrónica e Telecom.", 8, 5, 2),
        S(13, 0, 90, "Física I", 16, 5, 2),

        S(14, 40, 90, "Análise Matemática I", 1, 5, 2),
        S(14, 40, 90, "Metodologia de Inv. Científica", 2 or 4, 5, 2),
        S(14, 40, 90, "Língua Portuguesa", 8, 5, 2),
        S(14, 40, 90, "Física I", 16, 5, 2),

        S(16, 20, 80, "Inglês Instrumental", 1 or 2, 5, 2),
        S(16, 20, 80, "Intro. à Electrónica e Telecom.", 4, 5, 2),
        S(16, 20, 80, "Língua Portuguesa", 8 or 16, 5, 2),

        S(19, 0, 45, "Leitura da matéria", 31, 10, 1),

        S(21, 0, 60, "Preparação do dia seguinte", 15, 10, 3),
        S(21, 0, 60, "Resumo da semana", 16, 10, 3),

        S(15, 0, 120, "Física I + Análise Matemática I", 32, 10, 1),
        S(15, 0, 120, "Língua Portuguesa + dúvidas da semana", 64, 10, 1),
        S(21, 0, 60, "Preparação da semana", 32 or 64, 10, 3)
    )

    fun install(c: Context, replace: Boolean) {
        if (replace) {
            for (a in Store.all(c)) {
                Scheduler.cancel(c, a.id)
                Store.remove(c, a.id)
            }
        }
        for (s in items) {
            val a = Alarm(Store.newId(c), s.h, s.m, s.label, s.days, true, s.snooze, s.dur, s.kind, if (s.kind == 2) LEAD_AULA else 0)
            Store.upsert(c, a)
            Scheduler.schedule(c, a)
        }
        c.getSharedPreferences("mtm", Context.MODE_PRIVATE).edit().putBoolean("seeded2", true).putBoolean("lead5", true).apply()
    }

    /** Na primeira abertura desta versão, (re)cria o horário completo. */
    fun seedOnce(c: Context) {
        val p = c.getSharedPreferences("mtm", Context.MODE_PRIVATE)
        if (!p.getBoolean("seeded2", false)) {
            install(c, true)
        } else if (!p.getBoolean("lead5", false)) {
            // Atualização: aulas passam a avisar 5 min antes, sem apagar o resto
            for (a in Store.all(c)) {
                if (a.kind == 2 && a.lead == 0) {
                    a.lead = LEAD_AULA
                    Store.upsert(c, a)
                    Scheduler.schedule(c, a)
                }
            }
            p.edit().putBoolean("lead5", true).apply()
        }
    }

    fun count() = items.size
}
