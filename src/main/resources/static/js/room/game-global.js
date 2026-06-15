window.stompClient = null;

const playBtn = document.getElementById('playBtn');
const passBtn = document.getElementById('passBtn');
const leaveBtn = document.getElementById('leaveRoomBtn');
const readyBtn = document.getElementById('readyBtn');
const startBtn = document.getElementById('startGameBtn');
const restartBtn = document.getElementById('startGameRestartBtn');
const presetChatBtn = document.getElementById('presetChatBtn');
const presetChatList = document.getElementById('presetChatList');
const presetChatItems = document.querySelectorAll('.preset-chat-item');
const totalScoreBoardBtn = document.getElementById('totalScoreBoardBtn');
const scoreBoardModal = document.getElementById('scoreBoardModal');
const closeButtons = document.querySelectorAll('.close-modal');
const scoreTableBody = document.getElementById('scoreTableBody');

window.scoreMap = null;
window.totalScoreMap = null;

window.currentUserId = "";    // 当前登录用户 ID
window.currentUserNickname = ""; // 当前登录用户昵称
window.currentUsername = "";  // 当前页面的用户username
window.players = [];          // 房间内所有玩家列表

window.hasSentLeaveRequest = false;
window.selectedCards = []; //选中牌
window.gameStatus = false;

// 全局存储座位→DOM的映射关系
window.seatPlayerMap = {};
window.seatDomMap = {};

window.rotatedOrder = {};
window.currentSeat = '';

window.creator_name = "";

window.turnTimer = null;

// 座位类型与索引的映射（固定）
window.seatIndexMap = {
    "SEAT_1": 0,
    "SEAT_2": 1,
    "SEAT_3": 2,
    "SEAT_4": 3,
    "SEAT_5": 4
};

// 前端视觉位置（从右到左的顺序）
window.visualPositions = [
    "player1",  // 最右侧位置
    "player2",  // 右上位置
    "player3",  // 左上位置
    "player4"   // 最左侧位置
];

window.positions = [
    "currentPlayer",
    "player1",  // 最右侧位置
    "player2",  // 右上位置
    "player3",  // 左上位置
    "player4"   // 最左侧位置
];

// 索引对应的座位类型（用于反向查找）
window.indexToSeat = ["SEAT_1", "SEAT_2", "SEAT_3", "SEAT_4", "SEAT_5"];


// TODO: Release版请将默认值设置为 true
window.isPlaySound = true;
window.isPlayMusic = true;

// 音效管理器(单例模式)
window.soundManager = (function(){
    // 1. 定义音效映射：key=事件名，value=音频路径（对应static/sound下的文件）
    const soundMap = {
        backMusic: '/sound/background.mp3',
        gameStart: '/sound/game_start.wav',
        cardPlay: '/sound/card_play.mp3',
        pass: '/sound/pass.mp3',
        error: '/sound/error.mp3'
    }

    // 2. 缓存Audio实例（预加载，避免播放时卡顿）
    const audioInstances = {};

    // 3. 预加载所有音效
    function preloadAllSounds(){
        Object.keys(soundMap).forEach(soundKey => {
            const audio = new Audio(soundMap[soundKey]);
            audio.load();
            audioInstances[soundKey] = audio;
            audio.onerror = function (){
                console.error(`音效加载失败：${soundMap[soundKey]}`);
            }
        });
    }

    // 4. 播放指定音效（核心方法）
    function playSound(soundKey, options = {}){
        if(!window.isPlaySound)
            return;

        if(!audioInstances[soundKey]){
            console.error(`不存在该音效：${soundKey}`);
            return;
        }

        const audio = audioInstances[soundKey];
        const {volume = 0.7, loop = false} = options;

        audio.currentTime = 0;
        audio.volume = volume;
        audio.loop = loop;

        audio.play();
    }

    function playMusic(soundKey, options = {}){
        if(!window.isPlayMusic)
            return;

        if(!audioInstances[soundKey]){
            console.error(`不存在该音效：${soundKey}`);
            return;
        }

        const audio = audioInstances[soundKey];
        const {volume = 0.7, loop = true} = options;

        audio.currentTime = 0;
        audio.volume = volume;
        audio.loop = loop;

        audio.play();
    }

    // 5. 暂停指定音效
    function pauseSound(soundKey) {
        if (audioInstances[soundKey]) {
            audioInstances[soundKey].pause();
        }
    }

    return {
        preloadAllSounds, // 预加载所有音效
        playSound,        // 播放指定音效
        pauseSound,        // 暂停指定音效
        playMusic
    };

})();