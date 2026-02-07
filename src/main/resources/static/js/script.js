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

        // 插入框架并禁止自带滚动
        container.innerHTML = `<iframe src="${data.url}" scrolling="no" allow="clipboard-read; clipboard-write"></iframe>`;
        const iframe = container.querySelector('iframe');

        // 监听加载完成事件以执行校准
        iframe.onload = function() {
            console.log("终端已加载，准备执行布局校准...");

            // 核心修复逻辑：校准函数
            const calibrate = () => {
                // 强制改变高度（减少1px再还原）以触发 iframe 内部 ttyd 的 resize 监听
                iframe.style.height = 'calc(100% - 1px)';

                setTimeout(() => {
                    iframe.style.height = '100%';
                    iframe.focus();
                    // 尝试向内部发送标准的窗口 resize 事件
                    if(iframe.contentWindow) {
                        iframe.contentWindow.dispatchEvent(new Event('resize'));
                    }
                    console.log("布局校准完成");
                }, 100);
            };

            // 第一次校准：立即执行
            calibrate();

            // 第二次校准：联网环境下 xterm 启动慢，500ms 后补刀校准，确保高度计算正确
            setTimeout(calibrate, 600);
        };

        btn.innerText = "销毁容器环境";
        btn.className = "action-btn btn-destroy";
        btn.onclick = destroyEnv;
        startTimer(parseInt(data.timeout));
    } else {
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