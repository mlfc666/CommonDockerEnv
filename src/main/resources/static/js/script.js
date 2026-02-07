let timeLeft = 0;
let timerInterval = null;

// 页面加载完成后检查环境状态
window.onload = checkCurrentEnv;

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
    const envNameLabel = document.getElementById('env-name');
    const container = document.getElementById('terminal-container');

    if (data && data.sid) {
        envNameLabel.innerText = data.sid;

        // 1. 创建 iframe
        const iframe = document.createElement('iframe');
        iframe.src = data.url;
        iframe.setAttribute('scrolling', 'no');
        iframe.setAttribute('allow', 'clipboard-read; clipboard-write');

        container.innerHTML = '';
        container.appendChild(iframe);

        // 2. 核心：定义一个高强度的“强制对齐”函数
        const forceFit = () => {
            if (!iframe.contentWindow) return;
            console.log("正在强制终端对齐...");

            // 触发内部窗口的 resize 事件，这是 ttyd 重新计算行数的唯一标准
            iframe.contentWindow.dispatchEvent(new Event('resize'));

            // 额外保险：如果内部有 xterm 实例，尝试聚焦
            iframe.contentWindow.focus();
            iframe.focus();
        };

        // 3. 监听容器尺寸变化 (解决 Grid 布局延迟问题)
        const observer = new ResizeObserver(() => {
            forceFit();
        });
        observer.observe(container);

        // 4. 多阶段校准计划（应对不同的网络延迟阶段）
        iframe.onload = () => {
            // 阶段1：加载瞬间
            forceFit();

            // 阶段2：等待内部 WebSocket 握手可能引起的布局变动
            setTimeout(forceFit, 500);

            // 阶段3：最终保底校准
            setTimeout(forceFit, 2000);
        };

        // 5. 交互：点击容器时也触发校准和聚焦
        container.onclick = forceFit;

        btn.innerText = "销毁容器环境";
        btn.className = "action-btn btn-destroy";
        btn.onclick = destroyEnv;
        startTimer(parseInt(data.timeout));
    } else {
        // ... (此处逻辑保持你原来的 updateUI(null) 部分不变)
        envNameLabel.innerText = "CommonEnv 容器";
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
        const data = await res.json();
        if (data.sid) updateUI(data);
    } catch (e) {
        alert("创建失败: " + e.message);
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
        await fetch('/api/v1/destroy', { method: 'POST' });
        updateUI(null);
    } catch (e) {
        alert("销毁失败");
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
            updateUI(null);
            return;
        }
        timeLeft--;
        const m = Math.floor(timeLeft / 60);
        const s = timeLeft % 60;
        document.getElementById('timer').innerText = `剩余时间: ${m}:${s < 10 ? '0'+s : s}`;
    }, 1000);
}