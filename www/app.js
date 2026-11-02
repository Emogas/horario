(function () {
  'use strict';

  var Cap = window.Capacitor || {};
  var P = Cap.Plugins || {};
  var LN = P.LocalNotifications;
  var PREF = P.Preferences;

  var DIAS = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
  var DIAS_C = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];
  var ORDEM = [1, 2, 3, 4, 5, 6, 0];
  var TIPOS = {
    aula: { n: 'Aula', c: '#2B59C3' },
    estudo: { n: 'Estudo', c: '#E0952B' },
    rotina: { n: 'Rotina', c: '#1E8E83' },
    outro: { n: 'Outro', c: '#8A4F9E' }
  };
  var CANAL = 'alarmes_v1';
  var ID_TESTE = 900000001;

  var S = null;
  var vista = 'hoje';
  var diaSel = new Date().getDay();

  /* ---------- utilitários ---------- */
  function $(s) { return document.querySelector(s); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function chave(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  function min(h) { var p = h.split(':'); return +p[0] * 60 + +p[1]; }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function dur(m) {
    var h = Math.floor(m / 60), r = m % 60;
    return h ? h + ' h' + (r ? ' ' + r + ' min' : '') : r + ' min';
  }
  var tt;
  function aviso(txt) {
    var t = $('#toast');
    t.textContent = txt;
    t.classList.add('on');
    clearTimeout(tt);
    tt = setTimeout(function () { t.classList.remove('on'); }, 3200);
  }

  /* ---------- armazenamento local ---------- */
  var store = {
    get: async function (k) {
      var v;
      if (PREF) { v = (await PREF.get({ key: k })).value; }
      else { v = localStorage.getItem(k); }
      return v ? JSON.parse(v) : null;
    },
    set: async function (k, v) {
      var s = JSON.stringify(v);
      if (PREF) { await PREF.set({ key: k, value: s }); }
      else { localStorage.setItem(k, s); }
    }
  };
  function guardar() { return store.set('mtm_estado', S); }

  function horarioPadrao() {
    var id = 0, out = [];
    window.HORARIO_PADRAO.forEach(function (b) {
      id++;
      out.push({
        id: id, t: b.t, tipo: b.tipo, dias: b.dias.slice(), i: b.i, f: b.f,
        alarme: true, antes: b.tipo === 'aula' ? 10 : 0
      });
    });
    return { acts: out, nextId: id + 1 };
  }

  /* ---------- alarmes ---------- */
  async function criarCanal() {
    if (!LN) return;
    try {
      await LN.createChannel({
        id: CANAL, name: 'Alarmes de horário',
        description: 'Avisos de aulas, estudo e rotinas',
        importance: 5, visibility: 1, vibration: true, lights: true,
        sound: 'alarme.wav'
      });
    } catch (e) { /* canal já existe */ }
  }

  async function permissoes(pedir) {
    if (!LN) return { web: true };
    var p = await LN.checkPermissions();
    if (pedir && (p.display === 'prompt' || p.display === 'prompt-with-rationale')) {
      p = await LN.requestPermissions();
    }
    var ex = null;
    try { ex = (await LN.checkExactNotificationSetting()).exact_alarm; } catch (e) { /* indisponível */ }
    return { display: p.display, exact: ex };
  }

  async function reagendar() {
    if (!LN) return 0;
    try {
      var pend = await LN.getPending();
      if (pend.notifications.length) {
        await LN.cancel({ notifications: pend.notifications.map(function (n) { return { id: n.id }; }) });
      }
      var lista = [];
      S.acts.forEach(function (a) {
        if (!a.alarme) return;
        a.dias.forEach(function (d) {
          var m = min(a.i) - (a.antes || 0), wd = d;
          if (m < 0) { m += 1440; wd = (d + 6) % 7; }
          lista.push({
            id: a.id * 10 + (d + 1),
            title: a.antes ? 'Daqui a ' + a.antes + ' min: ' + a.t : 'Agora: ' + a.t,
            body: a.i + ' – ' + a.f + ' · ' + TIPOS[a.tipo].n,
            channelId: CANAL,
            smallIcon: 'ic_stat_alarm',
            schedule: { on: { weekday: wd + 1, hour: Math.floor(m / 60), minute: m % 60 }, allowWhileIdle: true }
          });
        });
      });
      if (lista.length) await LN.schedule({ notifications: lista });
      return lista.length;
    } catch (e) {
      aviso('Erro ao agendar alarmes: ' + (e.message || e));
      return 0;
    }
  }

  async function testarAlarme() {
    if (!LN) { aviso('Só funciona no telemóvel (APK).'); return; }
    var p = await permissoes(true);
    if (p.display !== 'granted') { aviso('Activa as notificações primeiro.'); return; }
    await criarCanal();
    await LN.schedule({
      notifications: [{
        id: ID_TESTE, title: 'Teste de alarme',
        body: 'Se ouviste isto, os alarmes funcionam.',
        channelId: CANAL, smallIcon: 'ic_stat_alarm',
        schedule: { at: new Date(Date.now() + 10000), allowWhileIdle: true }
      }]
    });
    aviso('Alarme em 10 s. Bloqueia o ecrã e fecha a app para testar.');
  }

  /* ---------- dados do dia ---------- */
  function blocosDe(d) {
    return S.acts.filter(function (a) { return a.dias.indexOf(d) >= 0; })
      .sort(function (x, y) { return min(x.i) - min(y.i); });
  }

  /* ---------- vistas ---------- */
  function cabecalho(t, s) {
    $('#top').innerHTML = '<h1>' + esc(t) + '</h1>' + (s ? '<p>' + esc(s) + '</p>' : '');
  }

  function linha(a, extra) {
    var c = TIPOS[a.tipo].c;
    var cls = 'cartao' + (extra.agora ? ' agora' : '') + (extra.fim ? ' fim' : '');
    var meta = TIPOS[a.tipo].n + ' · ' + dur(min(a.f) - min(a.i)) +
      (a.alarme ? ' · alarme' + (a.antes ? ' ' + a.antes + ' min antes' : '') : '') +
      (extra.agora ? ' · a decorrer' : '');
    var marcas = '';
    if (extra.marcar) {
      marcas = '<div class="marcas">' +
        '<button data-m="done" data-id="' + a.id + '" class="' + (extra.estado === 'done' ? 'on-done' : '') + '">Feito</button>' +
        '<button data-m="skip" data-id="' + a.id + '" class="' + (extra.estado === 'skip' ? 'on-skip' : '') + '">Falhei</button></div>';
    }
    return '<div class="row"><div class="hora"><b>' + a.i + '</b><span>' + a.f + '</span></div>' +
      '<div class="' + cls + '" style="--c:' + c + '"><div data-edit="' + a.id + '"><h3>' + esc(a.t) + '</h3>' +
      '<div class="meta">' + esc(meta) + '</div></div>' + marcas + '</div></div>';
  }

  function vHoje() {
    var agora = new Date(), nm = agora.getHours() * 60 + agora.getMinutes();
    var lg = S.log[chave(agora)] || {}, bl = blocosDe(agora.getDay());
    var feitos = bl.filter(function (a) { return lg[a.id] === 'done'; }).length;
    var atual = bl.filter(function (a) { return min(a.i) <= nm && nm < min(a.f); })[0];
    var prox = bl.filter(function (a) { return min(a.i) > nm; })[0];
    cabecalho(DIAS[agora.getDay()], agora.getDate() + ' de ' +
      ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'][agora.getMonth()]);

    var hero;
    if (atual) {
      hero = '<small>A decorrer</small><strong>' + esc(atual.t) + '</strong><span class="big">termina às ' + atual.f + '</span>';
    } else if (prox) {
      hero = '<small>Próximo bloco</small><strong>' + esc(prox.t) + '</strong><span class="big">em ' + dur(min(prox.i) - nm) + '</span>';
    } else {
      hero = '<small>' + (bl.length ? 'Fim do dia' : 'Sem blocos hoje') + '</small><strong>' +
        (bl.length ? 'Todos os blocos terminaram.' : 'Dia livre. Toca em + para adicionar.') + '</strong>';
    }
    var pct = bl.length ? Math.round(feitos / bl.length * 100) : 0;
    hero += '<div class="bar"><i style="width:' + pct + '%"></i></div><small>' + feitos + ' de ' + bl.length + ' concluídos</small>';

    var h = '<div class="hero">' + hero + '</div>';
    if (!bl.length) { h += '<div class="vazio">Nenhuma actividade neste dia.</div>'; }
    bl.forEach(function (a) {
      var ini = min(a.i), fim = min(a.f);
      h += linha(a, { agora: ini <= nm && nm < fim, fim: nm >= fim, marcar: nm >= ini, estado: lg[a.id] });
    });
    $('#view').innerHTML = h;
  }

  function vSemana() {
    cabecalho('Semana', 'Toca num bloco para editar.');
    var hj = new Date().getDay();
    var h = '<div class="dias">' + ORDEM.map(function (d) {
      return '<button data-d="' + d + '" class="' + (d === diaSel ? 'on ' : '') + (d === hj ? 'hoje' : '') + '">' + DIAS_C[d] + '</button>';
    }).join('') + '</div>';
    var bl = blocosDe(diaSel), total = 0;
    bl.forEach(function (a) { total += min(a.f) - min(a.i); });
    h += '<p style="margin:0 0 12px;color:var(--ink2);font-size:.9rem">' + DIAS[diaSel] + ' · ' + bl.length +
      ' blocos · ' + dur(total) + ' planeados</p>';
    if (!bl.length) h += '<div class="vazio">Dia sem actividades.</div>';
    bl.forEach(function (a) { h += linha(a, {}); });
    $('#view').innerHTML = h;
  }

  function acc(o, k, ok) {
    var r = o[k] || (o[k] = { c: 0, f: 0 });
    r.c++;
    if (ok) r.f++;
  }

  function vStats() {
    cabecalho('Estatísticas', 'Últimos 7 dias — blocos marcados como feitos.');
    var hj = new Date(), nm = hj.getHours() * 60 + hj.getMinutes();
    var dias = [], tipo = {}, disc = {}, somaC = 0, somaF = 0;
    for (var i = 6; i >= 0; i--) {
      var d = new Date(hj.getFullYear(), hj.getMonth(), hj.getDate() - i), k = chave(d);
      if (k < S.first) { dias.push({ d: d, p: null }); continue; }
      var lg = S.log[k] || {}, c = 0, f = 0;
      blocosDe(d.getDay()).forEach(function (a) {
        if (i === 0 && min(a.f) > nm) return;
        var ok = lg[a.id] === 'done';
        c++; if (ok) f++;
        acc(tipo, a.tipo, ok); acc(disc, a.t, ok);
      });
      somaC += c; somaF += f;
      dias.push({ d: d, p: c ? Math.round(f / c * 100) : null });
    }
    var media = somaC ? Math.round(somaF / somaC * 100) : null;
    var h = '<div class="painel"><h2>Cumprimento: ' + (media === null ? 'sem dados ainda' : media + '%') + '</h2><div class="colunas">' +
      dias.map(function (x) {
        return '<div class="col"><small>' + (x.p === null ? '' : x.p + '%') + '</small><i class="' + (x.p === null ? 'nd' : '') +
          '" style="height:' + (x.p === null ? 3 : Math.max(3, x.p)) + '%"></i><span>' + DIAS_C[x.d.getDay()] + '</span></div>';
      }).join('') + '</div></div>';

    function bloco(titulo, obj, cor) {
      var ks = Object.keys(obj);
      if (!ks.length) return '';
      ks.sort(function (a, b) { return obj[a].f / obj[a].c - obj[b].f / obj[b].c; });
      var s = '<div class="painel"><h2>' + titulo + '</h2>';
      ks.slice(0, 8).forEach(function (k) {
        var r = obj[k], p = Math.round(r.f / r.c * 100);
        s += '<div class="linha" style="--c:' + cor(k) + '"><div><span>' + esc(TIPOS[k] ? TIPOS[k].n : k) + '</span><b>' + p + '% · ' + r.f + '/' + r.c +
          '</b></div><div class="trilho"><i style="width:' + p + '%"></i></div></div>';
      });
      return s + '</div>';
    }
    h += bloco('Por tipo', tipo, function (k) { return TIPOS[k].c; });
    h += bloco('Por actividade (das mais fracas às mais fortes)', disc, function () { return '#2B59C3'; });
    if (somaC === 0) h += '<div class="vazio">Marca os blocos como Feito ou Falhei no ecrã Hoje para ver as estatísticas.</div>';
    $('#view').innerHTML = h;
  }

  async function vAjustes() {
    cabecalho('Ajustes', 'Alarmes, teste e cópia de segurança.');
    var p = await permissoes(false);
    function est(v) { return v === 'granted' ? '<span class="ok">Activo</span>' : '<span class="mau">' + (v || 'n/d') + '</span>'; }
    var h = '<div class="painel"><h2>Alarmes</h2>';
    if (p.web) {
      h += '<div class="item"><span>Modo</span><span>Navegador (sem alarmes)</span></div>';
    } else {
      h += '<div class="item"><span>Notificações</span>' + est(p.display) + '</div>' +
        '<div class="item"><span>Alarmes exactos</span>' + (p.exact ? est(p.exact) : '<span>n/d</span>') + '</div>';
    }
    h += '<button class="btn" id="b-perm">Pedir permissões</button>' +
      '<button class="btn sec" id="b-exato">Abrir definições de alarmes exactos</button>' +
      '<button class="btn sec" id="b-teste">Testar alarme (10 s)</button>' +
      '<button class="btn sec" id="b-reagendar">Reagendar todos os alarmes</button></div>';

    h += '<div class="painel"><h2>Para os alarmes tocarem sempre</h2><ol class="guia">' +
      '<li>Definições do telemóvel › Aplicações › Mogas Time Manager › Bateria: escolhe "Sem restrições".</li>' +
      '<li>Em Xiaomi, Tecno, Infinix, Oppo e Samsung, activa também "Início automático".</li>' +
      '<li>Mantém o volume de Notificações alto e o modo Não incomodar desligado.</li>' +
      '<li>Faz o teste de 10 s com o ecrã bloqueado e a app fechada.</li></ol></div>';

    h += '<div class="painel"><h2>Cópia de segurança</h2>' +
      '<textarea id="bk" spellcheck="false"></textarea>' +
      '<button class="btn sec" id="b-exp">Gerar cópia</button>' +
      '<button class="btn sec" id="b-imp">Restaurar a partir do texto</button>' +
      '<button class="btn perigo" id="b-rep">Repor horário LEE1TA original</button></div>';
    $('#view').innerHTML = h;
  }

  function desenhar() {
    $('#fab').style.display = (vista === 'ajustes' || vista === 'stats') ? 'none' : '';
    Array.prototype.forEach.call(document.querySelectorAll('#tabs button'), function (b) {
      b.classList.toggle('on', b.dataset.v === vista);
    });
    if (vista === 'hoje') vHoje();
    else if (vista === 'semana') vSemana();
    else if (vista === 'stats') vStats();
    else vAjustes();
  }

  /* ---------- editor ---------- */
  function abrirEditor(id) {
    var a = id ? S.acts.filter(function (x) { return x.id === id; })[0] : null;
    var n = a || { t: '', tipo: 'estudo', dias: [vista === 'semana' ? diaSel : new Date().getDay()], i: '08:00', f: '09:00', alarme: true, antes: 0 };
    var m = $('#modal');
    m.hidden = false;
    m.innerHTML = '<div class="folha"><h2>' + (a ? 'Editar actividade' : 'Nova actividade') + '</h2>' +
      '<label>Nome</label><input type="text" id="e-t" maxlength="80" value="' + esc(n.t) + '">' +
      '<label>Tipo</label><select id="e-tipo">' + Object.keys(TIPOS).map(function (k) {
        return '<option value="' + k + '"' + (k === n.tipo ? ' selected' : '') + '>' + TIPOS[k].n + '</option>';
      }).join('') + '</select>' +
      '<label>Dias da semana (repete todas as semanas)</label><div class="chips" id="e-dias">' + ORDEM.map(function (d) {
        return '<button type="button" data-d="' + d + '" class="' + (n.dias.indexOf(d) >= 0 ? 'on' : '') + '">' + DIAS_C[d].charAt(0) + '</button>';
      }).join('') + '</div>' +
      '<div class="duas"><div><label>Início</label><input type="time" id="e-i" value="' + n.i + '"></div>' +
      '<div><label>Fim</label><input type="time" id="e-f" value="' + n.f + '"></div></div>' +
      '<div class="sw"><span>Alarme</span><input type="checkbox" id="e-al"' + (n.alarme ? ' checked' : '') + '></div>' +
      '<label>Avisar antes</label><select id="e-antes">' + [0, 5, 10, 15, 30].map(function (v) {
        return '<option value="' + v + '"' + (v === (n.antes || 0) ? ' selected' : '') + '>' + (v ? v + ' min antes' : 'Na hora exacta') + '</option>';
      }).join('') + '</select>' +
      '<button class="btn" id="e-save">Guardar</button>' +
      (a ? '<button class="btn perigo" id="e-del">Apagar</button>' : '') +
      '<button class="btn sec" id="e-cancel">Cancelar</button></div>';

    m.onclick = async function (ev) {
      var t = ev.target;
      if (t === m || t.id === 'e-cancel') { fechar(); return; }
      if (t.closest('#e-dias') && t.dataset.d !== undefined) { t.classList.toggle('on'); return; }
      if (t.id === 'e-del') {
        if (!confirm('Apagar "' + a.t + '"?')) return;
        S.acts = S.acts.filter(function (x) { return x.id !== a.id; });
        await guardar(); await reagendar(); fechar(); desenhar(); aviso('Actividade apagada.');
        return;
      }
      if (t.id === 'e-save') {
        var nome = $('#e-t').value.trim();
        var ds = Array.prototype.map.call(document.querySelectorAll('#e-dias .on'), function (b) { return +b.dataset.d; });
        var i = $('#e-i').value, f = $('#e-f').value;
        if (!nome) { aviso('Escreve o nome da actividade.'); return; }
        if (!ds.length) { aviso('Escolhe pelo menos um dia.'); return; }
        if (!i || !f || min(f) <= min(i)) { aviso('A hora de fim deve ser depois do início.'); return; }
        var obj = a || { id: S.nextId++ };
        obj.t = nome; obj.tipo = $('#e-tipo').value; obj.dias = ds; obj.i = i; obj.f = f;
        obj.alarme = $('#e-al').checked; obj.antes = +$('#e-antes').value;
        if (!a) S.acts.push(obj);
        await guardar(); await reagendar(); fechar(); desenhar(); aviso('Guardado.');
      }
    };
  }
  function fechar() { var m = $('#modal'); m.hidden = true; m.innerHTML = ''; }

  /* ---------- eventos ---------- */
  document.addEventListener('click', async function (ev) {
    var t = ev.target;
    if (t.closest('#modal')) return;

    var tab = t.closest('#tabs button');
    if (tab) { vista = tab.dataset.v; desenhar(); return; }
    if (t.closest('#fab')) { abrirEditor(null); return; }

    var ed = t.closest('[data-edit]');
    if (ed && !t.closest('.marcas')) { abrirEditor(+ed.dataset.edit); return; }

    var dia = t.closest('.dias button');
    if (dia) { diaSel = +dia.dataset.d; desenhar(); return; }

    var mk = t.closest('[data-m]');
    if (mk) {
      var k = chave(new Date()), id = mk.dataset.id;
      S.log[k] = S.log[k] || {};
      S.log[k][id] = S.log[k][id] === mk.dataset.m ? undefined : mk.dataset.m;
      if (!S.log[k][id]) delete S.log[k][id];
      await guardar(); vHoje();
      return;
    }

    if (t.id === 'b-perm') { await criarCanal(); var p = await permissoes(true); aviso(p.web ? 'Só no telemóvel (APK).' : 'Notificações: ' + p.display); vAjustes(); }
    else if (t.id === 'b-exato') {
      if (!LN) { aviso('Só no telemóvel (APK).'); return; }
      try { await LN.changeExactNotificationSetting(); } catch (e) { aviso('Não disponível nesta versão do Android.'); }
    }
    else if (t.id === 'b-teste') { await testarAlarme(); }
    else if (t.id === 'b-reagendar') { var n = await reagendar(); aviso(n + ' alarmes agendados.'); }
    else if (t.id === 'b-exp') { $('#bk').value = JSON.stringify({ acts: S.acts, log: S.log, nextId: S.nextId, first: S.first }); aviso('Copia este texto e guarda-o num sítio seguro.'); }
    else if (t.id === 'b-imp') {
      try {
        var o = JSON.parse($('#bk').value);
        if (!Array.isArray(o.acts)) throw new Error('formato');
        S.acts = o.acts; S.log = o.log || {}; S.nextId = o.nextId || (o.acts.length + 1); S.first = o.first || S.first;
        await guardar(); await reagendar(); aviso('Cópia restaurada.');
      } catch (e) { aviso('Texto inválido. Cola a cópia completa.'); }
    }
    else if (t.id === 'b-rep') {
      if (!confirm('Repor o horário original? As tuas alterações serão perdidas.')) return;
      var h = horarioPadrao(); S.acts = h.acts; S.nextId = h.nextId;
      await guardar(); await reagendar(); aviso('Horário LEE1TA reposto.');
    }
  });

  /* ---------- arranque ---------- */
  async function iniciar() {
    S = await store.get('mtm_estado');
    var primeira = !S;
    if (primeira) {
      var h = horarioPadrao();
      S = { acts: h.acts, nextId: h.nextId, log: {}, first: chave(new Date()) };
      await guardar();
    }
    desenhar();
    await criarCanal();
    if (LN) {
      var p = await permissoes(true);
      if (primeira || p.display === 'granted') await reagendar();
      if (primeira) {
        if (p.display !== 'granted') aviso('Activa as notificações para os alarmes funcionarem.');
        else aviso('Horário carregado e alarmes agendados.');
      }
    }
    setInterval(function () { if (vista === 'hoje' && $('#modal').hidden) vHoje(); }, 30000);
  }
  iniciar();
})();
