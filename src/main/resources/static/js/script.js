let timeLeft = 0;
let timerInterval = null;

// --- 新增：全局消息提示函数 ---
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerText = message;

    container.appendChild(toast);

    // 3秒后自动消失
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

window.onload = checkCurrentEnv;

function repairDisplay() {
    const iframe = document.querySelector('#terminal-container iframe');
    if (iframe) {
        showToast("正在尝试校准终端布局...", "info");
        iframe.style.width = '98%';
        setTimeout(() => {
            iframe.style.width = '100%';
            iframe.focus();
            try {
                if (iframe.contentWindow) {
                    iframe.contentWindow.dispatchEvent(new Event('resize'));
                }
            } catch(e) {}
            showToast("校准指令已发送", "success");
        }, 150);
    }
}

async function checkCurrentEnv() {
    try {
        const res = await fetch('/api/v1/check');
        const data = await res.json();
        if (data.active) updateUI(data);
    } catch (e) {
        console.log("无活跃环境");
    }
}

function updateUI(data) {
    const btn = document.getElementById('main-btn');
    const repairBtn = document.getElementById('repair-btn');
    const envNameLabel = document.getElementById('env-name');
    const container = document.getElementById('terminal-container');

    if (data && data.sid) {
        envNameLabel.innerText = data.sid;
        repairBtn.style.display = "block";

        container.innerHTML = `<iframe src="${data.url}" scrolling="no" allow="clipboard-read; clipboard-write"></iframe>`;
        const iframe = container.querySelector('iframe');

        iframe.onload = function() {
            setTimeout(() => {
                iframe.style.width = '99%';
                setTimeout(() => {
                    iframe.style.width = '100%';
                    iframe.focus();
                }, 50);
            }, 300);
        };

        btn.innerText = "销毁容器环境";
        btn.className = "action-btn btn-destroy";
        btn.onclick = destroyEnv;
        startTimer(parseInt(data.timeout));
    } else {
        envNameLabel.innerText = "CommonEnv 容器";
        repairBtn.style.display = "none";
        container.innerHTML = `<div class="placeholder"><p>环境已就绪</p><p style="font-size: 0.8em;">点击按钮开始</p></div>`;
        document.getElementById('timer').innerText = "剩余时间: --:--";
        btn.innerText = "创建容器环境";
        btn.className = "action-btn btn-create";
        btn.onclick = spawnEnv;
        clearInterval(timerInterval);
    }
}

async function spawnEnv() {
    const btn = document.getElementById('main-btn');
    btn.disabled = true;
    btn.innerText = "正在创建...";

    try {
        const res = await fetch('/api/v1/spawn', { method: 'POST' });

        // 处理非 200 状态码 (包括 429, 403, 500 等)
        if (!res.ok) {
            let errorMsg = "服务器资源不足或达到创建上限";
            try {
                const errData = await res.json();
                // 核心修复：同时兼容后端返回的 'error' 字段或 'message' 字段
                errorMsg = errData.error || errData.message || errorMsg;
            } catch (jsonErr) {
                // 如果后端返回的不是 JSON 格式
                console.error("解析错误响应失败", jsonErr);
            }

            showToast(errorMsg, "error"); // 这里的弹窗就会显示 "当前人数已满"
            return; // 结束逻辑，不再向下执行
        }

        const data = await res.json();
        if (data.sid) {
            showToast("环境创建成功！", "success");
            updateUI(data);
        }
    } catch (e) {
        console.error("请求崩溃:", e);
        showToast("网络请求异常，请检查网络连接", "error");
    } finally {
        btn.disabled = false;
        // 如果是销毁状态变回来的，确保文字正确
        const checkRes = await fetch('/api/v1/check');
        const checkData = await checkRes.json();
        if (!checkData.active) {
            btn.innerText = "创建容器环境";
        }
    }
}

async function destroyEnv() {
    if (!confirm("确定要销毁环境吗？")) return;
    const btn = document.getElementById('main-btn');
    btn.disabled = true;
    try {
        const res = await fetch('/api/v1/destroy', { method: 'POST' });
        if (res.ok) {
            showToast("环境已安全销毁", "info");
            updateUI(null);
        }
    } catch (e) {
        showToast("销毁请求失败", "error");
    } finally {
        btn.disabled = false;
    }
}

function startTimer(seconds) {
    if (seconds <= 0) return;
    clearInterval(timerInterval);
    timeLeft = seconds;
    timerInterval = setInterval(() => {
        if (timeLeft <= 0) {
            clearInterval(timerInterval);
            showToast("环境使用时间已到，正在自动销毁...", "warning");
            updateUI(null);
            return;
        }
        timeLeft--;
        const m = Math.floor(timeLeft / 60);
        const s = timeLeft % 60;
        document.getElementById('timer').innerText = `剩余时间: ${m}:${s < 10 ? '0'+s : s}`;
    }, 1000);
}