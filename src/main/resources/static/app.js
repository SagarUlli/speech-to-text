'use strict';
const $ = (id) => document.getElementById(id);
const API = '/api/transcriptions';

/* ---------- helpers ---------- */
function toast(msg, isErr) {
  const t = $('toast'); t.textContent = msg; t.style.background = isErr ? 'var(--bad)' : '';
  t.classList.add('show'); clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.remove('show'), 3200);
}
async function api(url, opts) {
  const r = await fetch(url, opts && { ...opts, headers: { 'Content-Type': 'application/json' } });
  if (!r.ok) {
    let m = r.status + ' ' + r.statusText;
    try { const j = await r.json(); m = j.error || j.message || m; if (j.fields) m += ': ' + Object.values(j.fields).join(', '); } catch (_) {}
    throw new Error(m);
  }
  return r.status === 204 ? null : r.json();
}
function detectLanguage(text) {
  const dev = /[ऀ-ॿ]/.test(text), lat = /[A-Za-z]{2,}/.test(text);
  return dev && lat ? 'Hindi + English' : dev ? 'Hindi' : lat ? 'English / Hinglish' : '';
}
const fmtTime = (s) => String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
const fmtDate = (iso) => new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
function defaultTitle() { return 'Session ' + new Date().toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }); }

/* ---------- live recording ---------- */
const live = { ws: null, ctx: null, stream: null, node: null, running: false, finalText: '', interim: '', start: 0, tick: null, engine: 'gemini' };

function setStatus(text, cls) { const s = $('status'); s.textContent = text; s.className = 'status ' + (cls || ''); }
function renderLive() {
  const box = $('live');
  box.textContent = '';
  if (!live.finalText && !live.interim) {
    const p = document.createElement('span'); p.className = 'placeholder';
    p.textContent = 'Press start and speak — Hindi, English or a mix of both.'; box.appendChild(p); return;
  }
  box.append(document.createTextNode(live.finalText));
  if (live.interim) { const i = document.createElement('span'); i.className = 'interim'; i.textContent = live.interim; box.appendChild(i); }
  box.scrollTop = box.scrollHeight;
  $('save').disabled = $('clear').disabled = !live.finalText.trim() || live.running;
}

async function loadEngines() {
  try {
    const { default: def, engines } = await api('/api/engines');
    const sel = $('engine'); sel.textContent = '';
    engines.forEach((e) => {
      const o = document.createElement('option'); o.value = e.id;
      o.textContent = e.label + (e.available ? '' : ' — not configured'); o.disabled = !e.available; sel.appendChild(o);
    });
    sel.value = def;
    if (!engines.some((e) => e.available)) setStatus('No speech engine configured on the server', 'err');
  } catch (e) { setStatus('Cannot reach server', 'err'); }
}

async function startRecording() {
  if (!navigator.mediaDevices || !window.AudioWorkletNode) { toast('This browser does not support live audio capture.', true); return; }
  try {
    live.stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } });
  } catch (e) { toast('Microphone permission denied.', true); return; }

  live.engine = $('engine').value;
  setStatus('Connecting…');
  const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/transcribe');
  ws.binaryType = 'arraybuffer'; live.ws = ws;
  ws.onopen = () => ws.send(JSON.stringify({ type: 'start', engine: live.engine }));
  ws.onmessage = (ev) => onServerEvent(JSON.parse(ev.data));
  ws.onerror = () => setStatus('Connection error', 'err');
  ws.onclose = () => {
    if (live.running) stopRecording(true);
    else if (live.ws === ws) { cleanupAudio(); live.ws = null; setStatus('Idle'); }
  };
}

async function beginAudio() {
  live.ctx = new AudioContext();
  await live.ctx.audioWorklet.addModule('pcm-worklet.js');
  const src = live.ctx.createMediaStreamSource(live.stream);
  live.node = new AudioWorkletNode(live.ctx, 'pcm-processor');
  live.node.port.onmessage = (e) => {
    if (live.ws && live.ws.readyState === WebSocket.OPEN) live.ws.send(e.data.pcm);
    $('meter').style.width = Math.min(100, e.data.level * 160) + '%';
  };
  src.connect(live.node); // no connection to destination => no echo
  live.running = true; live.start = Date.now();
  live.tick = setInterval(() => { $('timer').textContent = fmtTime(Math.floor((Date.now() - live.start) / 1000)); }, 500);
  const t = $('toggle'); t.textContent = '■ Stop'; t.classList.add('recording'); t.disabled = false;
  setStatus('Listening…', 'ok');
}

function onServerEvent(m) {
  switch (m.type) {
    case 'ready': beginAudio().catch((e) => { toast('Audio setup failed: ' + e.message, true); stopRecording(true); }); break;
    case 'interim': live.interim = m.text; renderLive(); break;
    case 'final': live.finalText += (live.finalText && !/\s$/.test(live.finalText) && !/^\s/.test(m.text) ? ' ' : '') + m.text; live.interim = ''; renderLive(); break;
    case 'error':
      setStatus(m.message, 'err'); toast(m.message, true);
      if (!live.running && live.ws) { const w = live.ws; cleanupAudio(); live.ws = null; w.onclose = null; w.close(); } // failed before audio started
      break;
    case 'closed': if (live.running) stopRecording(true); break;
  }
}

function cleanupAudio() {
  clearInterval(live.tick);
  if (live.node) { live.node.port.onmessage = null; live.node.disconnect(); live.node = null; }
  if (live.ctx) { live.ctx.close().catch(() => {}); live.ctx = null; }
  if (live.stream) { live.stream.getTracks().forEach((t) => t.stop()); live.stream = null; }
  $('meter').style.width = '0';
}

async function stopRecording(remote) {
  const wasRunning = live.running; live.running = false;
  const t = $('toggle'); t.textContent = '● Start speaking'; t.classList.remove('recording');
  if (live.ws && live.ws.readyState === WebSocket.OPEN && !remote) {
    live.ws.send(JSON.stringify({ type: 'stop' })); // flush pending words
    setStatus('Finishing…');
    await new Promise((r) => setTimeout(r, 1800)); // let the last transcript segment arrive
  }
  cleanupAudio();
  if (live.ws) { live.ws.onclose = null; try { live.ws.close(); } catch (_) {} live.ws = null; }
  if (live.interim) { live.finalText += live.interim; live.interim = ''; }
  renderLive();
  setStatus('Idle');
  if (wasRunning && live.finalText.trim()) await saveSession(true);
}

async function saveSession(auto) {
  const content = live.finalText.trim();
  if (!content) return;
  try {
    await api(API, {
      method: 'POST',
      body: JSON.stringify({
        title: $('title').value.trim() || defaultTitle(), content,
        language: detectLanguage(content), engine: live.engine,
        durationSeconds: live.start ? Math.round((Date.now() - live.start) / 1000) : null,
      }),
    });
    toast(auto ? 'Saved to history' : 'Saved');
    live.finalText = ''; live.start = 0; $('title').value = ''; $('timer').textContent = '00:00'; renderLive();
    page = 0; loadList();
  } catch (e) { toast('Save failed: ' + e.message, true); }
}

$('toggle').onclick = () => (live.running ? stopRecording() : (live.ws ? null : startRecording()));
$('save').onclick = () => saveSession(false);
$('clear').onclick = () => { live.finalText = ''; live.interim = ''; $('timer').textContent = '00:00'; renderLive(); };

/* ---------- history dashboard (CRUD) ---------- */
let page = 0, q = '', editingId = null;
const SIZE = 6;

async function loadList() {
  try {
    const r = await api(`${API}?page=${page}&size=${SIZE}` + (q ? '&q=' + encodeURIComponent(q) : ''));
    if (page > 0 && r.content.length === 0) { page = Math.max(0, r.totalPages - 1); return loadList(); }
    const list = $('list'); list.textContent = '';
    if (!r.content.length) { const e = document.createElement('div'); e.className = 'empty'; e.textContent = q ? 'No matches.' : 'No transcriptions yet — record one above or press “+ New”.'; list.appendChild(e); }
    r.content.forEach((t) => list.appendChild(renderItem(t)));
    $('pageinfo').textContent = r.totalElements ? `Page ${r.page + 1} of ${r.totalPages} · ${r.totalElements} total` : '';
    $('prev').disabled = r.page <= 0; $('next').disabled = r.page + 1 >= r.totalPages;
  } catch (e) { toast('Could not load history: ' + e.message, true); }
}

function el(tag, cls, text) { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; }

function renderItem(t) {
  const d = el('article', 'item');
  d.appendChild(el('h4', null, t.title));
  const p = el('p', null, t.content || '(empty)'); p.lang = 'hi'; d.appendChild(p);
  const meta = el('div', 'meta');
  meta.appendChild(el('span', null, fmtDate(t.createdAt)));
  if (t.language) meta.appendChild(el('span', 'tag', t.language));
  if (t.engine) meta.appendChild(el('span', 'tag', t.engine));
  if (t.durationSeconds != null) meta.appendChild(el('span', null, fmtTime(t.durationSeconds)));
  d.appendChild(meta);
  const a = el('div', 'actions');
  const btn = (label, fn, cls) => { const b = el('button', cls || 'ghost', label); b.onclick = fn; a.appendChild(b); };
  btn('Edit', () => openDialog(t));
  btn('Copy', () => navigator.clipboard.writeText(t.content).then(() => toast('Copied'), () => toast('Copy failed', true)));
  btn('Download', () => download(t));
  btn('Delete', () => removeItem(t), 'ghost danger');
  d.appendChild(a);
  return d;
}

function download(t) {
  const url = URL.createObjectURL(new Blob([t.content], { type: 'text/plain;charset=utf-8' }));
  const a = el('a'); a.href = url; a.download = t.title.replace(/[^\w\-]+/g, '_') + '.txt'; a.click(); URL.revokeObjectURL(url);
}

async function removeItem(t) {
  if (!confirm(`Delete “${t.title}”? This cannot be undone.`)) return;
  try { await api(`${API}/${t.id}`, { method: 'DELETE' }); toast('Deleted'); loadList(); }
  catch (e) { toast('Delete failed: ' + e.message, true); }
}

function openDialog(t) {
  editingId = t ? t.id : null;
  $('dlg-title').textContent = t ? 'Edit transcription' : 'New transcription';
  $('f-title').value = t ? t.title : ''; $('f-lang').value = t ? (t.language || '') : '';
  $('f-content').value = t ? t.content : '';
  $('f-meta').textContent = t ? `Created ${fmtDate(t.createdAt)} · updated ${fmtDate(t.updatedAt)}` : '';
  $('dlg').showModal(); $('f-title').focus();
}
$('new').onclick = () => openDialog(null);
$('f-cancel').onclick = () => $('dlg').close();
$('form').onsubmit = async (ev) => {
  ev.preventDefault();
  const body = { title: $('f-title').value.trim(), content: $('f-content').value, language: $('f-lang').value.trim() || detectLanguage($('f-content').value) || null, engine: editingId ? undefined : 'manual', durationSeconds: undefined };
  try {
    if (editingId) {
      const cur = await api(`${API}/${editingId}`);          // keep engine/duration from the stored record
      body.engine = cur.engine; body.durationSeconds = cur.durationSeconds;
      await api(`${API}/${editingId}`, { method: 'PUT', body: JSON.stringify(body) });
    } else { await api(API, { method: 'POST', body: JSON.stringify(body) }); page = 0; }
    $('dlg').close(); toast('Saved'); loadList();
  } catch (e) { toast('Save failed: ' + e.message, true); }
};

let st; $('search').oninput = (e) => { clearTimeout(st); st = setTimeout(() => { q = e.target.value.trim(); page = 0; loadList(); }, 250); };
$('prev').onclick = () => { page--; loadList(); };
$('next').onclick = () => { page++; loadList(); };

loadEngines(); renderLive(); loadList();
