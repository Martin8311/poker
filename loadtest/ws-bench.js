#!/usr/bin/env node
'use strict';
/*
 * WebSocket / STOMP 压测脚本。
 *
 * 自助注册一个临时用户 -> 表单登录拿到 Spring Session(SESSION) cookie ->
 * 用该 cookie 握手 /ws/websocket(SockJS 原生 WebSocket 传输) -> 发 STOMP CONNECT。
 *
 * 两种模式:
 *   1) conns   并发长连接承载量:同时建立 N 条 STOMP 连接并保持，统计成功/失败/掉线。
 *        node ws-bench.js conns <N> <holdSec> [host] [port]
 *        例: node ws-bench.js conns 1000 20
 *
 *   2) latency 广播往返延迟:在一个房间内订阅话题并连续发消息，测 发->收 往返延迟。
 *        node ws-bench.js latency <count> [host] [port]
 *        例: node ws-bench.js latency 500
 */
const http = require('http');
const WebSocket = require('ws');

const MODE = process.argv[2] || 'conns';
const ARG1 = parseInt(process.argv[3] || '500', 10);     // conns: N ; latency: 消息条数
const ARG2 = parseInt(process.argv[4] || '20', 10);      // conns: holdSec
const HOST = process.argv[5] || 'localhost';
const PORT = parseInt(process.argv[6] || '8080', 10);
const BASE = 'http://' + HOST + ':' + PORT;
const WS_URL = 'ws://' + HOST + ':' + PORT + '/ws/websocket';
const NUL = '\x00';

// ---------------- HTTP 工具(内置 http，无依赖) ----------------
function httpReq(method, path, opts) {
  opts = opts || {};
  return new Promise((resolve, reject) => {
    const headers = Object.assign({}, opts.headers);
    if (opts.cookie) headers['Cookie'] = opts.cookie;
    if (opts.body != null) {
      headers['Content-Type'] = headers['Content-Type'] || 'application/x-www-form-urlencoded';
      headers['Content-Length'] = Buffer.byteLength(opts.body);
    }
    const req = http.request({ host: HOST, port: PORT, method, path, headers }, (res) => {
      let data = '';
      res.on('data', (d) => (data += d));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: data, setCookie: res.headers['set-cookie'] || [] }));
    });
    req.on('error', reject);
    if (opts.body != null) req.write(opts.body);
    req.end();
  });
}
const enc = encodeURIComponent;
function sessionFrom(setCookie, fallback) {
  for (const c of setCookie || []) {
    const m = /^SESSION=([^;]+)/.exec(c);
    if (m) return 'SESSION=' + m[1];
  }
  return fallback;
}
function csrfFrom(html) {
  // 兼容 表单隐藏域 与 <meta name="_csrf" content="...">
  let m = /name="_csrf"[^>]*value="([^"]+)"/.exec(html)
       || /name="_csrf"[^>]*content="([^"]+)"/.exec(html)
       || /content="([^"]+)"[^>]*name="_csrf"/.exec(html);
  return m ? m[1] : null;
}

async function registerAndLogin() {
  const user = 'lt_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
  const pwd = 'Passw0rd!';
  // 1) 取一次 token+cookie 用于注册
  let r = await httpReq('GET', '/login');
  let cookie = sessionFrom(r.setCookie);
  let csrf = csrfFrom(r.body);
  await httpReq('POST', '/register', { cookie, body: `username=${enc(user)}&password=${enc(pwd)}&nickname=${enc(user)}&_csrf=${enc(csrf)}` });

  // 2) 重新取 token+cookie 后登录(登录成功后 session 会轮换)
  r = await httpReq('GET', '/login');
  cookie = sessionFrom(r.setCookie);
  csrf = csrfFrom(r.body);
  r = await httpReq('POST', '/login', { cookie, body: `username=${enc(user)}&password=${enc(pwd)}&_csrf=${enc(csrf)}` });
  const loc = r.headers['location'] || '';
  const authCookie = sessionFrom(r.setCookie, cookie);
  if (!(r.status === 302 && !/error/.test(loc))) {
    throw new Error('登录失败: status=' + r.status + ' location=' + loc);
  }
  return { user, authCookie };
}

function stomp(command, headers, body) {
  let f = command + '\n';
  for (const k in headers) f += k + ':' + headers[k] + '\n';
  f += '\n' + (body || '') + NUL;
  return f;
}
// 解析一段可能含多个 STOMP 帧的文本，回调每个帧的 {command, body}
function eachFrame(text, cb) {
  const parts = text.split(NUL);
  for (const p of parts) {
    if (!p || p === '\n') continue;
    const idx = p.indexOf('\n\n');
    const head = idx >= 0 ? p.slice(0, idx) : p;
    const body = idx >= 0 ? p.slice(idx + 2) : '';
    const command = head.split('\n')[0].trim();
    if (command) cb(command, body);
  }
}

function pct(sorted, p) {
  if (!sorted.length) return 0;
  const i = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, i)];
}
const f2 = (n) => Number(n).toFixed(2);

// ---------------- 模式 1: 并发长连接承载量 ----------------
async function runConns(authCookie, N, holdSec) {
  let connected = 0, failed = 0, dropped = 0;
  const sockets = [];
  const failReasons = {};

  function open(i) {
    return new Promise((resolve) => {
      let ok = false;
      const ws = new WebSocket(WS_URL, { headers: { Cookie: authCookie }, perMessageDeflate: false });
      const to = setTimeout(() => { if (!ok) { failed++; failReasons['timeout'] = (failReasons['timeout'] || 0) + 1; try { ws.terminate(); } catch (e) {} resolve(); } }, 15000);
      ws.on('open', () => ws.send(stomp('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0' })));
      ws.on('message', (buf) => {
        if (ok) return;
        eachFrame(buf.toString(), (cmd) => {
          if (cmd === 'CONNECTED' && !ok) {
            ok = true; clearTimeout(to); connected++; sockets.push(ws);
            ws.on('close', () => { if (ok) dropped++; });
            resolve();
          } else if (cmd === 'ERROR' && !ok) {
            ok = true; clearTimeout(to); failed++; failReasons['stomp-error'] = (failReasons['stomp-error'] || 0) + 1;
            try { ws.close(); } catch (e) {} resolve();
          }
        });
      });
      ws.on('error', (e) => {
        if (ok) return;
        clearTimeout(to); failed++;
        const key = (e.message || 'err').slice(0, 40);
        failReasons[key] = (failReasons[key] || 0) + 1;
        resolve();
      });
    });
  }

  process.stdout.write(`opening ${N} STOMP connections ...\n`);
  const t0 = Date.now();
  // 分批建立，避免瞬时握手风暴
  const batch = 50;
  for (let i = 0; i < N; i += batch) {
    const ps = [];
    for (let j = i; j < Math.min(i + batch, N); j++) ps.push(open(j));
    await Promise.all(ps);
    process.stdout.write(`  progress: connected=${connected} failed=${failed}\r`);
  }
  const openMs = Date.now() - t0;
  const liveAfterOpen = connected - dropped;
  process.stdout.write(`\nall handshakes done in ${openMs} ms. holding ${holdSec}s ...\n`);
  await new Promise((r) => setTimeout(r, holdSec * 1000));

  const stillOpen = sockets.filter((w) => w.readyState === WebSocket.OPEN).length;
  const mem = process.memoryUsage().rss / 1024 / 1024;

  console.log('========================================================');
  console.log('  WS conns test  ->  ' + WS_URL);
  console.log('--------------------------------------------------------');
  console.log('  Requested        : ' + N);
  console.log('  CONNECTED ok      : ' + connected);
  console.log('  Failed handshakes : ' + failed + (Object.keys(failReasons).length ? '  ' + JSON.stringify(failReasons) : ''));
  console.log('  Open time         : ' + openMs + ' ms');
  console.log('  Dropped during hold: ' + dropped);
  console.log('  Still OPEN after ' + holdSec + 's: ' + stillOpen);
  console.log('  Client RSS        : ' + f2(mem) + ' MB');
  console.log('========================================================');
  console.log('RESULT\t' + JSON.stringify({ mode: 'conns', requested: N, connected, failed, dropped, stillOpen, openMs }));
  sockets.forEach((w) => { try { w.terminate(); } catch (e) {} });
}

// ---------------- 模式 2: 房间内广播往返延迟 ----------------
async function runLatency(authCookie, count) {
  // 建一个房间(房主自动入座 -> 可订阅该房间话题)
  let r = await httpReq('GET', '/hall', { cookie: authCookie });
  const csrf = csrfFrom(r.body);
  r = await httpReq('POST', '/room/create', { cookie: authCookie, body: `roomDesc=loadtest&roomPwd=null&_csrf=${enc(csrf)}` });
  const loc = r.headers['location'] || '';
  const m = /\/room\/(ROOM_[^\/?\s]+)/.exec(loc);
  if (!m) throw new Error('创建房间失败: status=' + r.status + ' location=' + loc);
  const roomId = m[1];
  console.log('room created: ' + roomId);

  const topic = '/topic/rooms.' + roomId;
  const latencies = [];
  const pending = new Map(); // seq -> sendNs
  let seq = 0, recv = 0;

  await new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL, { headers: { Cookie: authCookie }, perMessageDeflate: false });
    let subscribed = false;

    function sendOne() {
      if (seq >= count) return;
      const s = seq++;
      pending.set(s, process.hrtime.bigint());
      ws.send(stomp('SEND', { destination: '/app/rooms/' + roomId + '/message', 'content-type': 'application/json' },
                    JSON.stringify({ content: 'LAT|' + s })));
    }

    ws.on('open', () => ws.send(stomp('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0' })));
    ws.on('error', reject);
    ws.on('message', (buf) => {
      eachFrame(buf.toString(), (cmd, body) => {
        if (cmd === 'CONNECTED') {
          ws.send(stomp('SUBSCRIBE', { id: 'sub-0', destination: topic }));
          subscribed = true;
          // 预热 1 条后开始
          setTimeout(sendOne, 100);
        } else if (cmd === 'MESSAGE') {
          const mm = /LAT\|(\d+)/.exec(body);
          if (mm) {
            const s = parseInt(mm[1], 10);
            const t = pending.get(s);
            if (t != null) {
              latencies.push(Number(process.hrtime.bigint() - t) / 1e6);
              pending.delete(s);
              recv++;
              if (recv >= count) { resolve(); return; }
              setImmediate(sendOne); // 串行发下一条(测往返延迟)
            }
          }
        } else if (cmd === 'ERROR') {
          reject(new Error('STOMP ERROR: ' + body.slice(0, 120)));
        }
      });
    });
    setTimeout(() => resolve(), 60000); // 兜底超时
  });

  latencies.sort((a, b) => a - b);
  const sum = latencies.reduce((a, b) => a + b, 0);
  console.log('========================================================');
  console.log('  WS broadcast latency  ->  ' + topic);
  console.log('--------------------------------------------------------');
  console.log('  Round trips     : ' + latencies.length + ' / ' + count);
  console.log('  Latency(ms)     : min=' + f2(latencies[0] || 0) +
              '  avg=' + f2(latencies.length ? sum / latencies.length : 0) +
              '  p50=' + f2(pct(latencies, 50)) +
              '  p95=' + f2(pct(latencies, 95)) +
              '  p99=' + f2(pct(latencies, 99)) +
              '  max=' + f2(latencies[latencies.length - 1] || 0));
  console.log('========================================================');
  console.log('RESULT\t' + JSON.stringify({ mode: 'latency', roundTrips: latencies.length,
    p50: +f2(pct(latencies, 50)), p95: +f2(pct(latencies, 95)), p99: +f2(pct(latencies, 99)) }));
}

(async () => {
  try {
    console.log('register + login ...');
    const { user, authCookie } = await registerAndLogin();
    console.log('logged in as ' + user);
    if (MODE === 'latency') await runLatency(authCookie, ARG1);
    else await runConns(authCookie, ARG1, ARG2);
  } catch (e) {
    console.error('FATAL: ' + (e && e.stack || e));
    process.exit(1);
  }
  process.exit(0);
})();
