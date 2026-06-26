/**
 * 房间内「加好友」：在每个其他玩家的座位上挂一个加好友按钮。
 *
 * 复用全局 seatPlayerMap({username:座位}) 与 seatDomMap({座位:domId前缀})；
 * 通过包裹 loadPlayers，在每次座位重渲染后刷新按钮，自动跟随玩家加入/离开。
 * 实际发送走 FriendsUI.sendRequest（friends.js）。
 */
(function () {
    function currentMe() {
        try { if (typeof username !== 'undefined' && username) return username; } catch (e) {}
        try { if (typeof currentUsername !== 'undefined' && currentUsername) return currentUsername; } catch (e) {}
        return null;
    }

    function injectSeatButtons() {
        if (typeof seatPlayerMap === 'undefined' || typeof seatDomMap === 'undefined') return;
        // 先清掉旧按钮，再按当前座位重建（处理玩家离开 / 换座）
        document.querySelectorAll('.seat-add-friend-btn').forEach(b => b.remove());

        const me = currentMe();
        Object.entries(seatPlayerMap).forEach(([uname, seat]) => {
            if (!uname || uname === me) return;
            const domId = seatDomMap[seat];
            const seatEl = domId && document.getElementById(domId);
            const info = seatEl && seatEl.querySelector('.player-info');
            if (!info) return;

            const btn = document.createElement('button');
            btn.className = 'seat-add-friend-btn';
            btn.type = 'button';
            btn.textContent = '加好友';
            btn.addEventListener('click', function () {
                if (window.FriendsUI) FriendsUI.sendRequest(uname, btn);
            });
            info.appendChild(btn);
        });
    }

    function wrap() {
        if (typeof window.loadPlayers !== 'function') return false;
        const orig = window.loadPlayers;
        window.loadPlayers = function () {
            const result = orig.apply(this, arguments);
            // loadPlayers 可能是 async（返回 Promise），渲染完成后再注入
            Promise.resolve(result).then(injectSeatButtons).catch(injectSeatButtons);
            return result;
        };
        return true;
    }

    if (!wrap()) {
        document.addEventListener('DOMContentLoaded', wrap);
    }
})();
