# AgentX 智能体平台

AgentX 是一个面向企业和个人的智能 Agent 平台，支持 Agent 创建与发布、工作区会话、模型供应商配置、RAG 知识库、MCP 工具市场、Widget 外嵌、OpenAI 风格开放接口、用量计费、订单支付、后台审核和 Docker 部署等能力。

## 核心功能

- Agent 管理：创建、编辑、启停、版本发布、版本审核、工具绑定、知识库绑定。
- 工作区会话：会话创建、消息历史、SSE 流式响应、会话中断、多模态文件输入。
- 模型配置：支持 OpenAI、Anthropic 等模型供应商配置，并包含高可用网关和降级切换能力。
- RAG 知识库：数据集创建、文件上传、文档解析、语料分段、Embedding 入库、pgvector 检索。
- 检索增强：HyDE、向量检索、关键词检索、RRF 融合、Rerank、相邻片段扩展。
- 工具市场：工具上传、GitHub 校验、版本管理、安装/卸载、审核发布、MCP Gateway 接入。
- 开放能力：API Key 管理、OpenAI 风格 `/v1/chat/completions` 接口、Widget 公共聊天入口。
- 计费支付：账户余额、用量记录、Token 计费、订单生成、支付宝/Stripe 支付回调。
- 管理后台：Agent、工具、知识库、用户、订单、规则、容器模板等后台管理能力。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2.3、Spring MVC、Validation、WebSocket
- MyBatis-Plus
- PostgreSQL、pgvector
- RabbitMQ
- LangChain4j
- JWT
- Docker Java
- x-file-storage、Aliyun OSS、AWS S3 SDK
- Jakarta Mail
- Stripe Java SDK、Alipay EasySDK
- PDFBox、Tika、POI、flexmark

### 前端

- Next.js 15
- React 19
- TypeScript
- Tailwind CSS
- Radix UI / shadcn 风格组件
- lucide-react
- Axios / Fetch
- React Hook Form、Zod
- React Markdown、remark-gfm
- Recharts
- xterm
- qrcode

### 部署与中间件

- Docker / Docker Compose
- PostgreSQL + pgvector
- RabbitMQ
- Adminer（可选）

> 说明：当前项目没有使用 Nacos，也没有使用 Redis 作为运行依赖。

## 项目结构

```text
.
├── AgentX/                    # Spring Boot 后端
│   ├── src/main/java/org/xhy
│   │   ├── interfaces/         # Controller、请求/响应 DTO
│   │   ├── application/        # 应用服务、编排逻辑、Assembler
│   │   ├── domain/             # 领域模型、领域服务、仓储接口
│   │   └── infrastructure/     # 数据库、MQ、LLM、RAG、Docker、支付等基础设施适配
│   └── pom.xml
├── agentx-frontend-plus/       # Next.js 前端
│   ├── app/                    # App Router 页面
│   ├── components/             # 页面组件和 UI 组件
│   ├── hooks/                  # React Hooks
│   ├── lib/                    # API service、HTTP client、工具方法
│   └── types/                  # TypeScript 类型定义
├── deploy/                     # Docker 部署文件
│   ├── docker-compose.yml
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend
│   ├── .env.local.example
│   ├── .env.production.example
│   └── .env.external.example
└── .env.example
```

## 快速启动（推荐）

推荐使用 Docker Compose 一键启动完整环境。该方式会启动：

- PostgreSQL + pgvector
- RabbitMQ
- 后端服务
- 前端服务
- Adminer（仅 `tools` 模式）

### 环境要求

- Docker
- Docker Compose v2

### Windows

```powershell
cd deploy
.\start.bat local
```

### Linux / macOS

```bash
cd deploy
chmod +x ./start.sh
./start.sh local
```

启动后访问：

- 前端：http://localhost:3000
- 后端健康检查：http://localhost:8088/api/health
- RabbitMQ 管理页：http://localhost:15672

本地默认账号配置在 `deploy/.env.local.example` 中：

```text
管理员：admin@agentx.local / admin123
测试用户：test@agentx.local / test123
RabbitMQ：guest / guest
```

### 启动模式

```bash
./start.sh local       # 本地开发环境，包含内置 PostgreSQL
./start.sh tools       # local + Adminer
./start.sh production  # 生产风格配置模板
./start.sh external    # 使用外部 PostgreSQL
```

Windows 使用：

```powershell
.\start.bat local
.\start.bat tools
.\start.bat production
.\start.bat external
```

### 查看日志

```bash
cd deploy
docker compose logs -f
```

### 停止服务

```bash
cd deploy
docker compose down
```

如果需要同时删除本地数据库和 RabbitMQ 数据卷：

```bash
docker compose down -v
```

## 本地开发启动

如果希望前后端分别启动，可以只用 Docker 启动依赖服务，然后本地运行后端和前端。

### 1. 启动 PostgreSQL 和 RabbitMQ

```bash
cd deploy
copy .env.local.example .env        # Windows PowerShell/cmd
# cp .env.local.example .env        # Linux / macOS

docker compose --profile localdb up -d postgres rabbitmq
```

### 2. 启动后端

Windows PowerShell：

```powershell
cd AgentX
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="agentx"
$env:DB_USER="postgres"
$env:DB_PASSWORD="postgres"
$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_PORT="5672"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
cd AgentX
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=agentx
export DB_USER=postgres
export DB_PASSWORD=postgres
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
./mvnw spring-boot:run
```

后端默认地址：

```text
http://localhost:8088/api
```

### 3. 启动前端

```bash
cd agentx-frontend-plus
npm install --legacy-peer-deps
npm run dev
```

如需指定后端地址，创建或修改 `agentx-frontend-plus/.env.local`：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api
```

前端默认地址：

```text
http://localhost:3000
```

## 关键环境变量

常用配置可参考：

- `deploy/.env.local.example`
- `deploy/.env.production.example`
- `deploy/.env.external.example`
- `.env.example`

常见配置项：

```env
DB_HOST=postgres
DB_PORT=5432
DB_NAME=agentx
DB_USER=postgres
DB_PASSWORD=postgres

RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

JWT_SECRET=change-this-secret
NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api

AGENTX_ADMIN_EMAIL=admin@agentx.local
AGENTX_ADMIN_PASSWORD=admin123
AGENTX_TEST_ENABLED=true
```

生产环境请务必修改：

- 数据库密码
- RabbitMQ 密码
- JWT 密钥
- 管理员账号密码
- 支付、邮箱、GitHub Token、对象存储等第三方密钥

## 常见问题

### 1. 前端无法请求后端

确认 `NEXT_PUBLIC_API_BASE_URL` 指向后端 API 地址，例如：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8088/api
```

如果使用 Docker Compose，建议直接使用 `deploy/.env.local.example` 生成 `deploy/.env`。

### 2. 后端数据库连接失败

检查 PostgreSQL 容器是否启动：

```bash
cd deploy
docker compose ps
docker compose logs postgres
```

本地运行后端时，`DB_HOST` 应为 `localhost`；Docker Compose 内运行后端时，`DB_HOST` 应为 `postgres`。

### 3. RAG 向量检索不可用

确认数据库镜像为 `pgvector/pgvector:pg15`，并且初始化 SQL 已正确执行。Docker Compose 默认会挂载 `deploy/initdb`。

### 4. MCP / 容器相关能力不可用

MCP 工具和用户容器依赖 Docker Socket。Docker Compose 中后端服务会挂载：

```yaml
/var/run/docker.sock:/var/run/docker.sock
```

如果运行在 Windows Docker Desktop / WSL 环境，请确认 Docker Desktop 正常运行，并允许容器访问 Docker 引擎。

## 提交前检查

提交到 GitHub 前建议检查：

```bash
git status
```

不要提交：

- `.env`、`.env.local`、`application-secrets.yml`
- 简历、课程报告、PPT、PDF 等本地资料
- `node_modules/`、`target/`、`.next/`
- 数据库 dump、日志和临时文件

