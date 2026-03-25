# JoyLocal (悦享) - 本地生活商家聚合与风控平台

##  项目简介
JoyLocal 是一个面向本地生活 O2O 场景的商家聚合与风控管理系统。项目旨在解决高并发场景下的用户鉴权、流量洪峰应对、商品库存防超卖以及分布式缓存一致性等核心业务痛点。通过引入多级缓存架构与消息队列，系统能够在保障数据一致性的前提下，提供高可用、低延迟的本地生活服务体验。

##  技术栈
* **后端开发：** Java 17, Spring Boot, Spring MVC, MyBatis-Plus
* **数据存储：** MySQL 8.0, Redis
* **中间件：** RocketMQ, Redisson
* **其他组件：** Lua 脚本, ThreadLocal

## ✨ 核心技术亮点

* **无状态鉴权与上下文管理**
  基于 Spring MVC 拦截器重构鉴权模块，结合 Redis 实现无状态会话管理。利用 Java `ThreadLocal` 封装用户上下文环境，实现集群环境下当前线程内用户状态的安全隔离与高效传递，降低了系统耦合度。

* **高性能多级缓存架构**
  遵循 Cache Aside 模式引入 Redis 缓存层。针对高并发场景，前置处理缓存穿透问题，并结合逻辑过期机制从容应对缓存击穿。有效保障了数据库与缓存的双写一致性，核心接口的响应延迟成功降低约 **80%**。

* **精准防超卖与分布式锁机制**
  采用 MySQL 乐观锁 (CAS) 结合 Spring 事务机制，精准拦截并发场景下的商品超卖请求。同时，利用 Redis 配合 Lua 脚本封装防误删的分布式锁，确保了库存扣减与风控逻辑的原子性执行。

* **高并发异步削峰与超时流转**
  引入 RocketMQ 应对秒杀抢购等流量洪峰，实现核心请求的异步解耦与落库，大幅降低瞬时系统内存压力。深度利用 RocketMQ 原生的延迟消息机制，优雅实现了“订单 15 分钟未支付自动取消”及库存回滚的闭环逻辑。

##  部署与运行

本项目支持标准的工程化部署，以下为核心的部署操作流程：

### 1. 环境准备
确保服务器已安装并启动以下依赖组件：
* JDK 17+
* MySQL 8.0+
* Redis 6.0+ (需支持 Lua 脚本)
* RocketMQ (NameServer & Broker)

### 2. 数据库初始化
* 创建数据库 `joylocal_db`。
* 导入项目根目录下的 `sql/schema.sql` 完成表结构与基础数据的初始化。

### 3. 配置修改
修改 `src/main/resources/application-prod.yml` 中的关键配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://YOUR_IP:3306/joylocal_db
    username: root
    password: YOUR_PASSWORD
  redis:
    host: YOUR_IP
    port: 6379
rocketmq:
  name-server: YOUR_IP:9876

## Seckill MQ Flow

- `POST /voucher-order/seckill/{id}` only completes Redis eligibility check and pushes a RocketMQ order-create message, so the core request path stays lightweight under traffic spikes.
- `SeckillOrderConsumer` consumes the create message asynchronously, deducts DB stock, persists an unpaid order, and then emits a native delayed timeout message.
- `OrderTimeoutConsumer` checks the order 15 minutes later. If it is still unpaid, it cancels the order and rolls back both DB stock and Redis reservation.

## RocketMQ Notes

- This implementation uses RocketMQ native timed delivery (`syncSendDeliverTimeMills`) for the 15-minute order timeout, so it should run against RocketMQ 5.0+.
- Create the timeout topic as a `DELAY` topic and the seckill topic as a `NORMAL` topic before running the application.
