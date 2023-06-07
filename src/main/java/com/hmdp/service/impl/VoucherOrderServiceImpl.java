package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorder;

    @Override
    public Result seckillVocher(Long voucherId) {
        // 1. 查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2. 查询优惠券是否过期或者未到时间
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("未到抢购时间！");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail("已经过了抢购时间！");
        }
        // 3. 判断库存是否充足
        if (seckillVoucher.getStock()<1) {
            return Result.fail("库存不足！");
        }
        // 分布式锁
        Long userId = UserHolder.getUser().getId();
        ILock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            return Result.fail("不允许重复下单！");
        }
        try{
            return SpringUtil.getBean(VoucherOrderServiceImpl.class).createVoucherOrder2(voucherId);
        }finally {
            lock.unlock();
        }
        // 单机处理
        // return SpringUtil.getBean(VoucherOrderServiceImpl.class).createVoucherOrder(voucherId);
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("一人只能抢一张券！");
            }

            // 扣减库存
            boolean update = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId)
                    .gt("stock", 0).update();

            if (!update) {
                return Result.fail("库存不足！");
            }
            // 5. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 订单id
            Long orderId = redisIdWorder.nextId("order");
            voucherOrder.setId(orderId);
            // 用户id
            voucherOrder.setUserId(userId);
            // 代金券
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(orderId);
        }
    }

    @Transactional
    public Result createVoucherOrder2(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("一人只能抢一张券！");
        }

        // 扣减库存
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();

        if (!update) {
            return Result.fail("库存不足！");
        }
        // 5. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        Long orderId = redisIdWorder.nextId("order");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金券
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
