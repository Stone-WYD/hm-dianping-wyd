# 代码使用说明
黑马redis学习项目：
- 学习项目，完整项目在下面项目的master分支上
- 获取方式见下方
## 下载
克隆完整项目
```git
git clone https://gitee.com/huyi612/hm-dianping.git
```
切换分支
```git
git checkout init
```



## 学习笔记

### 短信登录

##### 基于 session 实现注意事项

1. 生成短信验证码，将验证码存入 session 中
2. 登录时校验验证码登录，保存用户信息到数据库和 session 中，存入session中是为了其他需要使用用户信息的接口方便查出
3. 使用拦截器，对于某些需要使用用户信息的接口，需要先确认 session 中是否有 user 信息，有的话放行并将用户数据放在一个 ThreadLocal 对象中，没有的话拦截并报错
4. 使用 session 时，浏览器会存储 cookie ，通过 cookie 中的 sessionId 可以在 tomcat中找到对应的session对象
5. 存在的问题：session共享问题，多台tomcat并不能共享session存储空间

##### 基于 redis 的实现

1. 生成验证码，验证码存入 redis 中，其中 key 是电话号码，value 是验证码
2. 登录时校验验证码，因为登录时需要传入电话号码，所以可以很容易从 redis 中取出验证码来进行校验。校验通过后保存用户信息时，保存到数据库的操作和使用 session 一样，但是保存到 redis 中时，需要生成一个 token 作为 key，并且将 key 返回给客户端
3. 同上3，但是客户端存储了 token 后，通过 token 获取用户信息，此处使用 token 的原因是不会泄露用户信息。
4. 注意问题： Spring 默认注入的 RedisTemplate 是 StringRedisTemplate，进行 hash 操作时，要求放入其中的 key 全是 String，那么在使用工具将 bean 转化为 map 时，需要进行一些额外的将 字段类型转化为 String 的操作。
5. 使用 redis 代替 session 需要考虑的问题：
   - 选择合适的数据结构
   - 选择合适的key
   - 选择合适的存储粒度（敏感信息不要返回给前端）

### 缓存

##### 缓存查询策略

##### 缓存更新策略

1. 低一致性需求：使用 Redis 自带的内存淘汰机制
2. 更新缓存还是删缓存：
   - 更新缓存会产生无效更新，并且存在较大的线程安全问题
   - 删除缓存本质时延迟更新，没有无效更新，线程安全问题相对较低
3. 高一致性需求：主动更新，并以超时剔除作为兜底方案
   - 读操作：缓存命中则直接返回，缓存未命中则查询数据库，并写入缓存，设定超时时间
   - 写操作：先写数据库，然后再删除缓存。要确保数据库与缓存操作的原子性

##### 缓存穿透

1. 产生原因
   - 用户请求的数据在缓存和数据库中都不存在，不断发起这样的请求，给数据库带来巨大的压力
2. 解决方案：
   - 缓存 null 值
   - 布隆过滤
   - 增强 id 复杂度，避免被猜测 id 的风险
   - 做好数据的基础校验
   - 加强用户权限的校验
   - 做好热点参数的限流

##### 缓存雪崩

1. 产生原因：同一时段内大量缓存 key 同时失效或者 Redis 服务宕机，导致大量请求到达数据库，带来巨大压力
2. 解决方案：
   - 给不同的 key 添加随机的 TTL
   - 利用 Redis 集群提高服务的可用性
   - 给缓存业务添加降级限流策略
   - 给业务添加多级缓存

##### 缓存击穿

1. 产生原因：也叫热点 key 问题，就是一个被高并发访问并且缓存重建业务比较复杂的 key 突然失效了，无数的请求访问会在瞬间给数据库带来巨大的冲击
2. 解决方案：
   - 互斥锁：没有额外内存消耗，保证一致性，实现简单，但有死锁风险，性能受影响。
   - 逻辑过期：线程无需等待，性能较好。但不能保证一致性，有额外内存消耗，实现复杂。没获取到锁，不争不抢，直接返回过期数据
3. 关于两种解决方案：
   - 互斥锁的解决方案比较简单，只是在缓存重建的过程中增加了加锁的操作，同一时间内多个读操作只有一个线程能获取锁然后重建缓存。***这种方案是可以跟缓存null值解决缓存穿透共同实现的。***
   - 相比较与互斥锁的处理，逻辑过期的解决方案性能更高，但是会牺牲一部分的一致性。另外，逻辑过期的方案，初始是要**给所有key都新建缓存**的，所以当 key 查不到缓存时可以直接返回 null，同时也**不会存在缓存穿透的问题**。使用逻辑过期的处理需要考虑具体的业务场景。



