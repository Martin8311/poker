/**
 * 刷新房间列表
 */
function refreshRooms() {
    // 发起GET请求获取最新房间列表
    fetch('/hall/getRoomList',{
        method: 'POST',
        headers: {
            'X-CSRF-TOKEN': getCsrfToken(), // 必须：Spring Security的CSRF令牌
            'Content-Type': 'application/json'
        }
    }) // 假设后端提供这个接口返回房间数据
        .then(response => {
            if (!response.ok) {
                throw new Error('网络响应不正常');
            }
            return response.json();
        })
        .then(rooms => {
            updateRoomUI(rooms);
        })
        .catch(error => {
            console.error('刷新房间失败:', error);
            // 可以在这里添加用户友好的错误提示
        });
}

/**
 * 验证房间密码（与后端交互）
 * @param {string} roomId - 房间ID
 * @param {string} password - 输入的密码
 * @returns {Promise<boolean>} - 验证结果
 */
function verifyPassword(roomId, password) {

    const formData = new FormData();
    formData.append('roomId', roomId);
    formData.append('password', password);

    return fetch(`/room/verify-password`, {
        method: 'POST',
        headers: {
            'X-CSRF-TOKEN': getCsrfToken()
        },
        body: formData
    })
        .then(response => {
            if (!response.ok) {
                return false;
            }
            return response.json().then(data => data.valid);
        })
        .catch(() => false);
}

document.addEventListener('DOMContentLoaded', function() {
    // 获取当前用户头像（假设后端通过属性传递）

    const currentAvatarUrl = iconUrl
    document.getElementById('currentAvatar').src = currentAvatarUrl;
    document.getElementById('modalCurrentAvatar').src = currentAvatarUrl;
});

// 等待页面DOM完全加载后再执行（避免找不到按钮）
document.addEventListener('DOMContentLoaded', function() {

    check_reconnect();

    refreshRooms();

    setInterval(refreshRooms, 5000);

    // 模态框控制
    const modal = document.getElementById('profileModal');
    const editBtn = document.getElementById('editProfileBtn');
    const closeButtons = document.querySelectorAll('.close-modal');

    editBtn.addEventListener('click', function() {
        modal.style.display = 'flex';
    });

    closeButtons.forEach(button => {
        button.addEventListener('click', function() {
            modal.style.display = 'none';
        });
    });

    // 点击模态框外部关闭
    window.addEventListener('click', function(event) {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });

    // 表单提交处理
    document.getElementById('profileForm').addEventListener('submit', async function(e) {
        e.preventDefault();

        // 创建FormData对象
        const formData = new FormData(this);

        try {
            // 发送更新请求
            const response = await fetch('/user/profile/update', {
                method: 'POST',
                body: formData,
                headers: {
                    'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
                }
            });

            const result = await response.json();

            if (result.success) {
                // 更新页面显示
                const newNickname = document.getElementById('newNickname').value;
                document.getElementById('nickname').textContent = newNickname;

                // 如果上传了新头像，更新头像显示
                if (document.getElementById('avatarFile').files[0]) {
                    const newAvatarUrl = result.avatarUrl + '?t=' + new Date().getTime(); // 加时间戳避免缓存
                    document.getElementById('hall-player-icon').src = newAvatarUrl;
                    document.getElementById('modalCurrentAvatar').src = newAvatarUrl;
                }

                // 关闭模态框并提示成功
                modal.style.display = 'none';
                showToast(result.message);
            } else {
                showToast('更新失败：' + result.error);
            }
        } catch (error) {
            console.error('更新资料失败：', error);
            showToast('更新失败，请重试');
        }
    });


});

document.addEventListener('DOMContentLoaded', function() {
    // 1. 获取DOM元素
    const createBtn = document.getElementById('createRoomBtn');
    const modal = document.getElementById('createRoomModal');
    const cancelBtn = document.getElementById('cancelCreateBtn');
    const submitBtn = document.getElementById('submitCreateBtn');
    const form = document.getElementById('createRoomForm');
    const roomDescInput = document.getElementById('roomDesc');
    const roomPwdInput = document.getElementById('roomPwd');

    // 2. 打开模态框（点击创建房间按钮）
    if (createBtn) {
        createBtn.addEventListener('click', function() {
            modal.style.display = 'block'; // 显示模态框
            roomDescInput.focus(); // 聚焦到简介输入框，提升用户体验
        });
    }

    // 3. 关闭模态框（点击关闭按钮、取消按钮、遮罩层）
    function closeModal() {
        modal.style.display = 'none'; // 隐藏模态框
        form.reset(); // 重置表单内容（可选，避免下次打开残留旧数据）
    }

    // 点击取消按钮
    cancelBtn.addEventListener('click', closeModal);
    // 点击遮罩层（点击模态框背景关闭）
    modal.querySelector('.modal-overlay').addEventListener('click', closeModal);

    // 4. 表单提交（点击确认创建按钮）
    submitBtn.addEventListener('click', function() {
        // 4.1 表单验证
        const roomDesc = roomDescInput.value.trim();
        const roomPwd = roomPwdInput.value.trim(); // 密码可选，无需强制验证

        if (roomDesc.length > 200) {
            alert('房间简介不能超过200字！');
            roomDescInput.value = roomDesc.substring(0, 200); // 截断超出部分
            roomDescInput.focus();
            return;
        }

        // 4.2 构造请求数据
        const roomData = {
            roomDesc: roomDesc ? roomDesc : nickname + "的游戏",
            roomPwd: roomPwd || null // 无密码时传null（后端可处理为公开房间）
        };

        createRoom(roomData);
    });

});


/**
 * 加入房间的核心逻辑
 * @param {string} roomId - 要加入的房间ID
 * @param {HTMLButtonElement} button - 被点击的按钮（用于处理加载状态）
 */
function handleJoinRoom(roomId, button) {
    // 保存按钮原始状态（文本+禁用状态），用于失败后恢复
    const originalHTML = button.innerHTML;
    const originalDisabled = button.disabled;

    // 1. 点击后立即更新按钮状态：显示加载中+禁用
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 加入中...';
    button.disabled = true;

    // 2. 发送POST请求到加入房间接口
    fetch(`/room/join/${roomId}`, {
        method: 'POST',
        headers: {
            'X-CSRF-TOKEN': getCsrfToken(), // 必须：Spring Security的CSRF令牌
            'Content-Type': 'application/json'
        }
    })
        .then(response => {
            if (response.ok) {
                // 3. 成功：跳转到房间页面
                window.location.href = `/room/${roomId}`;
            } else {
                // 4. 失败：解析错误信息并抛出
                return response.text().then(errorText => {
                    throw new Error(errorText || '加入失败：房间可能已满或不存在');
                });
            }
        })
        .catch(error => {
            // 5. 异常处理：显示错误提示，恢复按钮状态
            alert(error.message); // 可替换为更美观的页面内提示
            button.innerHTML = originalHTML;
            button.disabled = originalDisabled;
        });
}

function createRoom(roomData) {
    // 获取DOM元素
    const createBtn = document.getElementById('createRoomBtn');
    const loading = document.getElementById('loadingIndicator');
    const errorMsg = document.getElementById('errorMsg');

    // 防止重复点击：禁用按钮+显示加载状态
    createBtn.disabled = true;
    loading.style.display = 'inline-block';
    errorMsg.style.display = 'none'; // 隐藏之前的错误提示

    const formData = new FormData();
    formData.append('roomDesc', roomData.roomDesc); // 对应后端@RequestParam("roomDesc")
    formData.append('roomPwd', roomData.roomPwd);   // 对应后端@RequestParam("roomPwd")

    // 发送POST请求到创建房间接口
    fetch('/room/create', {
        method: 'POST',
        headers: {
            // 关键：Spring Security需要CSRF令牌验证POST请求
            'X-CSRF-TOKEN': getCsrfToken(),
        },
        // 若接口需要参数，可在这里添加（当前创建房间无需额外参数，仅需用户认证）
        body: formData,
    })
        .then(response => {
            // 处理HTTP响应状态
            if (response.ok) {
                // 响应成功：获取重定向URL并跳转
                const redirectUrl = response.url; // 后端返回的房间页面URL
                window.location.href = redirectUrl;
            } else {
                // 响应失败：解析错误信息
                return response.text().then(errorText => {
                    throw new Error(errorText || '创建房间失败，请重试');
                });
            }
        })
        .catch(error => {
            // 捕获请求异常：显示错误提示
            errorMsg.textContent = error.message;
            errorMsg.style.display = 'inline-block';
        })
        .finally(() => {
            // 无论成功/失败：恢复按钮状态+隐藏加载
            createBtn.disabled = false;
            loading.style.display = 'none';
        });
}

async function check_reconnect(){
    try {
        const response = await fetch(`/hall/reconnect`, {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: username,
        });

        const roomId = await response.text();

        // 如果存在未退出的房间
        if (roomId && roomId !== "none") {
            // 创建弹窗元素
            const modal = document.createElement('div');
            modal.className = 'reconnect-modal fixed inset-0 bg-black bg-opacity-70 flex items-center justify-center z-50';
            modal.innerHTML = `
                <div class="bg-white rounded-lg p-6 w-80 text-center">
                    <h3 class="text-xl font-bold mb-4">检测到已加入的房间</h3>
                    <p class="mb-6">您有一个房间(${roomId})因意外断开连接，是否返回？</p>
                    <button id="returnRoomBtn" class="bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 mr-3">
                        返回房间
                    </button>
                    <button id="cancelReturnBtn" class="bg-gray-200 px-6 py-2 rounded hover:bg-gray-300">
                        退出
                    </button>
                </div>
            `;
            document.body.appendChild(modal);

            // 返回房间按钮事件
            document.getElementById('returnRoomBtn').addEventListener('click', () => {
                // 跳转到房间页面，这里假设进入房间的URL格式为/room/{roomId}
                window.location.href = `/room/${roomId}`;
                document.body.removeChild(modal);
            });

            // 取消按钮事件
            document.getElementById('cancelReturnBtn').addEventListener('click', () => {
                fetch(`/rooms/${roomId}/giveUpRecover`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-TOKEN': getCsrfToken()
                    },
                    body: JSON.stringify({
                        type: 'PLAYER_LEAVE',
                        relatedName: username,
                        content: null,
                        timestamp: getTime()
                    })
                });

                document.body.removeChild(modal);
            });
        }
    } catch (error) {
        console.error('重连检测失败:', error);
        // 可以添加错误处理，如重试机制
    }
}

/**
 * 加载并渲染排行榜（TopN + 我的排名）
 * 均为 GET 请求，同源 fetch 自动携带登录 cookie，无需 CSRF
 */
async function loadLeaderboard() {
    try {
        const [topRes, meRes] = await Promise.all([
            fetch('/leaderboard/top?n=10'),
            fetch('/leaderboard/me')
        ]);

        if (!topRes.ok) {
            throw new Error('排行榜加载失败');
        }

        const top = await topRes.json();
        const me = meRes.ok ? await meRes.json() : null;
        renderLeaderboard(top, me);
    } catch (error) {
        console.error('加载排行榜失败:', error);
        showToast('排行榜加载失败，请稍后重试');
    }
}

// 排行榜模态框控制
document.addEventListener('DOMContentLoaded', function () {
    const leaderboardBtn = document.getElementById('leaderboardBtn');
    const leaderboardModal = document.getElementById('leaderboardModal');
    const refreshBtn = document.getElementById('refreshLeaderboardBtn');
    const closeButtons = document.querySelectorAll('.close-leaderboard');

    if (!leaderboardBtn || !leaderboardModal) {
        return;
    }

    // 打开：显示模态框并加载数据
    leaderboardBtn.addEventListener('click', function () {
        leaderboardModal.style.display = 'flex';
        loadLeaderboard();
    });

    // 关闭按钮
    closeButtons.forEach(btn => {
        btn.addEventListener('click', function () {
            leaderboardModal.style.display = 'none';
        });
    });

    // 点击模态框外部关闭
    window.addEventListener('click', function (event) {
        if (event.target === leaderboardModal) {
            leaderboardModal.style.display = 'none';
        }
    });

    // 刷新
    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadLeaderboard);
    }
});








