/**
 * 每日签到 - 前端逻辑
 *
 * 流程：
 *  1) 页面加载 → GET /signin/today → 若未签则自动弹窗；更新顶部红点
 *  2) 点击大厅「签到/查看签到」按钮 → 主动加载月度视图
 *  3) 月份翻页 → 重新拉取 status
 *  4) 点击「立即签到」 → POST /signin/do → 刷新视图 + 顶部按钮文案
 */
(function () {
    'use strict';

    // 当前查看的年月（Date 对象）
    let viewYear, viewMonth;

    // ============================================================
    //  工具
    // ============================================================

    function getCsrfToken() {
        const m = document.querySelector('meta[name="_csrf"]');
        if (!m) {
            throw new Error('CSRF 令牌未找到');
        }
        return m.getAttribute('content');
    }

    function pad2(n) {
        return n < 10 ? '0' + n : '' + n;
    }

    function fmtMonth(y, m) {
        return y + ' 年 ' + m + ' 月';
    }

    function todayParts() {
        const d = new Date();
        return { y: d.getFullYear(), m: d.getMonth() + 1, day: d.getDate() };
    }

    function daysInMonth(y, m) {
        return new Date(y, m, 0).getDate();
    }

    function firstWeekday(y, m) {
        // getDay(): 0=Sun ... 6=Sat
        return new Date(y, m - 1, 1).getDay();
    }

    // ============================================================
    //  弹窗控制
    // ============================================================

    function showSigninModal() {
        const modal = document.getElementById('signinModal');
        if (!modal) return;
        const t = todayParts();
        viewYear = t.y;
        viewMonth = t.m;
        loadMonthStatus();
        modal.style.display = 'flex';
    }

    function closeSigninModal() {
        const modal = document.getElementById('signinModal');
        if (modal) modal.style.display = 'none';
    }

    // ============================================================
    //  数据加载
    // ============================================================

    function loadMonthStatus() {
        return fetch(`/signin/status?year=${viewYear}&month=${viewMonth}`, {
            method: 'GET',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() }
        })
            .then(r => {
                if (!r.ok) throw new Error('加载签到状态失败');
                return r.json();
            })
            .then(renderMonthStatus)
            .catch(err => {
                console.error(err);
                if (window.showToast) showToast('签到数据加载失败');
            });
    }

    function renderMonthStatus(data) {
        // 标题
        document.getElementById('signinMonthTitle').textContent = fmtMonth(data.year, data.month);

        // 统计条
        document.getElementById('signinMonthDays').textContent = data.monthDays;
        document.getElementById('signinConsecutive').textContent = data.consecutiveDays;
        document.getElementById('signinTotal').textContent = data.totalDays;
        document.getElementById('signinLongest').textContent = data.longestStreak;

        // 签到按钮
        const btn = document.getElementById('signinDoBtn');
        const todayKey = todayParts().y + '-' + pad2(todayParts().m) + '-' + pad2(todayParts().day);
        const viewingToday = data.year === todayParts().y && data.month === todayParts().m;
        if (data.signedToday) {
            btn.disabled = true;
            btn.textContent = '今日已签到';
        } else if (viewingToday) {
            btn.disabled = false;
            btn.textContent = '立即签到';
        } else {
            btn.disabled = true;
            btn.textContent = '切换到当月签到';
        }

        // 翻页按钮
        const t = todayParts();
        const prevBtn = document.getElementById('signinPrevMonth');
        const nextBtn = document.getElementById('signinNextMonth');
        // 允许向前翻 5 年（业务上够用），向后不超过当月
        prevBtn.disabled = (viewYear < t.y - 5);
        nextBtn.disabled = (viewYear > t.y) || (viewYear === t.y && viewMonth >= t.m);

        // 日历
        renderCalendar(data.days, data.year, data.month);
    }

    function renderCalendar(days, year, month) {
        const grid = document.getElementById('signinCalendar');
        grid.innerHTML = '';

        // 周表头
        const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
        weekdays.forEach(w => {
            const el = document.createElement('div');
            el.className = 'signin-weekday';
            el.textContent = w;
            grid.appendChild(el);
        });

        // 前面补空
        const firstBlank = firstWeekday(year, month);
        for (let i = 0; i < firstBlank; i++) {
            const blank = document.createElement('div');
            blank.className = 'signin-day empty';
            grid.appendChild(blank);
        }

        // 每天
        days.forEach(d => {
            const cell = document.createElement('div');
            cell.className = 'signin-day';
            if (d.signed) cell.classList.add('signed');
            if (d.today) cell.classList.add('today');
            if (d.future) cell.classList.add('future');
            cell.textContent = d.day;
            cell.title = d.date + (d.signed ? '（已签到）' : (d.future ? '' : '（未签到）'));
            grid.appendChild(cell);
        });
    }

    // ============================================================
    //  签到动作
    // ============================================================

    function doSignIn() {
        const btn = document.getElementById('signinDoBtn');
        if (btn.disabled) return;
        btn.disabled = true;
        const oldText = btn.textContent;
        btn.textContent = '签到中...';

        fetch('/signin/do', {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() }
        })
            .then(r => {
                if (!r.ok) throw new Error('签到失败');
                return r.json();
            })
            .then(data => {
                if (window.showToast) showToast(data.message || '签到成功');
                updateHeaderBtn(data.alreadySigned, data.consecutiveDays);
                // 刷新当月视图
                return loadMonthStatus();
            })
            .catch(err => {
                console.error(err);
                if (window.showToast) showToast('签到失败，请重试');
                btn.disabled = false;
                btn.textContent = oldText;
            });
    }

    // ============================================================
    //  顶部按钮 / 红点
    // ============================================================

    function updateHeaderBtn(signed, consecutive) {
        const btnText = document.getElementById('signinBtnText');
        const dot = document.getElementById('signinDot');
        if (signed) {
            btnText.textContent = '查看签到（连续 ' + consecutive + ' 天）';
            if (dot) dot.hidden = true;
        } else {
            btnText.textContent = '立即签到';
            if (dot) dot.hidden = false;
        }
    }

    function refreshHeader() {
        fetch('/signin/today', {
            method: 'GET',
            headers: { 'X-CSRF-TOKEN': getCsrfToken() }
        })
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (!data) return;
                if (data.signed) {
                    // 拉一次 summary 拿连续天数
                    fetch('/signin/summary', {
                        method: 'GET',
                        headers: { 'X-CSRF-TOKEN': getCsrfToken() }
                    })
                        .then(r => r.ok ? r.json() : null)
                        .then(s => updateHeaderBtn(true, (s && s.consecutiveDays) || 0))
                        .catch(() => updateHeaderBtn(true, 0));
                } else {
                    updateHeaderBtn(false, 0);
                    // 登录后未签 → 自动弹窗
                    showSigninModal();
                }
            })
            .catch(err => console.warn('签到头部刷新失败:', err));
    }

    // ============================================================
    //  绑定
    // ============================================================

    document.addEventListener('DOMContentLoaded', function () {
        // 入口按钮
        const entryBtn = document.getElementById('signinBtn');
        if (entryBtn) {
            entryBtn.addEventListener('click', showSigninModal);
        }

        // 关闭
        document.querySelectorAll('.signin-modal-close').forEach(el => {
            el.addEventListener('click', closeSigninModal);
        });

        // 点击外部关闭
        const modal = document.getElementById('signinModal');
        if (modal) {
            window.addEventListener('click', function (e) {
                if (e.target === modal) closeSigninModal();
            });
        }

        // 翻页
        const prevBtn = document.getElementById('signinPrevMonth');
        const nextBtn = document.getElementById('signinNextMonth');
        if (prevBtn) {
            prevBtn.addEventListener('click', function () {
                viewMonth--;
                if (viewMonth < 1) { viewMonth = 12; viewYear--; }
                loadMonthStatus();
            });
        }
        if (nextBtn) {
            nextBtn.addEventListener('click', function () {
                viewMonth++;
                if (viewMonth > 12) { viewMonth = 1; viewYear++; }
                loadMonthStatus();
            });
        }

        // 签到
        const doBtn = document.getElementById('signinDoBtn');
        if (doBtn) doBtn.addEventListener('click', doSignIn);

        // 登录后自动刷新（若今天已签，更新按钮文案；若未签则弹窗）
        refreshHeader();
    });

    // 暴露给 hall.js（如有需要在房间列表等场景手动调用）
    window.SignInUI = {
        show: showSigninModal,
        close: closeSigninModal,
        refresh: refreshHeader
    };
})();
