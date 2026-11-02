// Horário LEE1TA (turno tarde) — 1º ano / 1º semestre 2026-27
// dias: 0=Domingo, 1=Segunda ... 6=Sábado
window.HORARIO_PADRAO = [
  { t: 'Estudo: Análise Matemática I', tipo: 'estudo', dias: [1, 2], i: '06:00', f: '07:30' },
  { t: 'Estudo: Intro. à Computação + Metodologia', tipo: 'estudo', dias: [3], i: '06:00', f: '07:30' },
  { t: 'Estudo: Intro. Electrónica e Telecom. + Inglês', tipo: 'estudo', dias: [4, 5], i: '06:00', f: '07:30' },

  { t: 'Análise Matemática I', tipo: 'aula', dias: [1], i: '13:00', f: '14:30' },
  { t: 'Introdução à Computação', tipo: 'aula', dias: [2, 3], i: '13:00', f: '14:30' },
  { t: 'Intro. à Electrónica e Telecom.', tipo: 'aula', dias: [4], i: '13:00', f: '14:30' },
  { t: 'Física I', tipo: 'aula', dias: [5], i: '13:00', f: '14:30' },

  { t: 'Análise Matemática I', tipo: 'aula', dias: [1], i: '14:40', f: '16:10' },
  { t: 'Metodologia de Inv. Científica', tipo: 'aula', dias: [2, 3], i: '14:40', f: '16:10' },
  { t: 'Língua Portuguesa', tipo: 'aula', dias: [4], i: '14:40', f: '16:10' },
  { t: 'Física I', tipo: 'aula', dias: [5], i: '14:40', f: '16:10' },

  { t: 'Inglês Instrumental', tipo: 'aula', dias: [1, 2], i: '16:20', f: '17:40' },
  { t: 'Intro. à Electrónica e Telecom.', tipo: 'aula', dias: [3], i: '16:20', f: '17:40' },
  { t: 'Língua Portuguesa', tipo: 'aula', dias: [4, 5], i: '16:20', f: '17:40' },

  { t: 'Leitura da matéria', tipo: 'rotina', dias: [1, 2, 3, 4, 5], i: '19:00', f: '19:45' },
  { t: 'Preparação do dia de amanhã', tipo: 'rotina', dias: [1, 2, 3, 4], i: '21:00', f: '22:00' },
  { t: 'Resumo da semana', tipo: 'rotina', dias: [5], i: '21:00', f: '22:00' },

  { t: 'Estudo: Física I + Análise Matemática I', tipo: 'estudo', dias: [6], i: '15:00', f: '17:00' },
  { t: 'Estudo: Língua Portuguesa + dúvidas da semana', tipo: 'estudo', dias: [0], i: '15:00', f: '17:00' },
  { t: 'Preparação da semana', tipo: 'rotina', dias: [0, 6], i: '21:00', f: '22:00' }
];
