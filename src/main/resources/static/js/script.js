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

        // 显式处理错误状态码
        if (!res.ok) {
            const errData = await res.json();
            // 假设后端返回错误信息如 { "message": "达到容器限制" }
            const errorMsg = errData.message || "服务器资源不足或达到容器创建上限";
            showToast("创建失败: " + errorMsg, "error");
            return;
        }

        const data = await res.json();
        if (data.sid) {
            showToast("环境创建成功！", "success");
            updateUI(data);
        }
    } catch (e) {
        showToast("网络请求异常，请检查网络连接", "error");
        updateUI(null);
    } finally {
        btn.disabled = false;
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