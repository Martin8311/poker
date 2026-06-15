function clearPlayedCards(){
    visualPositions.forEach(domId => {
        const dom = domId + "-card";
        const cardsContainer = document.querySelector(`.played-cards-container[data-player= "${dom}"]`);
        cardsContainer.innerHTML = '';
    });

    const dom = "currentPlayer-card";
    const cardsContainer = document.querySelector(`.played-cards-container[data-player= "${dom}"]`);
    cardsContainer.innerHTML = '';
}

function renderPlayedCards(currentPlayer, playedCards){
    const domId = seatDomMap[seatPlayerMap[currentPlayer]] + "-card";
    const cardsContainer = document.querySelector(`.played-cards-container[data-player= "${domId}"]`);

    // 清空该玩家之前的出牌（如果需要保留历史记录，可以注释掉这行）
    cardsContainer.innerHTML = '';

    if(playedCards === null || playedCards.length === 0){
        const noCardEl = document.createElement('div');
        noCardEl.className = 'played-card';
        noCardEl.textContent = '不出';
        noCardEl.style.color = 'rgba(250,175,255,0.7)';
        noCardEl.style.fontSize = '25px';
        cardsContainer.appendChild(noCardEl);
        soundManager.playSound('pass');
        return;
    }

    // 遍历出牌列表，创建牌的元素并添加到容器
    playedCards.forEach((card, index) => {
        const cardEl = document.createElement('div');
        cardEl.className = 'played-card';

        // 关键：改为 absolute 定位，基于父容器定位
        cardEl.style.position = 'absolute';
        // 每张牌向右偏移固定距离（index 越大，偏移越多）
        cardEl.style.left = `${index * 20}px`;
        // 垂直方向居中（可根据需要调整 top 值）
        cardEl.style.top = '50%';
        cardEl.style.transform = 'translateY(-50%)';
        // 控制层级：index 越小，z-index 越低（确保左侧牌在下方）
        cardEl.style.zIndex = index + 3;

        // 使用牌的name属性获取图片（与手牌图片路径保持一致）
        const imgSrc = getCardImageUrl(card.url);

        cardEl.innerHTML = `<img 
            src="${imgSrc}" 
            alt="${card.name}" 
            title="${card.name}"
            style="width:80px; height: 120px; object-fit: cover"
        >`;

        cardsContainer.appendChild(cardEl);
    });
    soundManager.playSound('cardPlay');
}

/**
 * 渲染当前玩家的手牌（显示图片）
 * @param {Array<Card>} cards 后端返回的当前玩家手牌列表
 */
function renderMyCards(cards) {
    const cardsContainer = document.getElementById('myCards');
    if (!cardsContainer) return;

    cardsContainer.innerHTML = ''; // 清空原有内容

    // 遍历手牌，生成带图片的牌元素
    cards.forEach(card => {
        const cardEl = document.createElement('div');
        cardEl.className = 'card'; // 牌的基础样式类
        cardEl.dataset.cardId = card.name; // 用name作为唯一标识（后续出牌用）
        cardEl.dataset.url = card.url;

        // 牌面结构：包含图片和隐藏的牌值（用于逻辑判断）
        cardEl.innerHTML = `
            <div class="card-inner">
                <!-- 扑克图片 -->
                <img src="${getCardImageUrl(card.url)}" alt="${card.name}" class="card-img">
                <!-- 隐藏的牌值（不显示，用于比较大小等逻辑） -->
                <span class="card-value" style="display: none;">${card.value}</span>
                <span class="card-rank"  style="display: none;">${card.rank}</span>
            </div>
        `;

        cardEl.addEventListener('click', () => toggleCardSelection(cardEl));
        cardsContainer.appendChild(cardEl);
    });
}

function firstPlayerTag(username){
    visualPositions.forEach(domId => {
        document.getElementById(domId + 'Ident').textContent = '?';
    });

    const domId = seatDomMap[seatPlayerMap[username]];
    const identEl = document.getElementById(domId + 'Ident');
    identEl.textContent = '大鬼子'; // 拿到真实昵称后赋值

}


function clearIdent(){
    positions.forEach(domId => {
        document.getElementById(domId + 'Ident').textContent = '?';
    });
}

async function loadPlayers(seatPlayerMap) {
    // 1. 清空所有位置
    visualPositions.forEach(domId => {
        document.getElementById(domId + 'Name').textContent = '等待玩家';
        document.getElementById(domId + 'Status').textContent = ' ';
        document.getElementById(domId + 'Score').textContent = '0';
        document.getElementById(domId + 'Games').textContent = '0';
        document.getElementById(domId + 'WinRate').textContent = '0%';
        document.getElementById(domId + 'Ident').textContent = '?';

        // 重置头像为默认用户图标
        const avatarContainer = document.querySelector(`#${domId} .player-avatar`);
        if (avatarContainer) {
            avatarContainer.innerHTML = '<i class="fas fa-user"></i>';
        }
    });

    // 2. 渲染每个座位的玩家
    Object.entries(seatPlayerMap).forEach(([playerName, seat]) => {
        const domId = seatDomMap[seat];

        const nameEl = document.getElementById(domId + 'Name');
        const statusEl = document.getElementById(domId + 'Status');
        const scoreEl = document.getElementById(domId + 'Score');
        const gamesEl = document.getElementById(domId + 'Games');
        const winRateEl = document.getElementById(domId + 'WinRate');
        const avatarContainer = document.querySelector(`#${domId} .player-avatar`);

        getUserInfo(playerName)
            .then(userInfo => {
                console.log(userInfo)
                nameEl.textContent = userInfo.nickName; // 拿到真实昵称后赋值
                statusEl.textContent = ' ';
                scoreEl.textContent = `积分:${userInfo.score}`;
                gamesEl.textContent = `局数:${userInfo.total_game}`;
                winRateEl.textContent = '胜率' + userInfo.win_rate;

                if(avatarContainer){
                    avatarContainer.innerHTML = '';

                    // 创建图片元素
                    const avatarImg = document.createElement('img');
                    // 设置头像路径（优先使用用户自定义头像，否则用默认头像）
                    const avatarUrl = userInfo.iconUrl ? ('/avatar/' + userInfo.iconUrl) : '/icon/default-avatar.jpg';
                    avatarImg.src = avatarUrl;
                    avatarImg.alt = `${userInfo.nickName || playerName}的头像`;

                    // 设置图片样式（确保适配圆形容器）
                    avatarImg.style.width = '100%';
                    avatarImg.style.height = '100%';
                    // avatarImg.style.borderRadius = '50%';
                    avatarImg.style.objectFit = 'cover'; // 保持比例填充容器

                    // 添加图片加载失败处理（兜底机制）
                    avatarImg.onerror = function() {
                        // 加载失败时显示默认图标
                        avatarContainer.innerHTML = '<i class="fas fa-user"></i>';
                        console.warn(`头像加载失败: ${avatarUrl}`);
                    };

                    // 将图片添加到容器
                    avatarContainer.appendChild(avatarImg);
                }


            }).catch(error =>{
                console.log("尝试重新加载");
                wait(2000);
                rotatedOrder = generateRotatedOrder(currentSeat);
                seatDomMap = mapToVisualPositions(rotatedOrder, currentSeat);
                loadPlayers(seatPlayerMap);
                return ;
        });
    });
}

function stopGameTimer(){
    if (window.turnTimer) {
        window.clearInterval(turnTimer);
        window.turnTimer = null;
    }

    // 移除所有玩家的高亮状态
    document.querySelectorAll('.player-position').forEach(el => {
        el.classList.remove('current-turn', 'blinking');
    });


}

/**
 * 显示游戏结束遮罩层
 */
function showGameOverOverlay(){
    let overlay = document.getElementById('gameOverOverlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'gameOverOverlay';
        overlay.className = 'game-over-overlay';
        document.body.appendChild(overlay);
    }

    // 设置遮罩层基本样式
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100%';
    overlay.style.height = '100%';
    overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.8)';
    overlay.style.display = 'flex';
    overlay.style.flexDirection = 'column';
    overlay.style.justifyContent = 'center';
    overlay.style.alignItems = 'center';
    overlay.style.zIndex = '1000';
    overlay.style.color = 'white';

    // 添加标题
    overlay.innerHTML = `
        <h2 style="font-size: 2rem; margin-bottom: 20px;">游戏结束</h2>
        <div id="scoreBoard" style="width: 80%; max-width: 600px;"></div>
        <div class="game-over-buttons" style="margin-top: 30px; display: flex; gap: 20px;">
            <button id="restartGame" class="btn btn-success">再来一局</button>
            <button id="leaveRoom" class="btn btn-danger">离开房间</button>
        </div>
    `;

    // 绑定按钮事件
    document.getElementById('restartGame').addEventListener('click', restartGame);

}

/**
 * 渲染玩家得分面板
 * @param {Object} scoreMap - 玩家昵称与得分的映射 {nickname: score}
 */
async function renderScoreBoard() {
    // 1. 先移除旧的计分板（避免重复创建）
    const oldScoreBoard = document.getElementById('scoreBoard');
    if (oldScoreBoard) oldScoreBoard.remove();

    // 2. 创建遮幕容器（父容器，半透明背景+居中悬浮）
    const scoreBoardOverlay = document.createElement('div');
    scoreBoardOverlay.id = 'scoreBoard';
    scoreBoardOverlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background-color: rgba(0, 0, 0, 0.6); /* 半透明遮幕背景 */
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 9999; /* 确保在最上层 */
        backdrop-filter: blur(4px); /* 背景模糊，增强遮幕感 */
    `;

    // 3. 创建计分板主体（白色卡片，带阴影）
    const scoreBoardContent = document.createElement('div');
    scoreBoardContent.style.cssText = `
        width: 90%;
        max-width: 500px;
        background-color: #fff;
        border-radius: 12px;
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
        overflow: hidden;
        position: relative; /* 用于关闭图标的绝对定位 */
    `;

    // 4. 添加关闭图标（右上角）
    const closeIcon = document.createElement('div');
    closeIcon.style.cssText = `
        position: absolute;
        top: 12px;
        right: 12px;
        width: 24px;
        height: 24px;
        cursor: pointer;
        color: #666;
        font-size: 20px;
        font-weight: bold;
        display: flex;
        justify-content: center;
        align-items: center;
        border-radius: 50%;
        background-color: #f5f5f5;
        transition: all 0.2s ease;
    `;
    closeIcon.textContent = '×'; // 关闭符号（乘号）
    // 关闭图标hover效果
    closeIcon.addEventListener('mouseover', () => {
        closeIcon.style.backgroundColor = '#ff4444';
        closeIcon.style.color = '#fff';
    });
    closeIcon.addEventListener('mouseout', () => {
        closeIcon.style.backgroundColor = '#f5f5f5';
        closeIcon.style.color = '#666';
    });
    // 点击关闭遮幕
    closeIcon.addEventListener('click', () => {
        scoreBoardOverlay.remove();
    });

    // 5. 添加计分板标题
    const boardTitle = document.createElement('div');
    boardTitle.style.cssText = `
        padding: 16px;
        background-color: #4CAF50;
        color: #fff;
        font-size: 18px;
        font-weight: bold;
        text-align: center;
    `;
    boardTitle.textContent = '积分';

    // 6. 处理得分数据（按得分从高到低排序）

    const sortedPlayers = Object.entries(scoreMap).sort((a, b) => Number(b[1]) - Number(a[1]));

    // 7. 创建得分表格
    const table = document.createElement('table');
    table.style.cssText = `
        width: 100%;
        border-collapse: collapse;
        text-align: center;
    `;

    // 表格头部
    const thead = document.createElement('thead');
    thead.innerHTML = `
        <tr style="background-color: #f8f8f8;">
            <th style="padding: 12px; border-bottom: 1px solid #ddd; color: #333;">玩家</th>
            <th style="padding: 12px; border-bottom: 1px solid #ddd; color: #333;">上局得分</th>
            <th style="padding: 12px; border-bottom: 1px solid #ddd; color: #333;">总得分</th>
        </tr>
    `;

    // 表格主体
    const tbody = document.createElement('tbody');
    sortedPlayers.forEach(([nickname, score], index) => {
        const tr = document.createElement('tr');
        // 交替行背景色（增强可读性）
        tr.style.backgroundColor = index % 2 === 0 ? '#fff' : '#fafafa';
        // 行hover效果
        tr.style.transition = 'background-color 0.2s ease';
        tr.addEventListener('mouseover', () => {
            tr.style.backgroundColor = '#f0f9f0';
        });
        tr.addEventListener('mouseout', () => {
            tr.style.backgroundColor = index % 2 === 0 ? '#fff' : '#fafafa';
        });

        // 玩家昵称单元格
        const nicknameTd = document.createElement('td');
        nicknameTd.style.padding = '12px';
        nicknameTd.style.borderBottom = '1px solid #eee';
        nicknameTd.style.color = '#333';
        nicknameTd.textContent = nickname;

        // 得分单元格
        const scoreTd = document.createElement('td');

        scoreTd.style.padding = '12px';
        scoreTd.style.borderBottom = '1px solid #eee';
        scoreTd.style.color = '#333';
        // 得分正负色区分（正数绿色，负数红色）
        scoreTd.style.color = score > 0 ? '#2e7d32' : score < 0 ? '#c62828' : '#666';
        scoreTd.textContent = score;

        // 总分单元格
        const totalScoreTd = document.createElement('td');
        totalScoreTd.style.padding = '12px';
        totalScoreTd.style.borderBottom = '1px solid #eee';
        totalScoreTd.style.color = '#333';
        // 得分正负色区分（正数绿色，负数红色）
        totalScoreTd.style.color = totalScoreMap[nickname] > 0 ? '#2e7d32' : totalScoreMap[nickname] < 0 ? '#c62828' : '#666';
        totalScoreTd.textContent = totalScoreMap[nickname];

        tr.appendChild(nicknameTd);
        tr.appendChild(scoreTd);
        tr.appendChild(totalScoreTd)
        tbody.appendChild(tr);
    });

    // 组装表格
    table.appendChild(thead);
    table.appendChild(tbody);

    // 7. 创建按钮容器（用于放置再来一局按钮）
    const buttonContainer = document.createElement('div');
    buttonContainer.style.cssText = `
        padding: 12px;
        text-align: center;
        border-bottom: 1px solid #eee;
    `;

    let creator_name = "";

    await getCreatorName()
        .then(nickname => {
            creator_name = nickname; // 拿到真实昵称后赋值
        })
        .catch(error => {
            creator_name = '未知玩家'; // 失败时显示默认值
        });

    // 8. 组装计分板主体
    scoreBoardContent.appendChild(closeIcon); // 关闭图标（最上层）
    scoreBoardContent.appendChild(boardTitle); // 标题
    scoreBoardContent.appendChild(buttonContainer);
    scoreBoardContent.appendChild(table); // 得分表格


    // 9. 组装遮幕容器并添加到页面
    scoreBoardOverlay.appendChild(scoreBoardContent);
    document.body.appendChild(scoreBoardOverlay);

    // 10. 点击遮幕背景关闭（可选：增强交互）
    scoreBoardOverlay.addEventListener('click', (e) => {
        // 只点击遮幕背景时关闭（不包含计分板主体）
        if (e.target === scoreBoardOverlay) {
            scoreBoardOverlay.remove();
        }
    });

}

// 当前页面玩家出牌
function showTurnNotification(username, isFirst) {
    // 移除所有玩家的高亮状态
    document.querySelectorAll('.player-position').forEach(el => {
        el.classList.remove('current-turn');
    });

    // 高亮自己（假设玩家座位DOM的ID为"player-用户名"）
    const domId = seatDomMap[seatPlayerMap[username]];
    const currentPlayerEl = document.getElementById(domId);
    if (currentPlayerEl) {
        currentPlayerEl.classList.add('current-turn');
    }

    const actionButtons = document.getElementById('actionButtons');
    const playBtn = document.getElementById('playBtn');
    const passBtn = document.getElementById('passBtn');

    actionButtons.style.display = 'flex'; // 显示按钮容器

    // 2. 首回合仅显示“出牌”按钮
    if (isFirst) {
        playBtn.style.display = 'block';
        passBtn.style.display = 'none'; // 隐藏“不出”按钮
    } else {
        playBtn.style.display = 'block';
        passBtn.style.display = 'block'; // 显示两个按钮
    }

    const cardArea = document.querySelector(`.played-cards-container[data-player="${domId}-card"]`);
    startTurnTimer(username, domId, cardArea);
}

function highLightHandEmptyPlayer(username){
    const domId = seatDomMap[seatPlayerMap[username]];

    // 高亮当前玩家（假设玩家座位DOM的ID为"player-用户名"）
    const currentPlayerEl = document.getElementById(domId);
    if (currentPlayerEl) {
        currentPlayerEl.classList.add('hand-empty');
    }

}

function removeHighLightHandEmptyPlayer(){
    document.querySelectorAll('.player-position').forEach(el => {
        el.classList.remove('hand-empty');
    });
}

// 高亮当前出牌玩家
function highlightCurrentPlayer(username) {
    // 1. 清除旧定时器（避免轮次切换时计时叠加）
    if (turnTimer) {
        window.clearInterval(turnTimer);
        turnTimer = null;
    }

    // 移除所有玩家的高亮状态
    document.querySelectorAll('.player-position').forEach(el => {
        el.classList.remove('current-turn');
    });

    const domId = seatDomMap[seatPlayerMap[username]];

    // 高亮当前玩家（假设玩家座位DOM的ID为"player-用户名"）
    const currentPlayerEl = document.getElementById(domId);
    if (currentPlayerEl) {
        currentPlayerEl.classList.add('current-turn');
    }

    // 清空当前玩家面前的牌区
    const cardArea = document.querySelector(`.played-cards-container[data-player="${domId}-card"]`);
    if (cardArea) {
        cardArea.innerHTML = ''; // 清空牌区内容
        startTurnTimer(username, domId, cardArea);
    }
}

/**
 * 再来一局按钮事件
 */
function restartGame() {
    // 发送重新开始请求到后端
    if (window.stompClient && window.roomId) {
        window.stompClient.send(`/app/rooms/${window.roomId}/restart`, {}, JSON.stringify({
            username: window.currentUsername
        }));

        // 移除游戏结束遮罩层
        const overlay = document.getElementById('gameOverOverlay');
        if (overlay) overlay.remove();
    }
}

async function removePlayedCardsFromHand(playedCards){
    const myCardsContainer = document.getElementById('myCards');
    const playedCardNames = playedCards.map(card => card.name);

    // 遍历手牌，移除已出的牌
    document.querySelectorAll('#myCards .card').forEach(cardEl => {
        if (playedCardNames.includes(cardEl.dataset.cardId)) {
            myCardsContainer.removeChild(cardEl);
        }
    });

    // 清空选中状态
    selectedCards = [];

    // 检查剩余手牌数量
    const remainingCards = myCardsContainer.querySelectorAll('.card');
    const hasRemainingCards = remainingCards.length > 0;


    if (!hasRemainingCards) {
        await stompClient.send("/app/rooms/" + roomId + "/hand-empty",
            {},
            JSON.stringify({
                currentTurnPlayer: currentUsername,
                type: "HAND_EMPTY",
                timestamp: getTime()
            }));
    }
}

// 3. 提示工具函数：创建/显示/自动隐藏字幕提示
function showHandCardTip (text, isError = true){
    // 1. 先获取手牌容器，用于后续插入提示
    const myCardsContainer = document.getElementById('myCards');
    if (!myCardsContainer) return; // 容错：若手牌容器不存在，终止执行

    // 先移除旧提示（避免重复叠加）
    const oldTip = document.querySelector('#handCardTip');
    if (oldTip) oldTip.remove();

    // 创建提示元素
    const tipEl = document.createElement('div');
    tipEl.id = 'handCardTip';
    tipEl.textContent = text;
    // 提示样式：在手牌区域下方居中，半透明背景，圆角边框
    tipEl.style.cssText = `
            position: absolute;
            top: 95%;
            left: 0%;
            transform: translateY(-100%);
            margin-top: 8px;
            padding: 6px 16px;
            border-radius: 4px;
            font-size: 14px;
            font-weight: 500;
            z-index: 100;
            transition: opacity 0.3s ease;
            ${isError ? 'background-color: rgba(220, 38, 38, 0.9); color: #fff;' : 'background-color: rgba(34, 197, 94, 0.9); color: #fff;'}
        `;

    // 给手牌容器添加相对定位，确保提示绝对定位生效
    if (getComputedStyle(myCardsContainer).position !== 'relative') {
        myCardsContainer.style.position = 'relative';
    }

    // 插入提示到手牌容器内
    myCardsContainer.appendChild(tipEl);

    // 3秒后自动隐藏（先淡入再淡出）
    setTimeout(() => {
        tipEl.style.opacity = '0'; // 透明度渐变到0
        setTimeout(() => tipEl.remove(), 300); // 等待渐变结束后移除元素
    }, 3000);
}

/**
 * 展示玩家准备 / 取消准备
 */
function showPlayerIsReady(username, isReady){
    const domId = seatDomMap[seatPlayerMap[username]] + 'Status';
    const statusEl = document.getElementById(domId);
    if(isReady){
        statusEl.textContent = '已准备';
    }else{
        statusEl.textContent = '未准备';
    }
}

function hidePlayerIsReady(message){
    positions.forEach(domId => {
        if((seatDomMap[seatPlayerMap[creator_name]] === domId) && message === '未准备')
            document.getElementById(domId + 'Status').textContent = '房主';
        else
            document.getElementById(domId + 'Status').textContent = message;
    });
}

function showHandEmptyPlayer(name, order, team){
    const teamMap = ["好人", "小鬼子", "大鬼子"];
    const domId = seatDomMap[seatPlayerMap[name]];
    const ident = teamMap[team]
    const identEl = document.getElementById(domId + 'Ident');
    identEl.textContent = '已出空! 身份:' + ident + ' 名次:' + order; // 拿到真实昵称后赋值
    highLightHandEmptyPlayer(name);
}

async function loadScoreBoardData(){
    renderScoreBoard();
}

