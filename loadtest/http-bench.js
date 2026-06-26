#!/usr/bin/env node
'use strict';
/*
 * 零依赖 HTTP 压测脚本（闭环并发模型，类似 wrk / autocannon）。
 *
 * 用法:
 *   node http-bench.js <url> <connections> <durationSec> [label]
 * 例:
 *   node http-bench.js http://localhost:8080/login 100 20 single-instance
 *   node http-bench.js http://localhost/login      100 20 nginx-3-instances
 *
 * 输出: 总请求数、QPS、吞吐、延迟 min/avg/p50/p90/p95/p99/max、非 2xx 与错误数。
 *
 * 说明: 维持 <connections> 条并发链路，每条链路在前一个请求完成后立即发下一个
 * （closed-loop），在 <durationSec> 秒内尽可能多地打请求，用 keep-alive 复用连接。
 */
const http = require('http');
const https = require('https');
const { URL } = require('url');

const url = process.argv[2] || 'http://localhost:8080/login';
const connections = parseInt(process.argv[3] || '50', 10);
const durationSec = parseInt(process.argv[4] || '20', 10);
const label = process.argv[5] || '';

const target = new URL(url);
const client = target.protocol === 'https:' ? https : http;
const agent = new client.Agent({ keepAlive: true, maxSockets: connections, maxFreeSockets: connections });

const options = {
  protocol: target.protocol,
  hostname: target.hostname,
  port: target.port || (target.protocol === 'https:' ? 443 : 80),
  path: (target.pathname || '/') + (target.search || ''),
  method: 'GET',
  agent,
  headers: { Connection: 'keep-alive' },
};

const latencies = [];
let total = 0, ok = 0, non2xx = 0, errors = 0, bytes = 0;
let finishedChains = 0;
let reported = false;
let startAt = 0, endAt = 0;

function once() {
  const t0 = process.hrtime.bigint();
  const req = client.request(options, (res) => {
    res.on('data', (d) => { bytes += d.length; });
    res.on('end', () => {
      const ms = Number(process.hrtime.bigint() - t0) / 1e6;
      latencies.push(ms);
      total++;
      if (res.statusCode >= 200 && res.statusCode < 300) ok++; else non2xx++;
      cont();
    });
  });
  req.on('error', () => { total++; errors++; cont(); });
  req.end();
}

function cont() {
  if (Date.now() < endAt) once();
  else if (++finishedChains >= connections) report();
}

function pct(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

function fmt(n, d = 2) { return Number(n).toFixed(d); }

function report() {
  if (reported) return;
  reported = true;
  const elapsed = (Date.now() - startAt) / 1000;
  latencies.sort((a, b) => a - b);
  const sum = latencies.reduce((a, b) => a + b, 0);
  const avg = latencies.length ? sum / latencies.length : 0;
  const qps = total / elapsed;
  const mbps = (bytes / 1024 / 1024) / elapsed;

  console.log('========================================================');
  if (label) console.log('  Label       : ' + label);
  console.log('  Target      : ' + url);
  console.log('  Connections : ' + connections + '   Duration: ' + fmt(elapsed, 1) + 's');
  console.log('--------------------------------------------------------');
  console.log('  Requests    : ' + total + '  (2xx=' + ok + ', non2xx=' + non2xx + ', errors=' + errors + ')');
  console.log('  Throughput  : ' + fmt(qps, 1) + ' req/s   ' + fmt(mbps, 2) + ' MB/s');
  console.log('  Latency(ms) : min=' + fmt(latencies[0] || 0) +
              '  avg=' + fmt(avg) +
              '  p50=' + fmt(pct(latencies, 50)) +
              '  p90=' + fmt(pct(latencies, 90)) +
              '  p95=' + fmt(pct(latencies, 95)) +
              '  p99=' + fmt(pct(latencies, 99)) +
              '  max=' + fmt(latencies[latencies.length - 1] || 0));
  console.log('========================================================');

  // 机器可读单行（便于汇总对比）
  console.log('RESULT\t' + JSON.stringify({
    label, url, connections, durationSec: +fmt(elapsed, 1),
    requests: total, qps: +fmt(qps, 1),
    p50: +fmt(pct(latencies, 50)), p95: +fmt(pct(latencies, 95)), p99: +fmt(pct(latencies, 99)),
    non2xx, errors,
  }));
}

console.log('warming up 2s ...');
// 预热 2s（让 JIT / 连接池就位），再正式计时
const warmEnd = Date.now() + 2000;
(function warm() {
  const req = client.request(options, (res) => { res.on('data', () => {}); res.on('end', () => {
    if (Date.now() < warmEnd) warm(); else begin();
  }); });
  req.on('error', () => { if (Date.now() < warmEnd) warm(); else begin(); });
  req.end();
})();

function begin() {
  console.log('running ' + durationSec + 's @ ' + connections + ' connections ...');
  startAt = Date.now();
  endAt = startAt + durationSec * 1000;
  for (let i = 0; i < connections; i++) once();
}
