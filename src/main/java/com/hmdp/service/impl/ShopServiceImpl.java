package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        try{
            String key = CACHE_SHOP_KEY + id;
            // 1. 从redis中查询,命中缓存 或者 缓存穿透时返回结果
            Result redisResult = getShopResultFromRedis(key);
            if (redisResult!=null) return redisResult;

            // 2. 缓存击穿处理
            // 加锁
            if (!tryLock(id)) {
                // 加锁失败，休眠一段时间
                while(true){
                    Thread.sleep(50);
                    // 休眠直到获取到结果
                    Result result = getShopResultFromRedis(key);
                    if (result!=null) return result;
                }
            }

            // 3. 缓存未命中，去数据库查询
            // 3.1. 枷锁成功后的双重检查
            Result result = getShopResultFromRedis(key);
            if (result!=null) return result;
            Shop shop = getById(id);
            // 模拟重建缓存操作比较耗时
            Thread.sleep(200);

            // 4. 数据库没有查到数据
            if (shop == null) {
                // 缓存穿透处理 增加空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("未能查到店铺数据!");
            }

            // 5. 数据库查到数据，存入redis，返回结果
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            // 6. 释放锁
            releaseLock(id);
        }

    }

    private Result getShopResultFromRedis(String key) {
        // 1. 查询缓存
        String value = stringRedisTemplate.opsForValue().get(key);
        // 2. 缓存命中
        if (StrUtil.isNotBlank(value)) {
            return Result.ok(JSONUtil.toBean(value, Shop.class));
        }
        // 3. 缓存穿透处理
        if (value != null) {
            return Result.fail("店铺信息不存在！");
        }
        return null;
    }

    private boolean tryLock(Long key){
        String lockKey = LOCK_SHOP_KEY + key;
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "lock", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 避免空引用
        return BooleanUtil.isTrue(flag);
    }

    private void releaseLock(Long key){
        // 释放锁
        stringRedisTemplate.delete(LOCK_SHOP_KEY + key);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空！");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
