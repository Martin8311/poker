/**
 * 充值弹窗 - 两步式（Step1 选档 → Step2 订单确认 → 支付）
 *
 * 支付确认机制：STOMP 推 /user/{u}/queue/recharge（见 recharge-notify.js）
 * 兜底机制：5s 轮询 /recharge/order/{id}
 */
(function () {
    'use strict';

    let currentRole = 'PLAYER';
    let currentOrderId = null;
    let currentOrderExpireAt = null;
    let pollTimer = null;
    let countdownTimer = null;
    let selectedMethod = 'MOCK';

    // ============================================================
    //  工具
    // ============================================================

    function getCsrfToken() {
        const m = document.querySelector('meta[name="_csrf"]');
        if (!m) throw new Error('CSRF 令牌未找到');
        return m.getAttribute('content');
    }

    function post(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: body ? JSON.stringify(body) : undefined
        }).then(r => r.json().then(data => ({ ok: r.ok, status: r.status, data })));
    }

    function getJson(url) {
        return fetch(url, {
            method: 'GET',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() }
        }).then(r => r.json().then(data => ({ ok: r.ok, status: r.status, data })));
    }

    // ============================================================
    //  顶部按钮 / 文案
    // ============================================================

    function updateHeaderBtn(status) {
        status = normalizeStatus(status);
        const btn = document.getElementById('rechargeBtn');
        const text = document.getElementById('rechargeBtnText');
        if (!btn || !text) return;
        currentRole = status.role;

        if (status.role === 'ADMIN') {
            text.textContent = '管理员无需充值';
            btn.disabled = true;
            btn.classList.add('btn-disabled');
            return;
        }
        btn.disabled = false;
        btn.classList.remove('btn-disabled');
        if (status.role === 'PLAYER') {
            text.textContent = '开通 VIP';
        } else {
            text.textContent = '续费 ' + status.role + '（剩 ' + status.daysLeft + ' 天）';
        }
    }

    function refreshHeader() {
        getJson('/recharge/status')
            .then(({ ok, data }) => {
                if (ok) updateHeaderBtn(data);
            })
            .catch(err => console.warn('充值头部刷新失败:', err));
    }

    // ============================================================
    //  弹窗 + view 切换
    // ============================================================

    function showRechargeModal() {
        const modal = document.getElementById('rechargeModal');
        if (!modal) return;
        modal.style.display = 'flex';
        switchToStep1();
        Promise.all([
            getJson('/recharge/plans').then(r => r.data),
            getJson('/recharge/status').then(r => r.data)
        ]).then(([plans, status]) => {
            renderStatus(status);
            renderPlans(plans);
        }).catch(err => {
            console.error(err);
            if (window.showToast) showToast('充值数据加载失败');
        });
    }

    function closeRechargeModal() {
        const modal = document.getElementById('rechargeModal');
        if (modal) modal.style.display = 'none';
        stopPolling();
        stopCountdown();
    }

    function switchToStep1() {
        document.getElementById('rechargeStep1').style.display = '';
        document.getElementById('rechargeStep2').style.display = 'none';
        document.getElementById('rechargeModalTitle').textContent = '开通 VIP / SVIP';
    }

    function switchToStep2() {
        document.getElementById('rechargeStep1').style.display = 'none';
        document.getElementById('rechargeStep2').style.display = '';
        document.getElementById('rechargeModalTitle').textContent = '确认订单';
    }

    // ============================================================
    //  渲染
    // ============================================================

    function renderStatus(status) {
        status = normalizeStatus(status);
        const box = document.getElementById('rechargeCurrent');
        if (!box) return;
        if (status.role === 'PLAYER' || status.expired) {
            box.classList.add('expired');
            box.innerHTML = '当前：<strong>普通玩家</strong>（开通后立即生效）';
        } else {
            box.classList.remove('expired');
            const expire = status.expireAt
                ? status.expireAt.replace('T', ' ').substring(0, 16)
                : '暂无到期时间';
            box.innerHTML =
                '当前：<strong>' + esc(status.roleDisplay || status.role) + '</strong>' +
                '（剩 <strong>' + status.daysLeft + '</strong> 天，' + expire + ' 到期）' +
                '<br>累计开通 <strong>' + status.totalOrders + '</strong> 单 / 共 <strong>' +
                status.totalRechargedDays + '</strong> 天';
        }
    }

    function renderPlans(plans) {
        const list = document.getElementById('rechargePlanList');
        if (!list) return;
        list.innerHTML = '';
        const vip = plans.filter(p => p.role === 'VIP');
        const svip = plans.filter(p => p.role === 'SVIP');
        appendSection(list, '普通 VIP', '', vip);
        appendSection(list, '超级 SVIP', 'svip', svip);
    }

    function appendSection(parent, title, modifier, items) {
        if (!items || items.length === 0) return;
        const h = document.createElement('div');
        h.className = 'recharge-section-title ' + modifier;
        h.textContent = title;
        parent.appendChild(h);
        items.forEach(p => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'recharge-card' + (p.role === 'SVIP' ? ' svip' : '') + (p.recommended ? ' recommended' : '');
            btn.dataset.planId = p.planId;
            btn.innerHTML =
                '<span class="recharge-card-info">' +
                  '<span class="recharge-card-label">' + esc(p.label) +
                    (p.recommended ? '<span class="recharge-card-tag">推荐</span>' : '') +
                  '</span>' +
                  '<span class="recharge-card-sub">' + p.days + ' 天 · 立即生效</span>' +
                '</span>' +
                '<span class="recharge-card-price">' + esc(p.priceDisplay) + '</span>';
            btn.addEventListener('click', () => createOrder(p.planId, btn));
            parent.appendChild(btn);
        });
    }

    // ============================================================
    //  Step 1: 创建订单
    // ============================================================

    function createOrder(planId, btn) {
        const allCards = document.querySelectorAll('.recharge-card');
        allCards.forEach(c => c.disabled = true);
        const oldText = btn.innerHTML;
        btn.innerHTML = '<span class="recharge-card-info"><span class="recharge-card-label">下单中...</span></span>';

        post('/recharge/create-order', { planId: planId })
            .then(({ ok, status, data }) => {
                if (!ok) {
                    if (window.showToast) showToast(data.message || ('下单失败 (' + status + ')'));
                    return;
                }
                currentOrderId = data.orderId;
                currentOrderExpireAt = new Date(data.expiredAt);
                renderOrderDetail(data);
                switchToStep2();
                startCountdown();
            })
            .catch(err => {
                console.error(err);
                if (window.showToast) showToast('网络错误，请重试');
            })
            .finally(() => {
                allCards.forEach(c => c.disabled = false);
                btn.innerHTML = oldText;
            });
    }

    function renderOrderDetail(order) {
        document.getElementById('rechargeOrderPlan').textContent = order.planLabel + '（' + order.days + ' 天）';
        document.getElementById('rechargeOrderAmount').textContent = order.priceDisplay;
        document.getElementById('rechargeOrderNo').textContent = order.orderNo;
        const expireText = (order.expiredAt || '').replace('T', ' ').substring(0, 19);
        document.getElementById('rechargeOrderExpire').textContent = expireText;
        // 重置支付方式选择 + 按钮
        selectedMethod = 'MOCK';
        document.querySelectorAll('.recharge-pay-method').forEach(el => {
            el.classList.toggle('selected', el.dataset.method === 'MOCK');
        });
        document.getElementById('rechargePayStatus').style.display = 'none';
        const confirmBtn = document.getElementById('rechargeConfirmPayBtn');
        confirmBtn.disabled = false;
        confirmBtn.textContent = '确认支付';
    }

    // ============================================================
    //  Step 2: 支付方式选择
    // ============================================================

    document.addEventListener('click', function (e) {
        const el = e.target.closest('.recharge-pay-method');
        if (!el || el.classList.contains('disabled')) return;
        selectedMethod = el.dataset.method;
        document.querySelectorAll('.recharge-pay-method').forEach(x => {
            x.classList.toggle('selected', x.dataset.method === selectedMethod);
        });
    });

    // ============================================================
    //  Step 2: 确认支付
    // ============================================================

    function confirmPay() {
        const btn = document.getElementById('rechargeConfirmPayBtn');
        if (btn.disabled) return;
        btn.disabled = true;
        btn.textContent = '处理中...';

        if (selectedMethod === 'MOCK') {
            post('/recharge/mock-pay/' + currentOrderId)
                .then(({ ok, status, data }) => {
                    if (!ok) {
                        if (window.showToast) showToast(data.message || ('支付失败 (' + status + ')'));
                        btn.disabled = false;
                        btn.textContent = '确认支付';
                        return;
                    }
                    showPayStatus('处理中', '');
                    // 启动兜底轮询（5s 间隔）。STOMP 推送到了会自动关掉
                    startPolling();
                })
                .catch(err => {
                    console.error(err);
                    if (window.showToast) showToast('网络错误，请重试');
                    btn.disabled = false;
                    btn.textContent = '确认支付';
                });
        } else {
            // ALIPAY 等真支付：未启用
            if (window.showToast) showToast('该支付方式未启用');
            btn.disabled = false;
            btn.textContent = '确认支付';
        }
    }

    function showPayStatus(text, countdownText) {
        const box = document.getElementById('rechargePayStatus');
        const textEl = document.getElementById('rechargePayStatusText');
        const cdEl = document.getElementById('rechargeCountdown');
        textEl.textContent = text;
        cdEl.textContent = countdownText;
        box.style.display = 'flex';
        box.className = 'recharge-pay-status';
    }

    // ============================================================
    //  兜底轮询
    // ============================================================

    function startPolling() {
        stopPolling();
        pollTimer = setInterval(() => {
            if (!currentOrderId) return;
            getJson('/recharge/order/' + currentOrderId)
                .then(({ ok, data }) => {
                    if (!ok) return;
                    if (data.status === 'PAID') {
                        stopPolling();
                        showPayStatus('支付成功！', '');
                        box.classList.add('success');
                        if (window.showToast) showToast('开通成功！');
                        setTimeout(() => {
                            closeRechargeModal();
                            refreshHeader();
                        }, 800);
                    } else if (data.status === 'FAILED' || data.status === 'EXPIRED' || data.status === 'CANCELLED') {
                        stopPolling();
                        showPayStatus('订单 ' + data.status, '');
                        document.getElementById('rechargePayStatus').classList.add('error');
                        if (window.showToast) showToast('支付未完成');
                        const btn = document.getElementById('rechargeConfirmPayBtn');
                        btn.disabled = false;
                        btn.textContent = '确认支付';
                    }
                })
                .catch(err => console.warn('轮询失败:', err));
        }, 2000);
    }

    function stopPolling() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }

    // ============================================================
    //  倒计时
    // ============================================================

    function startCountdown() {
        stopCountdown();
        countdownTimer = setInterval(() => {
            if (!currentOrderExpireAt) return;
            const diff = currentOrderExpireAt.getTime() - Date.now();
            if (diff <= 0) {
                stopCountdown();
                showPayStatus('订单已过期', '');
                document.getElementById('rechargePayStatus').classList.add('error');
                document.getElementById('rechargeCountdown').textContent = '00:00';
                document.getElementById('rechargeConfirmPayBtn').disabled = true;
                return;
            }
            const m = Math.floor(diff / 60000);
            const s = Math.floor((diff % 60000) / 1000);
            const txt = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
            const cdEl = document.getElementById('rechargeCountdown');
            if (cdEl) cdEl.textContent = txt;
        }, 1000);
    }

    function stopCountdown() {
        if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
    }

    // ============================================================
    //  STOMP 推送接收（来自 /user/queue/recharge）
    // ============================================================

    function onStompMessage(msg) {
        try {
            const data = JSON.parse(msg.body);
            console.log('充值 STOMP 推送:', data);
            if (data.status === 'PAID') {
                stopPolling();
                showPayStatus(data.message || '开通成功！', '');
                document.getElementById('rechargePayStatus').classList.add('success');
                if (window.showToast) showToast(data.message || '开通成功');
                setTimeout(() => {
                    closeRechargeModal();
                    refreshHeader();
                }, 800);
            } else if (data.status === 'FAILED' || data.status === 'EXPIRED') {
                stopPolling();
                showPayStatus(data.message || ('支付' + (data.status === 'EXPIRED' ? '已过期' : '失败')), '');
                document.getElementById('rechargePayStatus').classList.add('error');
                if (window.showToast) showToast(data.message);
                const btn = document.getElementById('rechargeConfirmPayBtn');
                btn.disabled = false;
                btn.textContent = '确认支付';
            } else if (data.status === 'CANCELLED') {
                stopPolling();
                closeRechargeModal();
                if (window.showToast) showToast('订单已取消');
            }
        } catch (e) {
            console.warn('解析 STOMP 消息失败:', e);
        }
    }

    // 暴露给 friends.js（同一 STOMP 连接上订阅）
    window.RechargeNotifyHandler = onStompMessage;

    // ============================================================
    //  绑定
    // ============================================================

    document.addEventListener('DOMContentLoaded', function () {
        const entryBtn = document.getElementById('rechargeBtn');
        if (entryBtn) {
            entryBtn.addEventListener('click', function () {
                if (entryBtn.disabled) return;
                showRechargeModal();
            });
        }
        document.querySelectorAll('.recharge-modal-close').forEach(el => {
            el.addEventListener('click', closeRechargeModal);
        });
        const modal = document.getElementById('rechargeModal');
        if (modal) {
            window.addEventListener('click', function (e) {
                if (e.target === modal) closeRechargeModal();
            });
        }
        const backBtn = document.getElementById('rechargeBackBtn');
        if (backBtn) backBtn.addEventListener('click', switchToStep1);
        const confirmBtn = document.getElementById('rechargeConfirmPayBtn');
        if (confirmBtn) confirmBtn.addEventListener('click', confirmPay);

        refreshHeader();
    });

    // ============================================================
    //  工具
    // ============================================================

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, c =>
            ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    function normalizeStatus(raw) {
        const s = raw || {};
        const role = s.role || s.effectiveRole || 'PLAYER';
        const daysLeft = firstNumber(
            s.daysLeft,
            s.days_left,
            s.leftDays,
            s.left_days,
            s.remainingDays,
            s.remaining_days
        );
        const totalOrders = firstNumber(
            s.totalOrders,
            s.total_orders,
            s.orderCount,
            s.order_count,
            s.orderTotal,
            s.order_total
        );
        const totalRechargedDays = firstNumber(
            s.totalRechargedDays,
            s.total_recharged_days,
            s.totalDays,
            s.total_days,
            s.totalRechargeDays,
            s.total_recharge_days
        );
        return {
            role: role,
            roleDisplay: s.roleDisplay || s.role_display || s.displayName || role,
            expireAt: s.expireAt || s.expire_at || s.vipExpireAt || s.vip_expire_at || '',
            daysLeft: daysLeft,
            expired: Boolean(s.expired),
            totalOrders: totalOrders,
            totalRechargedDays: totalRechargedDays
        };
    }

    function firstNumber() {
        for (let i = 0; i < arguments.length; i++) {
            const n = Number(arguments[i]);
            if (Number.isFinite(n)) return n;
        }
        return 0;
    }

    window.RechargeUI = {
        show: showRechargeModal,
        close: closeRechargeModal,
        refresh: refreshHeader
    };
})();
