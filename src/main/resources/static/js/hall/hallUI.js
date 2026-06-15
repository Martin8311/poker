let cropper;

// 提示消息功能
function showToast(message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    toast.style.position = 'fixed';
    toast.style.bottom = '20px';
    toast.style.left = '50%';
    toast.style.transform = 'translateX(-50%)';
    toast.style.padding = '10px 20px';
    toast.style.backgroundColor = 'rgba(0, 0, 0, 0.7)';
    toast.style.color = 'white';
    toast.style.borderRadius = '4px';
    toast.style.zIndex = '9999';

    document.body.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 3000);
}

// function previewAvatar(event) {
//     const file = event.target.files[0];
//     if (!file) return;
//     const reader = new FileReader();
//     reader.onload = function (e) {
//         const img = document.getElementById('cropImage');
//         img.src = e.target.result;
//         img.onload = function () {
//             if (cropper) {
//                 cropper.destroy();
//             }
//             const cropperArea = document.getElementById('cropperArea');
//             cropperArea.style.display = 'block';
//             const newAvatarPreview = document.getElementById('newAvatarPreview');
//             newAvatarPreview.style.display = 'block';
//             cropper = new Cropper(img, {
//                 aspectRatio: 1, // 裁剪比例为1:1
//                 viewMode: 1, // 限制裁剪框不超过图片范围
//                 preview: '.avatar - img' // 裁剪结果预览区域
//             });
//         };
//     };
//     reader.readAsDataURL(file);
// }


// 头像预览功能
function previewAvatar(event) {
    const file = event.target.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = function(e) {
            const previewContainer = document.getElementById('newAvatarPreview');
            const previewImg = previewContainer.querySelector('img');

            previewImg.src = e.target.result;
            previewContainer.style.display = 'block';
        }
        reader.readAsDataURL(file);
    }
}

/**
 * 更新房间列表UI
 * @param {Array} rooms - 房间数据数组
 */
function updateRoomUI(rooms) {
    const container = document.getElementById('roomContainer');

    // 清空现有房间卡片（保留可能的"暂无房间"提示，但会被后续操作覆盖）
    container.innerHTML = '';

    if (rooms.length === 0) {
        // 没有房间时显示提示
        container.innerHTML = `
            <div class="no-rooms">
                暂无可用房间，创建一个新房间开始游戏吧！
            </div>
        `;
        return;
    }

    // 有房间时，动态生成房间卡片
    rooms.forEach(room => {
        const roomCard = document.createElement('div');
        const roomTypeText = room.publicRoom ? '公开' : '非公开';
        roomCard.className = 'room-card';
        roomCard.innerHTML = `
            <h3>房间 ID: <span>${room.roomId}</span></h3>
            <p>房主: <span>${room.creator.nickname}</span></p>
            <p>信息: <span ></span>${room.info}</p>
            <p>当前人数: <span>${room.players.length}</span>/5</p>
            <p>房间类型: <span>${roomTypeText}</span></p>
            
            <button class="btn btn-primary join-room-btn" data-room-id="${room.roomId}" data-room-password="${room.publicRoom}">
                <i class="fas fa-door-open"></i> 加入房间
            </button>
        `;
        container.appendChild(roomCard);
    });

    // 1. 批量选中所有「加入房间」按钮（通过统一的class名join-room-btn
    const joinButtons = document.querySelectorAll('.join-room-btn');

    // 2. 为每个按钮绑定click事件
    joinButtons.forEach(button => {
        button.addEventListener('click', function() {
            const roomId = this.getAttribute('data-room-id');
            const password = this.getAttribute('data-room-password');


            if(password === 'false'){ // 如果有密码
                createPasswordForm(roomId, this);
            }else{ //公开房间
                handleJoinRoom(roomId, this); // 传递button元素，用于处理加载状态
            }
        });
    });
}

function createPasswordForm(roomId, button){
    // 检查是否已有密码表单，如有则移除
    const existingForm = document.getElementById('passwordFormContainer');
    if (existingForm) {
        existingForm.remove();
    }

    // 创建密码表单容器
    const formContainer = document.createElement('div');
    formContainer.id = 'passwordFormContainer';
    formContainer.className = 'password-form-overlay';

    // 表单HTML结构
    formContainer.innerHTML = `
        <div class="password-form">
     
            <p>房间 ${roomId} 需要密码才能加入</p>
            <form id="roomPasswordForm">
                <div class="form-group">
                    <label for="roomPwdInput">请输入房间密码：</label>
                    <input type="password" id="roomPwdInput" 
                           class="form-control" 
                           placeholder="输入密码" required>
                </div>
                <div class="form-actions">
                    <button type="button" id="cancelJoinBtn" class="btn btn-secondary">取消</button>
                    <button type="submit" class="btn btn-primary">确认加入</button>
                </div>
            </form>
        </div>
    `;

    // 添加到页面
    document.body.appendChild(formContainer);

    // 获取表单元素
    const form = document.getElementById('roomPasswordForm');
    const passwordInput = document.getElementById('roomPwdInput');
    const cancelBtn = document.getElementById('cancelJoinBtn');

    // 自动聚焦到密码输入框
    passwordInput.focus();

    // 取消按钮事件
    cancelBtn.addEventListener('click', () => {
        formContainer.remove();
    });

    // 表单提交事件
    form.addEventListener('submit', (e) => {
        e.preventDefault(); // 阻止表单默认提交

        const inputPassword = passwordInput.value.trim();

        if (!inputPassword) {
            alert('请输入密码');
            passwordInput.focus();
            return;
        }

        // 验证密码（调用后端接口验证）
        verifyPassword(roomId, inputPassword)
            .then(valid => {
                if (valid) {
                    // 密码正确，加入房间
                    formContainer.remove();
                    handleJoinRoom(roomId, button);
                } else {
                    // 密码错误
                    alert('密码错误，请重新输入');
                    passwordInput.value = '';
                    passwordInput.focus();
                }
            })
            .catch(error => {
                console.error('密码验证失败', error);
                alert('验证失败，请重试');
            });
    });

    // 按ESC键关闭表单
    document.addEventListener('keydown', function handleEsc(e) {
        if (e.key === 'Escape') {
            formContainer.remove();
            document.removeEventListener('keydown', handleEsc);
        }
    });
}