CommonEnv 在线容器平台 - 开发文档

*   相关项目：https://github.com/mlfc666/CommonEnv
*   演示地址：https://java.mlfc.moe/

## 1. 任务进度概览

### 基础功能需求
- [x] **容器环境管理**：实现容器的异步创建、启动、状态检查与销毁
- [x] **动态代理转发**：基于 Spring Cloud Gateway 实现从主域名到容器内网 IP 的请求转发
- [x] **终端嵌入功能**：通过 ttyd 将 Linux 终端集成至 Web 页面，并支持自动聚焦
- [x] **资源配额控制**：支持对每个容器进行 CPU 权重和内存上限（RAM）的物理限制
- [x] **生命周期自动化**：实现容器过期自动回收机制与系统启动时的残留环境清理

### 扩展思路与优化
- [x] **WebSocket 支持**：动态路由层支持 WS/WSS 协议转发，确保终端交互流畅
- [x] **镜像同步机制**：启动环境前自动检查并尝试拉取远程仓库的最新镜像
- [x] **布局校准算法**：前端通过延迟重绘技术解决 iframe 嵌入终端时的黑边与比例失调问题
- [x] **多环境配置**：区分开发（dev）与生产（prod）环境，支持内网桥接与自定义网络切换
- [x] **全局异常处理**：针对容器满员、系统错误提供统一的 RESTful 错误响应

---

## 2. 项目架构与类/接口说明

### 2.1 配置层 (Configs)
| 类名称 | 职责说明 | 关键点 |
| :--- | :--- | :--- |
| `DockerProperties` | 配置映射类 | 绑定 `app.docker` 前缀，管理镜像名、内网、资源限制及超时时长 |
| `DockerConfig` | 客户端工厂 | 封装 Apache HttpClient 5，构建单例 `DockerClient` 连接引擎 |
| `DynamicRouteConfig` | 动态路由层 | 使用 Spring Cloud Gateway MVC 拦截 `/env-{sid}/**` 并转发至对应 IP |

### 2.2 业务逻辑层 (Services)
| 类名称 | 职责说明 | 关键点 |
| :--- | :--- | :--- |
| `DockerService` | 核心业务中心 | 负责容器全生命周期管理，包含 `ttyd` 参数构建与终端配色配置 |
| `Scheduled Tasks` | 定时清理任务 | 运行于 `DockerService` 中，每分钟扫描并强制移除 EXPIRATION_TIME 过期的容器 |
| `EventListener` | 初始化监听 | 在 `ApplicationReadyEvent` 触发时清理所有带有 `env-` 前缀的残留容器 |

### 2.3 表现层与控制器 (Controllers & UI)
| 类/文件名称 | 类型 | 职责说明 |
| :--- | :--- | :--- |
| `InternalController` | REST接口 | 提供内部查询接口，根据 SID 获取容器内网 IP 及其响应头设置 |
| `GlobalExceptionHandler` | 增强处理器 | 捕获 `IllegalStateException`（满员）及通用异常，返回 429 或 500 状态码 |
| `index.html` | 结构层 | 响应式网格布局，定义导航栏、计时器面板与终端容器区域 |
| `style.css` | 表现层 | 采用 CSS Grid 布局，锁定 100dvh 视口高度，强制隐藏系统滚动条 |
| `script.js` | 控制层 | 处理环境创建/销毁逻辑，维护倒计时状态，执行 iframe 宽度校准算法 |

---

## 3. 核心功能实现说明

### 3.1 容器创建与参数注入
*   **操作流程**：用户点击“创建容器环境”，前端向 `/api/v1/spawn` 发送 POST 请求。
*   **实现细节**：
    1.  校验当前运行容器数是否超过 `max-containers`。
    2.  注入环境变量 `EXPIRATION_TIME` 作为逻辑过期标识。
    3.  启动 `ttyd` 进程，动态计算 `-b` (basePath) 参数以适配 Nginx 反向代理路径。
    4.  通过 `HostConfig` 限制内存（如 256MB）和 CPU 配额。

### 3.2 动态路由转发逻辑
*   **转发原理**：
    1.  拦截路径模式 `/env-{sid}/**`。
    2.  从路径变量中解析出 `sid`。
    3.  调用 `dockerClient` 查询容器内网 IP 地址。
    4.  判断请求头 `Upgrade` 是否为 `websocket`，动态切换 `ws://` 或 `http://` 协议。
    5.  利用 `MvcUtils.GATEWAY_REQUEST_URL_ATTR` 将目标地址注入转发链。

### 3.3 前端布局校准 (Layout Calibration)
*   **问题背景**：iframe 在首次加载 ttyd 时，由于 DOM 渲染时机问题，终端字符矩阵常出现黑边或排列错乱。
*   **解决方案**：
    *   在 `iframe.onload` 触发后延迟 300ms。
    *   先将 iframe 宽度修改为 `99%` 强制触发浏览器重绘。
    *   50ms 后恢复 `100%` 并调用 `.focus()` 确保键盘事件能立即被终端捕获。

### 3.4 自动化回收机制
*   **逻辑说明**：
    *   **定时清理**：每 60 秒遍历所有前缀为 `/env-` 的容器，提取其 `EXPIRATION_TIME` 环境变量，与系统当前时间戳比对。
    *   **强力销毁**：通过 `withForce(true)` 确保即使容器进程卡死也能被物理移除，释放内存占用。

---

## 4. 部署与环境要求

### 4.1 运行环境
*   **JDK版本**：Java 17 或更高版本。
*   **容器引擎**：Docker Engine (支持 Unix Domain Socket)。
*   **网络要求**：建议创建专用的 Docker Network（如 `1panel-network`）以实现宿主机与容器间的内网互通。

### 4.2 核心配置项 (application.yml)
*   `app.docker.host`: Docker 套接字地址（默认 unix:///var/run/docker.sock）。
*   `app.docker.image`: 使用的镜像，需预装 ttyd、tmux 及相关的运行环境。
*   `app.docker.timeout-seconds`: 默认环境存活时长（如 1200 秒）。
*   `app.docker.base-url`: 外部访问的基础 URL，影响 ttyd 的路径重定向逻辑。