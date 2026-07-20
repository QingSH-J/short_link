# 短链接服务 (Short Link Service)

一个基于 Spring Boot 的短链接生成与跳转服务，支持号段发号、不可枚举短码、多级缓存、布隆过滤器防穿透、接口限流和点击量统计。

## 功能特性

- **短链生成**：长链接 → 短码，基于分布式号段发号器保证 ID 唯一
- **不可枚举短码**：用 Hashids 对自增 ID 做可逆混淆，短码无法被顺序猜测/遍历
- **302 跳转**：访问短码重定向到原始链接
- **点击量统计**：Redis 计数 + 定时批量落库，跳转不阻塞
- **短链管理**：查询详情、软删除
- **缓存**：Redis 缓存热点短链，缓存空值防穿透，Redis 故障自动降级到数据库
- **布隆过滤器**：拦截不存在的短码，减少无效查询（Redisson 分布式布隆）
- **接口限流**：基于 Redisson 的分布式限流
- **有效期**：短链默认 30 天过期

## 技术栈

| 层 | 技术 |
|----|------|
| 语言/框架 | Java 17, Spring Boot 4 |
| 数据库 | PostgreSQL |
| 数据库迁移 | Flyway |
| ORM | Spring Data JPA (Hibernate) |
| 缓存/中间件 | Redis, Redisson (布隆过滤器、限流) |
| 短码编码 | Hashids |
| 前端 | React 19 + Vite + TypeScript |

## 架构概览

```
                   ┌─────────────┐
   创建请求  ──────▶│  Handler    │
   跳转请求  ──────▶│ (Controller)│──── 限流 (Redisson RateLimiter)
                   └──────┬──────┘
                          ▼
                   ┌─────────────┐
                   │   Service   │
                   └──────┬──────┘
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   布隆过滤器        Redis 缓存        号段发号器
  (防穿透)      (读加速/降级)      (GetIdService)
          │               │               │
          └───────────────┼───────────────┘
                          ▼
                   ┌─────────────┐
                   │ PostgreSQL  │
                   └─────────────┘
```

**短链生成流程**：校验 URL → 号段发号器取唯一 ID → Hashids 编码为短码 → 存库 → 事务提交后预热缓存 & 加入布隆过滤器

**短链跳转流程**：布隆过滤器判断 → 查 Redis 缓存 → 未命中查数据库 → 校验有效性（过期/状态）→ 计数 +1 → 302 跳转

## 快速开始

### 前置依赖

- JDK 17+
- PostgreSQL（本地 5432，需要一个 `demo` 数据库）
- Redis（本地默认 6380，无密码）

### 环境变量

启动前需要设置（本地开发大多有默认值，仅 `HASHIDS_SALT` 必填）：

| 变量 | 说明 | 本地默认值 |
|------|------|-----------|
| `HASHIDS_SALT` | 短码编码密钥（**必填**，上线后不可更改） | 无 |
| `DB_URL` | 数据库连接串 | `jdbc:postgresql://localhost:5432/demo` |
| `DB_USERNAME` | 数据库用户名 | `postgres` |
| `DB_PASSWORD` | 数据库密码 | `postgres` |
| `REDIS_HOST` | Redis 主机 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6380` |
| `SPRING_PROFILES_ACTIVE` | 环境 profile | `dev` |

### 运行后端

```bash
# 设置密钥并启动（表结构由 Flyway 自动创建）
HASHIDS_SALT="your-dev-salt" ./mvnw spring-boot:run
```

启动后：
- 服务地址：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`

### 运行前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173，/api 自动代理到后端 8080
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/link` | 创建短链，body: `{"original":"https://..."}` |
| `GET` | `/{shortCode}` | 跳转（302 重定向到原链） |
| `GET` | `/api/link/{shortCode}` | 查询短链详情 |
| `GET` | `/api/link/{shortCode}/stats` | 查询点击量 |
| `DELETE` | `/api/link/{shortCode}` | 软删除短链 |

### 示例

```bash
# 创建
curl -X POST http://localhost:8080/api/link \
  -H "Content-Type: application/json" \
  -d '{"original":"https://github.com"}'
# → {"shortCode":"NwrN...","originalUrl":"https://github.com"}

# 跳转
curl -i http://localhost:8080/NwrN...      # 302 + Location 头

# 查询点击量
curl http://localhost:8080/api/link/NwrN.../stats
```

## 数据库迁移

表结构与初始数据由 Flyway 管理，位于 `src/main/resources/db/migration/postgre/`：

| 脚本 | 内容 |
|------|------|
| `V1__create_short_links.sql` | 短链表 |
| `V2__create_id.sql` | 号段表 |
| `V3__init_id_segment.sql` | 号段种子数据 |
| `V4__add_click_count.sql` | 点击量字段 |

> 已应用的迁移脚本请勿修改，需变更表结构请新增 `V5__xxx.sql`。

## 部署（Railway）

1. 创建 PostgreSQL 和 Redis 服务
2. 部署应用（自动识别 Maven 构建）
3. 配置环境变量：
   ```
   SPRING_PROFILES_ACTIVE=prod
   DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DB_USERNAME=${{Postgres.PGUSER}}
   DB_PASSWORD=${{Postgres.PGPASSWORD}}
   REDIS_HOST=${{Redis.REDISHOST}}
   REDIS_PORT=${{Redis.REDISPORT}}
   REDIS_PASSWORD=${{Redis.REDISPASSWORD}}
   HASHIDS_SALT=<生产环境随机密钥>
   ```
4. Health Check Path 设为 `/actuator/health`

> 应用通过 `server.port=${PORT}` 自动适配 Railway 分配的端口。

## 注意事项

- **`HASHIDS_SALT` 一旦上线不可更改**，否则历史短码将无法正确解析
- 生产环境所有敏感配置通过环境变量注入，切勿硬编码或提交到仓库
- 布隆过滤器无法删除元素，被删短码由状态校验拦截（无副作用）
