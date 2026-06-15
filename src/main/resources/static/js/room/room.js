/**
 *  页面 DOM 加载完毕后，触发核心初始化流程：
 **/
document.addEventListener('DOMContentLoaded', function(){
    playBtn.onclick = () => handlePlayCard(false);
    passBtn.onclick = () => handlePassTurn();
    window.soundManager.preloadAllSounds()
    initWebSocket(); // 初始化WebSocket
});

// 监听页面刷新/关闭事件
window.addEventListener('beforeunload', (e) => {
    const savedState = localStorage.getItem('roomState');
    // 若用户在房间内，提示确认
    if (savedState) {
        const message = '您正在游戏中，刷新/关闭页面会中断游戏，确定要继续吗？';
        e.returnValue = message; // 兼容部分浏览器
        return message; // 兼容部分浏览器
    }
});

// 发送聊天消息
document.getElementById('sendMessageBtn').addEventListener('click', function() {
    let messageInput = document.getElementById('messageInput');
    let content = messageInput.value.trim();

    if (content) {
        stompClient.send("/app/rooms/" + roomId + "/message", {}, JSON.stringify({
            type: 'USER_CHAT',
            senderNickname: currentUserNickname,
            content: content,
            timestamp: getTime()
        }));
        messageInput.value = '';
    }
});

presetChatBtn.addEventListener('click', function(e){
    e.stopPropagation();
    presetChatList.style.display = presetChatList.style.display === 'block' ? 'none' : 'block';
});

document.addEventListener('click', function() {
    presetChatList.style.display = 'none';
});

presetChatItems.forEach(item => {
    item.addEventListener('click', function() {
        const content = this.getAttribute('data-content');

        if (content) {
            stompClient.send("/app/rooms/" + roomId + "/message", {}, JSON.stringify({
                type: 'USER_CHAT',
                senderNickname: currentUserNickname,
                content: content,
                timestamp: getTime()
            }));
        }

        presetChatList.style.display = 'none';
    });
});

totalScoreBoardBtn.addEventListener('click', function (){
    loadScoreBoardData();
    // scoreBoardModal.style.display = 'flex';
})

// 关闭积分面板
closeButtons.forEach(button => {
    button.addEventListener('click', function() {
        window.scoreBoardModal.style.display = 'none';
    });
});

// 点击模态框外部关闭
window.addEventListener('click', function(event) {
    if (event.target === scoreBoardModal) {
        scoreBoardModal.style.display = 'none';
    }
});


// 按Enter发送消息
document.getElementById('messageInput').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        document.getElementById('sendMessageBtn').click();
    }
});

document.getElementById('closeSoundBtn').addEventListener('click', function(){
    window.isPlaySound = !window.isPlaySound;

    // 同步样式：添加/移除 'muted' 类
    this.classList.toggle('muted', !window.isPlaySound);
});

document.getElementById('closeMusicBtn').addEventListener('click', function(){
    // 切换状态
    window.isPlayMusic = !window.isPlayMusic;

    // 同步样式：添加/移除 'muted' 类
    this.classList.toggle('muted', !window.isPlayMusic);

    // 播放/暂停背景音乐（注意：原代码中的 playMusic 应为 playSound）
    if (window.isPlayMusic) {
        if(window.gameStatus)
            soundManager.playMusic('backMusic', { loop: true }); // 背景音乐通常循环播放
    } else {
        soundManager.pauseSound('backMusic');
    }
});

document.getElementById('readyBtn')?.addEventListener('click', function(){
    this.classList.toggle('ready');

    // 切换按钮文字
    const isReady = this.classList.contains('ready');
    this.textContent = isReady ? ' 取消准备' : ' 准备';

    // 发送准备/取消准备请求到后端
    sendReadyRequest(isReady);
});

document.getElementById('startGameBtn')?.addEventListener('click', function() {
    stompClient.send("/app/rooms/" + roomId + "/start", {}, JSON.stringify({
        type: 'START_GAME',
        timestamp: getTime()
    }));
});

document.getElementById('leaveRoomBtn').addEventListener('click', function() {
    stompClient.send("/app/rooms/" + roomId + "/leave", {}, JSON.stringify({
        type: 'PLAYER_LEAVE',
        senderNickname: currentUserNickname,
        relatedName: username,
        content: null,
        timestamp: getTime()
    }));

    hasSentLeaveRequest = true;

    // 短暂延迟后跳转
    setTimeout(() => {
        window.location.href = '/hall';
    }, 500);

});

document.getElementById('startGameRestartBtn')?.addEventListener('click', function(){
    stompClient.send(`/app/rooms/${roomId}/restart`, {}, JSON.stringify({
        type: "GAME_RESTART",
        timestamp: getTime() // 使用标准时间戳
    }));
});

// 关闭窗口时发送离开通知
window.addEventListener('beforeunload', function() {
    if(!window.gameStatus){
        if (stompClient && stompClient.connected && !hasSentLeaveRequest) {
            stompClient.send("/app/rooms/" + roomId + "/leave", {}, JSON.stringify({
                type: 'PLAYER_LEAVE',
                senderNickname: currentUserNickname,
                relatedName: username,
                content: null,
                timestamp: getTime()
            }));
        }
    }
});

// 初始化WebSocket连接
async function initWebSocket(){
    // 步骤1：创建 SockJS 实例（兼容不支持原生 WebSocket 的浏览器）
    const socket = new SockJS('/ws');

    // 步骤2：基于 SockJS 创建 Stomp 客户端（简化 WebSocket 消息协议）
    stompClient = Stomp.over(socket);

    // 连接参数（包含CSRF令牌）
    const headers = { 'X-CSRF-TOKEN': getCsrfToken() };

    // 1. 发起连接（异步）
    stompClient.connect(headers,
        // 连接成功的回调（核心：所有操作放这里）
        async function(frame) {
            // 1. 订阅房间主题（必须在连接成功后）
            stompClient.subscribe(`/topic/rooms.${roomId}`, function(message) {
                handleRoomMessage(JSON.parse(message.body));
            });

            // 2. 获取当前用户的信息
            await stompClient.send("/app/rooms/" + roomId + "/getInfo", {}, JSON.stringify({
                type: 'GET_INFO',
                senderNickname: null,
                content: null,
                timestamp: getTime()
            }));

            // 3. 发送"加入房间"消息（必须在连接成功后）
            await stompClient.send("/app/rooms/" + roomId + "/join", {}, JSON.stringify({
                type: 'PLAYER_JOIN',
                senderNickname: currentUserNickname,
                content: null,
                timestamp: getTime()
            }));

            const response = await fetch(`/room/${roomId}/get_room_status`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'text/plain',
                    'X-CSRF-TOKEN': getCsrfToken()
                },

            });

            const responseText = await response.text();

            if(responseText === "START"){
                window.gameStatus = true;
                console.log("重返房间");
                // TODO: 重返房间时 要从后端恢复信息

                await restoreGameData();
            }

        },

        // 连接失败的回调
        function(error) {
            console.error('WebSocket连接失败:', error);
            // 5秒后重试连接
            setTimeout(initWebSocket, 60000);
        }
    );
}

// 处理房间相关的消息
function handleRoomMessage(data){
    // 根据消息类型处理不同业务
    switch(data.type) {
        case "GET_INFO":
            handleGetUserInfo(data);
            break;
        case "PLAYER_JOIN":
            handlePlayerJoin(data);
            break;
        case "PLAYER_LEAVE":
            handlePlayerLeave(data);
            break;
        case "USER_CHAT":
            handleUserChat(data);
            break;
        case "START_GAME":
            handleGameStart(data);
            break;
        case "ALLOCATE_CARD":
            handleDealCards(data);
            break;
        case "Turn":
            handleTurn(data);
            break;
        case "ACTOR":
            handleActor(data);
            break;
        case "PASS":
            handlePass(data);
            break;
        case "HAND_EMPTY":
            handleHandEmpty(data);
            break;
        case "GAME_OVER":
            handleGameOver(data);
            break;
        case "GAME_RESTART":
            handleGameRestart(data);
            break;
        case "READY":
            handleReady(data);
            break;
        default:
            console.log("未知消息类型:", data.type);
    }
}

// "USER_CHAT"
function handleUserChat(data){
    // 获取聊天消息容器
    const chatContainer = document.getElementById('chatMessages');

    // 创建消息元素
    const messageEl = document.createElement('div');
    messageEl.className = 'chat-messages';

    // 设置消息内容（包含发送者和消息文本）
    messageEl.innerHTML = `
        <span class="message-time">[${formatTime(new Date())}]</span>
        <span class="message-sender">${data.senderNickname}</span>
        <span class="message-colon">:</span>
        <span class="message-text">${escapeHtml(data.content)}</span>
    `;

    // 添加到容器并滚动到底部
    chatContainer.appendChild(messageEl);
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

function handlePlayerJoin(data){
    seatPlayerMap = data.seatPlayerMap || {};

    currentSeat = data.currentSeat;

    // 2. 生成轮转顺序并映射到前端位置
    if(currentUsername === data.relatedName) {
        rotatedOrder = generateRotatedOrder(currentSeat);
        seatDomMap = mapToVisualPositions(rotatedOrder, currentSeat);
    }

    const currentCount = data.numOfPlayers;

    const playerCountEl = document.getElementById('playerCount');
    if (playerCountEl) {
        playerCountEl.textContent = `当前玩家: ${currentCount}/5`; // 假设最大5人
    }

    loadPlayers(seatPlayerMap);

    // 获取聊天消息容器
    const chatContainer = document.getElementById('chatMessages');

    // 创建消息元素
    const messageEl = document.createElement('div');
    messageEl.className = 'chat-messages';

    // 设置消息内容（包含发送者和消息文本）
    messageEl.innerHTML = `
        <span class="message-time">[${formatTime(new Date())}]</span>
        <span class="message-sender">${data.senderNickname}</span>
        <span class="message-colon">:</span>
        <span class="message-text">${escapeHtml(data.content)}</span>
    `;

    // 添加到容器并滚动到底部
    chatContainer.appendChild(messageEl);
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

function handlePlayerLeave(data){
    seatPlayerMap = data.seatPlayerMap || {};

    // players = data.playerList || [];
    // const currentCount = players.length;

    const currentCount = data.numOfPlayers;

    const playerCountEl = document.getElementById('playerCount');
    if (playerCountEl) {
        playerCountEl.textContent = `当前玩家: ${currentCount}/5`; // 假设最大5人
    }

    loadPlayers(seatPlayerMap);

    // 获取聊天消息容器
    const chatContainer = document.getElementById('chatMessages');

    // 创建消息元素
    const messageEl = document.createElement('div');
    messageEl.className = 'chat-messages';

    // 设置消息内容（包含发送者和消息文本）
    messageEl.innerHTML = `
        <span class="message-time">[${formatTime(new Date())}]</span>
        <span class="message-sender">${data.senderNickname}</span>
        <span class="message-colon">:</span>
        <span class="message-text">${escapeHtml(data.content)}</span>
    `;

    // 添加到容器并滚动到底部
    chatContainer.appendChild(messageEl);
    chatContainer.scrollTop = chatContainer.scrollHeight;
}

function handleGameOver(data){
    window.gameStatus = false;
    window.soundManager.pauseSound('backMusic');
    stopGameTimer();

    window.scoreMap = data.scoreMap;
    window.totalScoreMap = data.totalScoreMap;

    renderScoreBoard();
    removeHighLightHandEmptyPlayer();
    hidePlayerIsReady('未准备');

    hideButton(playBtn);
    hideButton(passBtn);

    showButton(leaveBtn);
    showButton(readyBtn);
    showButton(startBtn);

    if(readyBtn)
        readyBtn.click();
}

function handleTurn(data){
    const actor = data.currentTurnPlayer;
    const isFirst = data.first;

    if(!window.gameStatus){
        window.gameStatus = true;
        firstPlayerTag(actor);
    }

    if(isFirst){
        clearPlayedCards();
    }

    if(currentUsername === actor){ // 当前页面用户轮次
        showTurnNotification(actor, isFirst);
    }else{ //高亮当前出牌玩家（座位区域）
        highlightCurrentPlayer(actor);
    }
}

async function handleGameStart(data){
    if(data.status === "error") {
        console.log(data.message);
        showHandCardTip(data.message);
        return;
    }

    clearIdent();

    hidePlayerIsReady(' ');
    hideButton(leaveBtn);
    hideButton(readyBtn);
    hideButton(startBtn);
    hideButton(restartBtn);

    await getCreatorName()
        .then(nickname => {
            creator_name = nickname; // 拿到真实昵称后赋值
        })
        .catch(error => {
            creator_name = '未知玩家'; // 失败时显示默认值
        });

    if(creator_name === currentUsername) {
        await stompClient.send("/app/rooms/" + roomId + "/allocate", {}, JSON.stringify({
            username: currentUsername,
            timestamp: getTime()
        }));

        await wait(3000);
        if(window.isPlayMusic)
            window.soundManager.playMusic('backMusic');

        // 发牌完毕后 确定轮次
        await stompClient.send("/app/rooms/" + roomId + "/turn", {}, JSON.stringify({
            username: currentUsername,
            timestamp: getTime()
        }));
    }
}

function handleGetUserInfo(data){
    if(currentUsername === "")
        currentUsername = data.relatedName;
}

function handleDealCards(data){
    // 通过POST方法取牌
    fetch(`/rooms/${roomId}/get_card`, {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
            'X-CSRF-TOKEN': getCsrfToken()
        },
        body: currentUsername  // 传递当前玩家用户名
    })
        .then(response => response.json())
        .then(cards => {
            // 渲染自己的手牌
            renderMyCards(cards);
        });
}

function handleHandEmpty(data){
    const player = data.actor; // 出空者
    const order = data.order; // 顺序
    const team = data.team;
    showHandEmptyPlayer(player, order, team);
}

function handleHandEmpty_old(data){
    const player = data.actor; // 出空者
    const order = data.order; // 顺序
    const teamMap = ["好人", "小鬼子", "大鬼子"];
    const team = teamMap[data.team] ; // 阵营

    // 1. 根据玩家信息获取对应的出牌区域
    const domId = seatDomMap[seatPlayerMap[player]]; // 复用已有的座位映射关系
     const cardArea = document.querySelector(`.played-cards-container[data-player="${domId}-card"]`);

    // 2. 清空该区域原牌（如果有残留）
    // cardArea.innerHTML = '';

    // 3. 创建"手牌已出空"的信息元素
    const emptyInfo = document.createElement('div');
    emptyInfo.className = 'hand-empty-info';

    // 4. 设置显示内容（可包含阵营信息）
    emptyInfo.innerHTML = `
        <div class="empty-text">${player}已出空, 名次${order}</div>
        <div class="empty-team">阵营:${team}</div>
    `;

    // 5. 添加样式（也可在CSS中定义）
    emptyInfo.style.cssText = `
        color: white;
        font-weight: bold;
        text-align: center;
        padding: 10px;
        height: 100%;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
    `;

    // 阵营文字样式（可选）
    const teamEl = emptyInfo.querySelector('.empty-team');
    if (teamEl) {
        teamEl.style.cssText = `
            font-size: 12px;
            margin-top: 5px;
            opacity: 0.8;
        `;
    }

    // 6. 添加到出牌区域
    cardArea.appendChild(emptyInfo);

    // 7. 可选：添加视觉提示（如边框闪烁效果）
    // highlightEmptyPlayer(domId);
}

async function handlePlayCard(auto){
    let selectedCards;

    if(auto){
        // 自动模式：选择最右侧的一张牌
        const allCards = document.querySelectorAll('#myCards .card');

        // 选择最右侧的牌（最后一个元素）
        const rightmostCard = allCards[allCards.length - 1];
        selectedCards = [rightmostCard];
    }else{
        selectedCards = getSelectedCards();
    }

    if(selectedCards.length === 0){
        showHandCardTip('请选择要出的牌!');
        return ;
    }

    // 2. 从DOM元素中提取牌的实际数据（与后端Card类匹配）
    const selectedCardsData = selectedCards.map(cardEl => ({
        name: cardEl.dataset.cardId,  // 从data-cardId获取牌名（对应后端card.name）
        value: parseInt(cardEl.querySelector('.card-value').textContent),  // 提取牌值
        // 若后端需要其他字段（如花色），可从cardEl的属性或结构中提取
        // suit: 例如从card.name解析（如"Heart3"中的"Heart"）
        url: cardEl.dataset.url,
        rank: cardEl.querySelector('.card-rank').textContent,
        suit: cardEl.dataset.cardId.replace(/\d+$/, '')  // 假设name格式为"SuitNumber"（如"Heart3"）
    }));

    const response = await fetch(`/rooms/${roomId}/actor`, {
        method: 'POST',
        headers:{
            'Content-Type': 'application/json', // 声明请求体为 JSON 格式
            'X-CSRF-TOKEN': getCsrfToken()
        },
        body: JSON.stringify({
            currentTurnPlayer: currentUsername,
            type: "ACTOR",
            playerCards: selectedCardsData,
            timestamp: getTime()
        })
    });

    // 先获取响应体的文本内容
    const responseText = await response.text();

    if(responseText === "ok"){
        // 行动后隐藏按钮
        playBtn.style.display = 'none';
        passBtn.style.display = 'none';

        stompClient.send("/app/rooms/" + roomId + "/process-actor", {}, JSON.stringify({
            currentTurnPlayer: currentUsername,
            type: "ACTOR",
            playerCards: selectedCardsData,
            timestamp: getTime()
        }));

    }else{
        // 出牌不合规
        console.log(response);
        showHandCardTip('出牌不合规，请重新选择!');
    }
}

async function handlePassTurn(){
    await stompClient.send("/app/rooms/" + roomId + "/pass",
        {},
        JSON.stringify({
            currentTurnPlayer: currentUsername,
            type: "PASS",
            timestamp: getTime()
        }));

    // 行动后隐藏按钮
    playBtn.style.display = 'none';
    passBtn.style.display = 'none';
}

function handleActor(data){
    // 1. 解析后端返回的回合数据
    const currentPlayer = data.currentTurnPlayer; // 当前出牌玩家
    const nextPlayer = data.nextActor; // 下一回合玩家
    const playedCards = data.playerCards; // 本次出的牌

    clearInterval(turnTimer);
    turnTimer = null;

    // 移除当前玩家已出的牌（如果是自己出牌）
    if (currentPlayer === currentUsername) {
        removePlayedCardsFromHand(playedCards);
    }

    // 2. 显示当前玩家出的牌（在公共区域展示）
    renderPlayedCards(currentPlayer, playedCards);

    // 4. 切换到下一回合：更新UI高亮和操作按钮
    if(currentUsername === nextPlayer){
        showTurnNotification(nextPlayer, false);
    }else{
        highlightCurrentPlayer(nextPlayer);
    }
}

async function handleGameRestart(data){
    // TODO:需要重置页面的一些全局变量

    playBtn.style.display = 'none';
    passBtn.style.display = 'none'; // 显示两个按钮

    removeHighLightHandEmptyPlayer();
    window.gameStatus = true;

    Object.entries(seatPlayerMap).forEach(([playerName, seat]) => {
        const domId = seatDomMap[seat] + "-card";
        const cardsContainer = document.querySelector(`.played-cards-container[data-player= ${domId}`);
        // 清空该玩家之前的出牌（如果需要保留历史记录，可以注释掉这行）
        cardsContainer.innerHTML = '';
    });

    await loadPlayers(seatPlayerMap);

    await wait(5000);
    if(creator_name === currentUsername) {
        // 发牌完毕后 确定轮次
        await stompClient.send("/app/rooms/" + roomId + "/turn", {}, JSON.stringify({
            username: currentUsername,
            timestamp: getTime()
        }));
    }

    await wait(1000);
    if(window.isPlayMusic)
        window.soundManager.playMusic('backMusic');
    handleDealCards(data);
}

async function handlePass(data){
    const currentPlayer = data.currentTurnPlayer; // 当前出牌玩家
    const nextPlayer = data.nextActor; // 下一回合玩家

    clearInterval(turnTimer);
    turnTimer = null;

    // TODO: 显示 "不出" 在相应玩家的牌区
    await renderPlayedCards(currentPlayer, []);

    if(data.first){
        clearPlayedCards();
    }

    // 出牌轮次转换
    if(currentUsername === nextPlayer){
        showTurnNotification(nextPlayer, data.first);
    }else{
        highlightCurrentPlayer(nextPlayer);
    }
}

function handleReady(data){
    showPlayerIsReady(data.relatedName, data.ready);
}


/**
 * 启动出牌计时器
 * @param {string} username - 当前出牌玩家的用户名
 * @param {string} domId - 当前玩家的DOM ID
 * @param {HTMLElement} cardArea - 当前玩家的出牌区容器
 */
function startTurnTimer(username, domId, cardArea){
    let remainingTime = 20; // 初始倒计时20秒

    // 创建倒计时显示元素
    const timerEl = document.createElement('div');
    timerEl.className = 'turn-timer';
    timerEl.innerHTML = `
        <div class="timer-value">${remainingTime}s</div>
    `;
    cardArea.appendChild(timerEl);

    // 6. 启动定时器（每秒更新一次）
    if(!turnTimer){
        turnTimer = window.setInterval(() => {
            remainingTime--;
            timerEl.querySelector('.timer-value').textContent = `${remainingTime}s`;

            // 7. 计时结束：执行自动操作
            if (remainingTime <= 0) {
                window.clearInterval(turnTimer);
                turnTimer = null;

                // 执行自动操作逻辑
                if(currentUsername === username)
                    executeAutoAction(username);
            }
        }, 1000); // 1秒 = 1000毫秒
    }
}

/**
 * 计时结束后执行自动操作
 * @param {string} username - 当前玩家用户名
 * @param {string} domId - 当前玩家DOM ID
 */
function executeAutoAction(username) {
    if(passBtn.style.display === 'none'){
        handlePlayCard(true);
    }else{
        handlePassTurn();
    }
}

async function restoreGameData(){
    const response = await fetch(`/rooms/${roomId}/recover`, {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain', // 声明请求体为 JSON 格式
            'Accept': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken()
        },
        body: username,
    });

    // 解析后端返回的游戏回合数据
    const gameRound = await response.json();
    console.log(gameRound);
    renderMyCards(gameRound.playerCards);
    let actor = gameRound.currentTurnPlayer;
    firstPlayerTag(gameRound.bigGhostPlayerUsername);

    hideButton(playBtn);
    hideButton(readyBtn);
    hideButton(leaveBtn);
    hideButton(startBtn);
    if(readyBtn)
        readyBtn.classList.toggle('ready');

    if(gameRound.lastActor){
        renderPlayedCards(gameRound.lastActor, gameRound.lastPlayerCards);
    }

    if(gameRound.playersSettlementSequenceMap){
        const sequenceMap = gameRound.playersSettlementSequenceMap;
        const teamMap = gameRound.playersTeamMap;

        Object.entries(sequenceMap).forEach(([name, order]) =>{
            showHandEmptyPlayer(name, order, teamMap[name]);
        });
    }

    if(username === actor) { // 当前页面用户轮次
        showTurnNotification(actor, gameRound.first);
    } else { //高亮当前出牌玩家（座位区域）
        highlightCurrentPlayer(actor);
    }
}