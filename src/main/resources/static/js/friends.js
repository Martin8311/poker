/**
 * 好友功能前端核心（大厅 / 房间共享）。
 *
 * 职责：构建好友弹窗、调用 /friends/** 接口、处理实时通知、维护未读角标。
 * 不负责建立 STOMP 连接：
 *   - 大厅：调用 FriendsUI.connectStandalone() 自建一条连接收通知；
 *   - 房间：复用页面已有的 stompClient，订阅 /user/queue/friend 后转交 FriendsUI.onNotification。
 *
 * 依赖：页面里有 <meta name="_csrf">；加好友按钮 id="friendsBtn"（可选，自动绑定）。
 */
(function () {
    function csrf() {
        const m = document.querySelector('meta[name="_csrf"]');
        return m ? m.getAttribute('content') : '';
    }

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    const DEFAULT_AVATAR = '/icon/default-avatar.jpg';

    // 当前登录用户名（hall/room 模板里定义的全局 username）
    function me() {
        try { if (typeof username !== 'undefined' && username) return username; } catch (e) {}
        try { if (typeof currentUsername !== 'undefined' && currentUsername) return currentUsername; } catch (e) {}
        return null;
    }

    const nickCache = {};   // username -> nickname（私信通知里显示昵称）
    let dmByFriend = {};    // username -> 未读私信数

    // ---------------- 弹窗构建 ----------------
    function buildModal() {
        if (document.getElementById('friendsModal')) return;
        const el = document.createElement('div');
        el.id = 'friendsModal';
        el.className = 'friends-modal';
        el.style.display = 'none';
        el.innerHTML = `
            <div class="friends-overlay"></div>
            <div class="friends-panel">
                <div class="friends-header">
                    <span><i class="fas fa-user-friends"></i> 好友</span>
                    <button class="friends-close" type="button">&times;</button>
                </div>
                <div class="friends-tabs">
                    <button class="friends-tab active" data-tab="search">搜索</button>
                    <button class="friends-tab" data-tab="list">我的好友</button>
                    <button class="friends-tab" data-tab="requests">收到的申请
                        <span class="friends-badge" id="friendsReqBadge" style="display:none">0</span>
                    </button>
                </div>
                <div class="friends-body">
                    <div class="friends-pane" data-pane="search">
                        <div class="friends-search-bar">
                            <input id="friendSearchInput" type="text" placeholder="输入昵称搜索好友">
                            <button id="friendSearchBtn" type="button">搜索</button>
                        </div>
                        <div class="friends-list" id="friendSearchResults">
                            <div class="friends-empty">输入昵称后点击搜索</div>
                        </div>
                    </div>
                    <div class="friends-pane" data-pane="list" style="display:none">
                        <div class="friends-list" id="friendListResults"></div>
                    </div>
                    <div class="friends-pane" data-pane="requests" style="display:none">
                        <div class="friends-list" id="friendRequestResults"></div>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(el);

        el.querySelector('.friends-close').addEventListener('click', close);
        el.querySelector('.friends-overlay').addEventListener('click', close);
        el.querySelectorAll('.friends-tab').forEach(tab => {
            tab.addEventListener('click', () => switchTab(tab.getAttribute('data-tab')));
        });
        el.querySelector('#friendSearchBtn').addEventListener('click', doSearch);
        el.querySelector('#friendSearchInput').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') doSearch();
        });
    }

    function switchTab(name) {
        document.querySelectorAll('#friendsModal .friends-tab').forEach(t =>
            t.classList.toggle('active', t.getAttribute('data-tab') === name));
        document.querySelectorAll('#friendsModal .friends-pane').forEach(p =>
            p.style.display = p.getAttribute('data-pane') === name ? '' : 'none');
        if (name === 'list') loadFriends();
        if (name === 'requests') loadRequests();
    }

    function open() {
        buildModal();
        document.getElementById('friendsModal').style.display = 'flex';
        switchTab('search');
        refreshBadge();
    }

    function close() {
        const m = document.getElementById('friendsModal');
        if (m) m.style.display = 'none';
    }

    // ---------------- 行模板 ----------------
    function row(iconUrl, nickname, sub, actionsHtml, online) {
        const dot = (online === true || online === false)
            ? `<span class="friend-dot ${online ? 'online' : 'offline'}"></span>` : '';
        return `<div class="friend-row">
            <div class="friend-avatar-wrap">
                <img class="friend-avatar" src="${esc(iconUrl || DEFAULT_AVATAR)}"
                     onerror="this.src='${DEFAULT_AVATAR}'" alt="">
                ${dot}
            </div>
            <div class="friend-meta">
                <div class="friend-nick">${esc(nickname)}</div>
                ${sub ? `<div class="friend-sub">${esc(sub)}</div>` : ''}
            </div>
            <div class="friend-actions">${actionsHtml}</div>
        </div>`;
    }

    function relationButton(item) {
        switch (item.relation) {
            case 'FRIEND':
                return `<span class="friend-tag">已是好友</span>`;
            case 'REQUEST_SENT':
                return `<span class="friend-tag">已申请</span>`;
            case 'REQUEST_RECEIVED':
                return `<button class="friend-btn ok" data-accept="${item.requestId}">同意</button>`;
            default:
                return `<button class="friend-btn add" data-add="${esc(item.username)}">加好友</button>`;
        }
    }

    // ---------------- 搜索 ----------------
    function doSearch() {
        const q = document.getElementById('friendSearchInput').value.trim();
        const box = document.getElementById('friendSearchResults');
        if (!q) { box.innerHTML = `<div class="friends-empty">请输入昵称</div>`; return; }
        box.innerHTML = `<div class="friends-empty">搜索中...</div>`;
        fetch('/friends/search?nickname=' + encodeURIComponent(q))
            .then(r => r.json())
            .then(list => {
                if (!list.length) { box.innerHTML = `<div class="friends-empty">没有找到匹配的玩家</div>`; return; }
                box.innerHTML = list.map(it => row(it.iconUrl, it.nickname, it.username, relationButton(it))).join('');
                bindRowActions(box);
            })
            .catch(() => box.innerHTML = `<div class="friends-empty">搜索失败，请重试</div>`);
    }

    function bindRowActions(scope) {
        scope.querySelectorAll('[data-add]').forEach(b =>
            b.addEventListener('click', () => sendRequest(b.getAttribute('data-add'), b)));
        scope.querySelectorAll('[data-accept]').forEach(b =>
            b.addEventListener('click', () => accept(b.getAttribute('data-accept'), b)));
        scope.querySelectorAll('[data-reject]').forEach(b =>
            b.addEventListener('click', () => reject(b.getAttribute('data-reject'), b)));
        scope.querySelectorAll('[data-remove]').forEach(b =>
            b.addEventListener('click', () => removeFriend(b.getAttribute('data-remove'), b)));
        scope.querySelectorAll('[data-dm]').forEach(b =>
            b.addEventListener('click', () => openChat(b.getAttribute('data-dm'), b.getAttribute('data-nick'), b.getAttribute('data-icon'))));
    }

    // ---------------- 好友列表 ----------------
    function loadFriends() {
        const box = document.getElementById('friendListResults');
        box.innerHTML = `<div class="friends-empty">加载中...</div>`;
        Promise.all([
            fetch('/friends').then(r => r.json()),
            fetch('/messages/unread').then(r => r.json()).catch(() => ({ byFriend: {} }))
        ]).then(([list, unread]) => {
            dmByFriend = (unread && unread.byFriend) || {};
            if (!list.length) { box.innerHTML = `<div class="friends-empty">还没有好友，去搜索添加吧</div>`; return; }
            box.innerHTML = list.map(f => {
                nickCache[f.username] = f.nickname;
                const n = dmByFriend[f.username] || 0;
                const dmBtn = `<button class="friend-btn dm" data-dm="${esc(f.username)}" data-nick="${esc(f.nickname)}" data-icon="${esc(f.iconUrl || '')}">私信${n > 0 ? ` <span class="dm-row-badge">${n}</span>` : ''}</button>`;
                const delBtn = `<button class="friend-btn del" data-remove="${esc(f.username)}">删除</button>`;
                return row(f.iconUrl, f.nickname, f.online ? '在线' : '离线', dmBtn + delBtn, f.online);
            }).join('');
            bindRowActions(box);
        }).catch(() => box.innerHTML = `<div class="friends-empty">加载失败</div>`);
    }

    // ---------------- 收到的申请 ----------------
    function loadRequests() {
        const box = document.getElementById('friendRequestResults');
        box.innerHTML = `<div class="friends-empty">加载中...</div>`;
        fetch('/friends/requests')
            .then(r => r.json())
            .then(list => {
                setReqTabBadge(list.length);
                if (!list.length) { box.innerHTML = `<div class="friends-empty">暂无好友申请</div>`; return; }
                box.innerHTML = list.map(req => row(req.requesterIcon, req.requesterNickname, req.createTime,
                    `<button class="friend-btn ok" data-accept="${req.id}">同意</button>
                     <button class="friend-btn del" data-reject="${req.id}">拒绝</button>`)).join('');
                bindRowActions(box);
            })
            .catch(() => box.innerHTML = `<div class="friends-empty">加载失败</div>`);
    }

    // ---------------- 动作 ----------------
    function post(url, btn, okMsg) {
        if (btn) btn.disabled = true;
        return fetch(url, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf() } })
            .then(async r => {
                if (r.ok) { if (okMsg) toast(okMsg); return true; }
                const data = await r.json().catch(() => ({}));
                toast(data.error || '操作失败');
                if (btn) btn.disabled = false;
                return false;
            })
            .catch(() => { toast('网络错误'); if (btn) btn.disabled = false; return false; });
    }

    function sendRequest(username, btn) {
        return post('/friends/request?username=' + encodeURIComponent(username), btn, '好友申请已发送')
            .then(ok => { if (ok && btn) { btn.outerHTML = `<span class="friend-tag">已申请</span>`; } return ok; });
    }

    function accept(id, btn) {
        return post('/friends/request/' + id + '/accept', btn, '已添加为好友')
            .then(ok => { if (ok) { loadRequests(); refreshBadge(); } });
    }

    function reject(id, btn) {
        return post('/friends/request/' + id + '/reject', btn, '已拒绝')
            .then(ok => { if (ok) { loadRequests(); refreshBadge(); } });
    }

    function removeFriend(username, btn) {
        return post('/friends/remove?username=' + encodeURIComponent(username), btn, '已删除好友')
            .then(ok => { if (ok) loadFriends(); });
    }

    // ---------------- 角标 / 通知 ----------------
    function setReqTabBadge(count) {
        const tabBadge = document.getElementById('friendsReqBadge');
        if (tabBadge) {
            tabBadge.textContent = count;
            tabBadge.style.display = count > 0 ? '' : 'none';
        }
    }

    function setBtnBadge(count) {
        const btn = document.getElementById('friendsBtn');
        if (!btn) return;
        let b = btn.querySelector('.friends-btn-badge');
        if (!b) {
            b = document.createElement('span');
            b.className = 'friends-btn-badge';
            btn.appendChild(b);
        }
        b.textContent = count;
        b.style.display = count > 0 ? '' : 'none';
    }

    // 角标 = 待处理好友申请数 + 未读私信总数
    function refreshBadge() {
        Promise.all([
            fetch('/friends/requests').then(r => r.json()).catch(() => []),
            fetch('/messages/unread').then(r => r.json()).catch(() => ({ total: 0, byFriend: {} }))
        ]).then(([reqs, unread]) => {
            const reqCount = (reqs && reqs.length) || 0;
            dmByFriend = (unread && unread.byFriend) || {};
            const dmTotal = (unread && unread.total) || 0;
            setReqTabBadge(reqCount);
            setBtnBadge(reqCount + dmTotal);
        });
    }

    function onNotification(payload) {
        if (!payload) return;
        if (payload.type === 'NEW_REQUEST') {
            toast((payload.message || '收到新的好友申请'), payload.fromIcon);
            refreshBadge();
            const m = document.getElementById('friendsModal');
            if (m && m.style.display !== 'none') loadRequests();
        } else if (payload.type === 'ACCEPTED') {
            toast((payload.message || '对方通过了你的好友申请'), payload.fromIcon);
            const m = document.getElementById('friendsModal');
            if (m && m.style.display !== 'none') loadFriends();
        }
    }

    // ---------------- 私信聊天窗 ----------------
    let dmFriend = null;   // 当前正在聊天的好友 username

    function buildChat() {
        if (document.getElementById('dmChat')) return;
        const el = document.createElement('div');
        el.id = 'dmChat';
        el.className = 'dm-chat';
        el.style.display = 'none';
        el.innerHTML = `
            <div class="dm-head">
                <img class="dm-head-avatar" id="dmHeadAvatar" src="${DEFAULT_AVATAR}" onerror="this.src='${DEFAULT_AVATAR}'" alt="">
                <span class="dm-head-name" id="dmHeadName"></span>
                <button class="dm-close" type="button">&times;</button>
            </div>
            <div class="dm-body" id="dmBody"></div>
            <div class="dm-input">
                <input id="dmInput" type="text" maxlength="1000" placeholder="输入消息，回车发送">
                <button id="dmSend" type="button">发送</button>
            </div>`;
        document.body.appendChild(el);
        el.querySelector('.dm-close').addEventListener('click', closeChat);
        el.querySelector('#dmSend').addEventListener('click', sendDm);
        el.querySelector('#dmInput').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') { e.preventDefault(); sendDm(); }
        });
    }

    function openChat(username, nickname, icon) {
        buildChat();
        close();   // 关闭好友弹窗，聚焦聊天
        dmFriend = username;
        if (nickname) nickCache[username] = nickname;
        document.getElementById('dmHeadName').textContent = nickname || username;
        document.getElementById('dmHeadAvatar').src = icon || DEFAULT_AVATAR;
        document.getElementById('dmChat').style.display = 'flex';
        loadHistory();
        setTimeout(() => { const i = document.getElementById('dmInput'); if (i) i.focus(); }, 50);
    }

    function closeChat() {
        const el = document.getElementById('dmChat');
        if (el) el.style.display = 'none';
        dmFriend = null;
    }

    function loadHistory() {
        const body = document.getElementById('dmBody');
        body.innerHTML = `<div class="dm-empty">加载中...</div>`;
        fetch('/messages/history?friend=' + encodeURIComponent(dmFriend) + '&size=50')
            .then(r => r.json())
            .then(list => {
                body.innerHTML = '';
                if (!Array.isArray(list) || !list.length) {
                    body.innerHTML = `<div class="dm-empty">还没有消息，打个招呼吧</div>`;
                } else {
                    list.forEach(appendDm);
                }
                scrollDmBottom();
                refreshBadge();   // 打开会话已把对方消息标记已读
            })
            .catch(() => body.innerHTML = `<div class="dm-empty">加载失败</div>`);
    }

    function appendDm(m) {
        const body = document.getElementById('dmBody');
        if (!body) return;
        const empty = body.querySelector('.dm-empty');
        if (empty) empty.remove();
        const mine = m.sender === me();
        const el = document.createElement('div');
        el.className = 'dm-msg ' + (mine ? 'me' : 'other');
        el.innerHTML = `<div class="dm-bubble">${esc(m.content)}</div><div class="dm-time">${esc(m.createTime || '')}</div>`;
        body.appendChild(el);
    }

    function scrollDmBottom() {
        const body = document.getElementById('dmBody');
        if (body) body.scrollTop = body.scrollHeight;
    }

    function sendDm() {
        const input = document.getElementById('dmInput');
        const text = input.value.trim();
        if (!text || !dmFriend) return;
        const btn = document.getElementById('dmSend');
        btn.disabled = true;
        fetch('/messages/send', {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': csrf(), 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'to=' + encodeURIComponent(dmFriend) + '&content=' + encodeURIComponent(text)
        }).then(async r => {
            btn.disabled = false;
            if (r.ok) {
                const dto = await r.json();
                input.value = '';
                appendDm(dto);
                scrollDmBottom();
                input.focus();
            } else {
                const data = await r.json().catch(() => ({}));
                toast(data.error || '发送失败');
            }
        }).catch(() => { btn.disabled = false; toast('网络错误'); });
    }

    // 收到对方私信推送（订阅 /user/queue/dm）
    function onDm(payload) {
        if (!payload) return;
        const chatEl = document.getElementById('dmChat');
        const chatOpen = dmFriend && payload.sender === dmFriend && chatEl && chatEl.style.display !== 'none';
        if (chatOpen) {
            appendDm(payload);
            scrollDmBottom();
            fetch('/messages/read?friend=' + encodeURIComponent(dmFriend),
                { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf() } }).catch(() => {});
        } else {
            const name = nickCache[payload.sender] || payload.sender;
            toast(name + '：' + (payload.content || ''), payload.fromIcon);
            refreshBadge();
            const mo = document.getElementById('friendsModal');
            if (mo && mo.style.display !== 'none') loadFriends();
        }
    }

    // ---------------- toast ----------------
    let toastTimer = null;
    function toast(text, icon) {
        let t = document.getElementById('friendToast');
        if (!t) {
            t = document.createElement('div');
            t.id = 'friendToast';
            t.className = 'friend-toast';
            document.body.appendChild(t);
        }
        t.innerHTML = (icon ? `<img src="${esc(icon)}" onerror="this.style.display='none'">` : '') +
            `<span>${esc(text)}</span>`;
        t.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => t.classList.remove('show'), 3500);
    }

    // ---------------- 大厅独立连接 ----------------
    function connectStandalone() {
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            console.warn('好友通知：SockJS/Stomp 未加载');
            return;
        }
        try {
            const socket = new SockJS('/ws');
            const client = Stomp.over(socket);
            client.debug = null;
            client.connect({ 'X-CSRF-TOKEN': csrf() }, function () {
                client.subscribe('/user/queue/friend', function (msg) {
                    try { onNotification(JSON.parse(msg.body)); } catch (e) {}
                });
                client.subscribe('/user/queue/dm', function (msg) {
                    try { onDm(JSON.parse(msg.body)); } catch (e) {}
                });
                // 充值状态推送
                client.subscribe('/user/queue/recharge', function (msg) {
                    try {
                        if (typeof window.RechargeNotifyHandler === 'function') {
                            window.RechargeNotifyHandler(msg);
                        }
                    } catch (e) {}
                });
            }, function (err) {
                console.warn('好友通知连接失败，将重试', err);
                setTimeout(connectStandalone, 10000);
            });
            window._friendStomp = client;
        } catch (e) {
            console.warn('好友通知连接异常', e);
        }
    }

    // ---------------- 导出 + 自动初始化 ----------------
    window.FriendsUI = {
        open, close, sendRequest, onNotification, onDm, openChat, refreshBadge, connectStandalone
    };

    document.addEventListener('DOMContentLoaded', function () {
        buildModal();
        const btn = document.getElementById('friendsBtn');
        if (btn) btn.addEventListener('click', open);
        refreshBadge();
    });
})();
