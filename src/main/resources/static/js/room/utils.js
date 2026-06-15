/**
 * 获取页面中的CSRF令牌（Spring Security必需）
 * 令牌由Thymeleaf自动注入到页面的meta标签中
 */
function getCsrfToken() {
    const metaTag = document.querySelector('meta[name="_csrf"]');
    if (!metaTag) {
        throw new Error('CSRF令牌未找到，请检查页面配置');
    }
    return metaTag.getAttribute('content');
}

function getTime(){
    return new Date().getTime();
}

// 获取选中的卡牌（返回 DOM 元素数组）
function getSelectedCards() {
    return selectedCards;
}

// 切换卡牌选中状态
function toggleCardSelection(card) {
    if (card.classList.contains('selected')) {
        // 已选中 → 取消选中
        card.classList.remove('selected');
        // 从选中列表中移除
        selectedCards = selectedCards.filter(el => el !== card);
    } else {
        // 未选中 → 选中
        card.classList.add('selected');
        // 添加到选中列表
        selectedCards.push(card);
    }
}

/**
 * 根据Card的name获取图片路径
 * @param {String} cardName Card对象的name字段（如"Spade5"）
 * @returns {String} 图片完整路径
 */
function getCardImageUrl(cardName) {
    // 容错：如果cardName为空，返回默认占位图
    if (!cardName) {
        return '/poker/Background.png'; // 建议准备一张默认占位图
    }
    // 拼接路径：/poker/ + 文件名 + .png
    return `/poker/${cardName}.png`;
}

function escapeHtml(unsafe) {
    if (!unsafe) return '';
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
} // 辅助函数：防止XSS攻击，转义HTML特殊字符（必须在handleUserChat之前定义）

function formatTime(date) {
    // 确保传入的是有效的Date对象
    if (!(date instanceof Date) || isNaN(date.getTime())) {
        return "";
    }

    // 格式化选项：24小时制，带分钟，如 "14:30" 或 "9:05"
    const options = {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false  // 使用24小时制
    };

    return date.toLocaleTimeString('zh-CN', options);
} // 辅助函数：格式化时间显示（在handleUserChat之前定义）

async function getCreatorName(){
    return new Promise((resolve, reject) => {
        fetch(`/room/${roomId}/get_creator_name`, {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain',
                'X-CSRF-TOKEN': getCsrfToken()
            }
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error(`请求失败：${response.status}`);
                }
                return response.text(); // 解析字符串响应
            })
            .then(creator_name => {
                // 成功时，通过resolve返回昵称
                resolve(creator_name || '未知用户');
            })
            .catch(error => {
                // 失败时，通过reject返回错误
                console.error('获取昵称失败：', error);
                reject(error);
            });
    });
}

/**
 * 获取玩家完整信息（昵称、总场数、胜场数、胜率）
 * @param {String} username - 玩家用户名
 * @returns {Promise<Object>} UserInfo对象（包含nickName, total_game, win_game, win_rate）
 */
async function getUserInfo(username) {
    return new Promise((resolve, reject) => {
        // 发起POST请求，后端接口为获取用户信息的新接口
        fetch(`/room/${roomId}/get_userinfo`, {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain', // 发送用户名字符串
                'Accept': 'application/json',  // 与后端 produces 一致
                'X-CSRF-TOKEN': getCsrfToken() // 从工具函数获取CSRF令牌
            },
            body: username // 请求体为用户名
        }).then(response => {
                return response.json();
            }).then(userInfo =>{
            // 验证返回的数据结构
            const defaultInfo = {
                nickName: '未知用户',
                score: 0,
                total_game: 0,
                win_game: 0,
                win_rate: '0.00%'
            };
            // 合并默认值和返回数据，确保字段存在
            resolve({ ...defaultInfo, ...userInfo });
        })
    });
}

/**
 * 生成以当前座位为起点的轮转顺序
 * @param {String} currentSeat 当前玩家的座位（如"SEAT_4"）
 * @returns {Array} 轮转后的座位顺序（如当前为SEAT_4时，返回["SEAT_4", "SEAT_5", "SEAT_1", "SEAT_2", "SEAT_3"]）
 */
function generateRotatedOrder(currentSeat) {
    const currentIndex = seatIndexMap[currentSeat]; // 当前座位的索引
    const rotated = [];

    // 从当前索引开始，循环5次（覆盖所有座位）
    for (let i = 0; i < 5; i++) {
        // 计算当前位置的索引（取模实现循环）
        const index = (currentIndex + i) % 5;
        rotated.push(indexToSeat[index]);
    }

    return rotated;
}

/**
 * 将轮转顺序映射到前端视觉位置（从右到左）
 * @param {Array} rotatedOrder 轮转后的座位顺序
 * @param {String} currentSeat 当前玩家的座位
 * @returns {Object} 座位→前端DOM ID的映射关系
 */
function mapToVisualPositions(rotatedOrder, currentSeat) {
    // 1. 当前玩家固定在下方（currentPlayer）
    seatDomMap[currentSeat] = "currentPlayer";

    // 2. 其他玩家按“从右到左”映射到visualPositions
    // 轮转顺序中，从当前玩家的下一个座位开始取4个
    for (let i = 1; i < 5; i++) { // i=1到4（跳过当前玩家）
        const seat = rotatedOrder[i];
        const domId = visualPositions[i - 1]; // 对应到从右到左的位置
        seatDomMap[seat] = domId;
    }

    return seatDomMap;
}

// 定义一个等待函数
function wait(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// 准备/取消准备
function sendReadyRequest(isReady){
    stompClient.send("/app/room/" + roomId + "/isReady", {}, JSON.stringify({
        relatedName: currentUsername,
        type: "READY",
        timestamp: getTime(),
        ready: isReady
    }));
}

function showButton(buttonId){
    if(!buttonId)
        return;
    buttonId.style.display = 'block';
}

function hideButton(buttonId){
    if(!buttonId)
        return;
    buttonId.style.display = 'none';
}