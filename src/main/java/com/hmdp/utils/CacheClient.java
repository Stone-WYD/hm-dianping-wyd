package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    // 向redis中存入对象
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 向redis中存入带逻辑过期字段的对象
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        // 创建RedisData对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 带缓存穿透处理的获取
    public <R,ID> R  queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = prefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 能获取到有数据的缓存
        if (StrUtil.isNotBlank(jsonStr)) {
            return JSONUtil.toBean(jsonStr, type);
        }
        // 如果获取到的是空字符串
        if (jsonStr!=null) {
            // 处理缓存穿透
            return null;
        }
        // 从数据库查找数据
        R r = dbFallback.apply(id);
        if (r==null){
            // 处理缓存穿透
            set(key, "", time, unit);
            return null;
        }
        // 重建缓存
        set(key, r, time, unit);
        return r;
    }

    // 带逻辑过期处理的获取
    public <R,ID> R queryWithLogicExpire(String prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = prefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 1.缓存未命中
        if (StrUtil.isBlank(jsonStr)) {
            // 正确key的value都已被缓存，没查到则直接返回null
            return null;
        }
        // 2.缓存命中，类型转换，查看是否过期
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 2.1. 未过期，返回数据
            return r;
        }
        // 2.2. 过期
        // 3. 获取锁，重建缓存
        if (tryLock(key)){
            // 双重检查
            RedisData redisDataForCheck = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), RedisData.class);
            if (redisDataForCheck.getExpireTime().isAfter(LocalDateTime.now())) {
                return JSONUtil.toBean((JSONObject) redisDataForCheck.getData(), type);
            }
            // 开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    R dbR = dbFallback.apply(id);
                    setWithLogicExpire(key, dbR, time, unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    releaseLock(key);
                }
            });
        }
        // 4. 当前线程直接返回旧的结果
        return r;
    }

    // 带互斥锁处理的获取
    public <R, ID> R queryWithMutex(String prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = prefix + id;
        try{
            // 1. 从redis中查询,命中缓存 或者 缓存穿透时返回结果
            String jsonStr = stringRedisTemplate.opsForValue().get(key);
            // 缓存命中
            if (StrUtil.isNotBlank(jsonStr)) {
                return JSONUtil.toBean(jsonStr, type);
            }
            if ("".equals(jsonStr))return null;

            // 2. 缓存击穿处理
            // 加锁
            if (!tryLock(key)) {
                // 加锁失败，休眠一段时间
                while(true){
                    Thread.sleep(50);
                    // 休眠直到获取到结果
                    String jsonStrAfterWait = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(jsonStrAfterWait)) return JSONUtil.toBean(jsonStr, type);
                    if ("".equals(jsonStrAfterWait)) return null;
                }
            }
            // 3. 缓存未命中，去数据库查询
            // 3.1. 枷锁成功后的双重检查
            String jsonStrForCheck = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(jsonStrForCheck)) return JSONUtil.toBean(jsonStr, type);
            // 数据库查询操作
            R r = dbFallback.apply(id);
            // 模拟重建缓存操作比较耗时
            Thread.sleep(200);
            // 4. 数据库没有查到数据
            if (r == null) {
                // 缓存穿透处理 增加空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 5. 数据库查到数据，存入redis，返回结果
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
            return r;
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            // 6. 释放锁
            releaseLock(key);
        }
    }

    private boolean tryLock(String key){
        String lockKey = LOCK_KEY + key;
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "lock", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 避免空引用
        return BooleanUtil.isTrue(flag);
    }

    private void releaseLock(String key){
        String lockKey = LOCK_KEY + key;
        // 释放锁
        stringRedisTemplate.delete(lockKey);
    }

}
