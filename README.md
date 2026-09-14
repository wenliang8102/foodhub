# FoodHub

FoodHub 是一个面向本地生活服务的微服务项目，基于 Spring Boot、Spring Cloud
和 Maven 多模块工程构建。当前阶段先确立服务边界、公共基础设施、服务发现和可重复的本地运行环境。

## 运行环境要求

- JDK 21
- 已启用 Linux 容器的 Docker Desktop
- Git

不要求全局安装 Maven，直接使用仓库内置的 Maven Wrapper 即可。

## 启动开发环境

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f infrastructure/compose.yaml up -d
.\mvnw.cmd clean verify
```

基础设施访问地址：

| 组件 | 地址 |
| --- | --- |
| Nacos | http://localhost:8848/nacos |
| RabbitMQ management | http://localhost:15672 |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

应用端口和模块职责见
[`docs/architecture.md`](docs/architecture.md).

RabbitMQ 初始交换机、路由键、队列、消息字段和幂等规则见
[`docs/messaging-contracts.md`](docs/messaging-contracts.md).

merchant 和 social 两个并行开发小组的边界说明见
[`docs/parallel-development-guide.md`](docs/parallel-development-guide.md).

## 运行单个服务

```powershell
.\mvnw.cmd -pl foodhub-auth -am spring-boot:run
```

默认 `local` 配置会从环境变量读取连接信息，并使用仅供开发环境使用的默认值。
生产环境密钥不得提交到本仓库。
