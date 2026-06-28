(function () {
    const usernameInput = document.getElementById('resetUsername');
    const phoneInput = document.getElementById('resetPhoneNumber');
    const promptEl = document.getElementById('resetHumanPrompt');
    const contentEl = document.getElementById('resetHumanContent');
    const answerInput = document.getElementById('resetHumanAnswer');
    const verifyHumanBtn = document.getElementById('resetVerifyHumanBtn');
    const refreshHumanBtn = document.getElementById('resetRefreshHumanBtn');
    const sendCodeBtn = document.getElementById('resetSendCodeBtn');
    const codeInput = document.getElementById('resetCode');
    const passwordInput = document.getElementById('resetNewPassword');
    const confirmInput = document.getElementById('resetConfirmPassword');
    const form = document.getElementById('forgotPasswordForm');
    const messageEl = document.getElementById('resetMessage');
    const errorEl = document.getElementById('resetError');

    let challenge = null;
    let humanToken = '';
    let challengeUsername = '';

    function csrfToken() {
        const meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.getAttribute('content') : '';
    }

    function showMessage(text, success) {
        messageEl.hidden = !success;
        errorEl.hidden = success;
        const target = success ? messageEl : errorEl;
        target.textContent = text;
    }

    function isValidPhone(phone) {
        return /^1[3-9]\d{9}$/.test(phone);
    }

    async function postForm(url, values) {
        const formData = new FormData();
        Object.entries(values).forEach(([key, value]) => formData.append(key, value || ''));
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'X-CSRF-TOKEN': csrfToken() },
            body: formData
        });
        return response.json();
    }

    function resetHumanState() {
        challenge = null;
        humanToken = '';
        challengeUsername = '';
        contentEl.innerHTML = '';
        answerInput.value = '';
        answerInput.disabled = false;
        answerInput.readOnly = false;
        verifyHumanBtn.disabled = false;
        verifyHumanBtn.textContent = '完成人机验证';
    }

    async function loadHumanChallenge() {
        const username = usernameInput.value.trim();
        if (!username) {
            showMessage('请先输入用户名', false);
            usernameInput.focus();
            return;
        }

        resetHumanState();
        promptEl.textContent = '人机验证加载中...';
        try {
            const data = await postForm('/forgot-password/human-verification/challenge', { username });
            if (!data.success || !data.challenge) {
                throw new Error(data.error || '人机验证加载失败');
            }
            challenge = data.challenge;
            challengeUsername = username;
            renderHumanChallenge(challenge);
        } catch (error) {
            promptEl.textContent = '人机验证加载失败';
            showMessage(error.message || '人机验证加载失败', false);
        }
    }

    function renderHumanChallenge(item) {
        promptEl.textContent = item.prompt || '请完成人机验证';
        contentEl.innerHTML = '';
        answerInput.value = '';

        if (item.type === 'SLIDER') {
            const track = document.createElement('div');
            track.className = 'human-slider-track';
            track.style.width = `${item.trackWidth || 260}px`;

            const target = document.createElement('span');
            target.className = 'human-slider-target';
            target.style.left = `${item.targetX || 0}px`;
            track.appendChild(target);

            const range = document.createElement('input');
            range.type = 'range';
            range.min = '0';
            range.max = String(Math.max(1, (item.trackWidth || 260) - 36));
            range.value = '0';
            range.className = 'human-slider-range';
            range.addEventListener('input', () => {
                answerInput.value = range.value;
            });

            contentEl.appendChild(track);
            contentEl.appendChild(range);
            answerInput.value = '0';
            answerInput.readOnly = true;
            answerInput.placeholder = '拖动滑块完成验证';
        } else {
            const image = document.createElement('div');
            image.className = 'human-image-code';
            image.textContent = item.expression || '';
            contentEl.appendChild(image);
            answerInput.readOnly = false;
            answerInput.placeholder = '请输入算式结果';
            answerInput.focus();
        }
    }

    async function verifyHuman() {
        const username = usernameInput.value.trim();
        if (!challenge || username !== challengeUsername) {
            await loadHumanChallenge();
            return false;
        }
        const answer = answerInput.value.trim();
        if (!answer) {
            showMessage('请先完成人机验证', false);
            return false;
        }

        verifyHumanBtn.disabled = true;
        try {
            const data = await postForm('/forgot-password/human-verification/verify', {
                username,
                challengeId: challenge.challengeId,
                answer
            });
            if (!data.success) {
                throw new Error(data.error || '人机验证失败');
            }
            humanToken = data.humanToken || '';
            verifyHumanBtn.textContent = '已通过';
            answerInput.disabled = true;
            showMessage('人机验证已通过，可以发送短信验证码', true);
            return true;
        } catch (error) {
            showMessage(error.message || '人机验证失败', false);
            await loadHumanChallenge();
            return false;
        } finally {
            if (!humanToken) {
                verifyHumanBtn.disabled = false;
            }
        }
    }

    function startCountdown(button, seconds) {
        let left = Math.max(1, Number(seconds || 60));
        button.disabled = true;
        button.textContent = `${left}s 后重试`;
        const timer = setInterval(() => {
            left -= 1;
            if (left <= 0) {
                clearInterval(timer);
                button.disabled = false;
                button.textContent = '发送验证码';
            } else {
                button.textContent = `${left}s 后重试`;
            }
        }, 1000);
    }

    refreshHumanBtn.addEventListener('click', loadHumanChallenge);
    verifyHumanBtn.addEventListener('click', verifyHuman);
    usernameInput.addEventListener('change', resetHumanState);

    sendCodeBtn.addEventListener('click', async function () {
        const username = usernameInput.value.trim();
        const phoneNumber = phoneInput.value.trim();
        if (!username) {
            showMessage('请输入用户名', false);
            usernameInput.focus();
            return;
        }
        if (!isValidPhone(phoneNumber)) {
            showMessage('请输入有效的 11 位手机号', false);
            phoneInput.focus();
            return;
        }
        if (!humanToken && !(await verifyHuman())) {
            return;
        }

        sendCodeBtn.disabled = true;
        try {
            const data = await postForm('/forgot-password/send-code', {
                username,
                phoneNumber,
                humanToken
            });
            if (!data.success) {
                throw new Error(data.error || '验证码发送失败');
            }
            humanToken = '';
            const debugText = data.debugCode ? ` 开发验证码：${data.debugCode}` : '';
            showMessage(`验证码已发送至 ${data.maskedPhoneNumber || phoneNumber}，${data.ttlMinutes || 5} 分钟内有效。${debugText}`, true);
            startCountdown(sendCodeBtn, data.cooldownSeconds || 60);
        } catch (error) {
            humanToken = '';
            showMessage(error.message || '验证码发送失败', false);
            await loadHumanChallenge();
            sendCodeBtn.disabled = false;
        }
    });

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        const username = usernameInput.value.trim();
        const phoneNumber = phoneInput.value.trim();
        const code = codeInput.value.trim();
        const newPassword = passwordInput.value.trim();
        const confirmPassword = confirmInput.value.trim();

        if (!/^\d{6}$/.test(code)) {
            showMessage('请输入 6 位数字验证码', false);
            codeInput.focus();
            return;
        }
        if (newPassword.length < 6 || newPassword.length > 64) {
            showMessage('密码长度需要在 6 到 64 位之间', false);
            passwordInput.focus();
            return;
        }
        if (newPassword !== confirmPassword) {
            showMessage('两次输入的新密码不一致', false);
            confirmInput.focus();
            return;
        }

        try {
            const data = await postForm('/forgot-password/reset', {
                username,
                phoneNumber,
                code,
                newPassword
            });
            if (!data.success) {
                throw new Error(data.error || '密码重置失败');
            }
            showMessage(data.message || '密码已重置', true);
            setTimeout(() => {
                window.location.href = '/login';
            }, 1200);
        } catch (error) {
            showMessage(error.message || '密码重置失败', false);
        }
    });
})();
