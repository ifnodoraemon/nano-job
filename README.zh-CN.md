# nano-job

`nano-job` 是一个基于 Java 21 和 Spring Boot 的纯后端任务执行平台，聚焦于延迟任务、异步执行、重试控制、去重语义和执行追踪。

这个项目刻意保持体量小，不做前端，也不堆业务页面，但它重点覆盖了真正能体现 Java 后端深度的部分：

- 状态流转
- 异步执行边界
- 重试策略设计
- 提交去重语义
- 租约恢复
- payload 契约
- 可观测性

## 技术栈

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- SQLite
- Micrometer
- JUnit 5

## 项目能力

`nano-job` 可以接收一个任务、持久化、按时间调度、异步执行、按策略重试，并记录完整执行历史。

当前支持的任务类型：

- `NOOP`
- `HTTP`

当前已实现能力：

- 任务创建、查询、取消、分页查询
- 延迟执行
- 独立线程池异步执行
- 执行租约与超时恢复
- 按任务类型区分重试策略
- 提交幂等与语义漂移检测
- 时间窗口去重
- 每次 attempt 的执行日志
- 基于注解自动发现的 payload 契约
- Micrometer 计数器和状态 gauge

## 架构主线

主流程如下：

1. `JobController` 接收请求
2. `JobService` 校验 payload 并执行 dedup 策略
3. `JobScheduler` 触发 `JobDispatchService`
4. `JobDispatchService` 找出到期任务并尝试领取
5. `JobExecutionService` 将任务提交到独立执行器
6. `JobHandler` 执行具体任务逻辑
7. `JobLifecycleService` 决定任务进入 `SUCCESS`、`RETRY_WAIT` 或 `FAILED`
8. `JobExecutionLogService` 记录每次执行 attempt
9. `JobMetricsService` 记录执行指标和状态指标

核心包说明：

- `controller`：API 接口层
- `service`：任务编排与生命周期控制
- `handler`：任务执行逻辑
- `retry`：按类型区分的重试策略
- `dedup`：去重策略与决策模型
- `jobtype`：任务类型元数据和 payload 契约发现
- `metrics`：Micrometer 指标埋点
- `support/payload`：共享 payload 绑定与校验

## 生命周期设计

任务状态：

- `PENDING`
- `RUNNING`
- `RETRY_WAIT`
- `SUCCESS`
- `FAILED`
- `CANCELED`

状态规则：

- 只有 `PENDING` 和 `RETRY_WAIT` 能被领取
- 只有 `RUNNING` 能进入 `SUCCESS` 或 `FAILED`
- `RUNNING` 带租约，避免任务永久挂死
- 租约过期后会回到统一失败路径，而不是另起一套恢复逻辑

这意味着“超时恢复”和“普通执行失败”复用同一套重试与终态语义，系统更统一。

## Payload 契约

payload 使用 Java record + 注解声明，属于“类型化契约”。

示例：

- [`HttpJobPayload`](./src/main/java/com/ifnodoraemon/nanojob/domain/payload/HttpJobPayload.java)
- [`NoopJobPayload`](./src/main/java/com/ifnodoraemon/nanojob/domain/payload/NoopJobPayload.java)

payload 类会声明：

- 自己属于哪个 `JobType`
- 人类可读的描述
- 字段校验规则，例如 `@NotBlank`、`@PositiveOrZero`

`JobTypeDefinitionRegistry` 会自动扫描这些契约，构建任务类型目录。

## Dedup 设计

提交幂等不是写死在 service 里的一个 `if`，而是策略化的。

当前 dedup 行为：

- 如果存在相同 `dedupKey` 的活跃任务，可以返回旧任务或者拒绝创建，取决于策略模式
- 如果相同 `dedupKey` 但 `type` 或 `payload` 不一致，会视为语义漂移并拒绝
- 如果没有活跃任务，但窗口内存在最近完成的同键任务，也可以直接复用

配置示例：

```yaml
nano-job:
  dedup:
    mode: RETURN_EXISTING
    detect-drift: true
    window: 0s
```

## 重试策略

重试是按任务类型决定的，不写死在生命周期引擎里。

- `HTTP`
  - 非法参数不重试
  - `5xx`、`408`、`429`、I/O 失败、超时会重试
  - 退避更激进
- `NOOP`
  - 简单线性重试

这样生命周期服务只负责状态流转，而恢复策略由类型自己定义。

## HTTP 任务执行

`HTTP` 任务使用 JDK 自带的 `HttpClient`。

支持的 payload 字段：

- `url`
- `method`
- `headers`
- `body`
- `timeoutMillis`

当前的 HTTP 错误分类：

- `2xx`：成功
- `4xx`：不可重试
- `408`、`429`、`5xx`：可重试
- 网络失败和超时：可重试
- URL 非法、方法非法：不可重试

## 指标

Micrometer 指标已经接入主执行链路。

计数器：

- `nano.job.dispatch.claimed`
- `nano.job.execution.started`
- `nano.job.execution.succeeded`
- `nano.job.execution.failed`
- `nano.job.execution.retry.scheduled`
- `nano.job.execution.final_failed`
- `nano.job.execution.lease_recovered`

状态 gauge：

- `nano.job.status.count{status=...}`

这些指标统一在中心服务里埋点，而不是散落在各个 handler 里。

## API

主要接口：

- `POST /api/jobs`
- `GET /api/jobs/{jobKey}`
- `POST /api/jobs/{jobKey}/cancel`
- `GET /api/jobs/{jobKey}/logs`
- `GET /api/jobs`
- `GET /api/job-types`

请求示例：

```json
{
  "type": "HTTP",
  "payload": {
    "url": "http://127.0.0.1:8080/demo",
    "method": "POST",
    "body": "{\"event\":\"demo\"}",
    "timeoutMillis": 1000
  },
  "executeAt": "2026-03-17T12:00:00",
  "maxRetry": 2,
  "dedupKey": "demo-http-1"
}
```

## 运行

```bash
./mvnw spring-boot:run
```

## 测试

```bash
./mvnw -Dmaven.repo.local=.m2 test
```

当前测试覆盖：

- 重复领取保护
- 租约超时恢复
- 重试策略行为
- dedup 漂移拒绝
- dedup 时间窗口
- 真实 HTTP 执行
- 指标埋点

## 为什么这个项目有价值

这个项目体量小，但不是 CRUD 项目。

它能体现的能力包括：

- 状态机设计
- 基于条件更新的幂等领取
- 调度与执行解耦
- 类型化 payload 契约
- 策略化的重试与 dedup
- 外部依赖错误分类
- 指标化的可观测性
