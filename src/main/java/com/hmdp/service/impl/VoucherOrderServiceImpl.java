package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SpringUtil;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final static DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorder;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService seckill_order_executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void  init(){
        seckill_order_executor.submit(new VoucherOrderHandler());
    }

    private BlockingQueue<VoucherOrder> ordersTask = new ArrayBlockingQueue<>(1024 * 1024);

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {

            while (true){
                // 获取队列中的订单信息
                VoucherOrder voucherOrder = null;
                try {
                    voucherOrder = ordersTask.take();
                } catch (InterruptedException e) {
                    log.error("从阻塞队列中获取订单信息失败！");
                }
                // 创建订单
                handlerVoucherOrder(voucherOrder);
            }
        }

        private void handlerVoucherOrder(VoucherOrder voucherOrder) {
            // 获取用户
            Long userId = voucherOrder.getUserId();
            // 创建锁对象
            RLock lock = redissonClient.getLock("lock:order+" + userId);
            // 获取锁
            boolean isLock = lock.tryLock();
            // 判断获取锁是否成功
            if (!isLock){
                // 获取锁失败 理论上不会出现这种情况
                log.error("线程池获取创建订单任务报错：不允许重复下单");
                return;
            }
            SpringUtil.getBean(VoucherOrderServiceImpl.class).createVoucherOrder3(voucherOrder);
        }
    }

    @Transactional
    public void createVoucherOrder3(VoucherOrder voucherOrder) {
        // 这里其实没必要加这么多判断然后 return 空这么写，因为Redis已经判断过了。而如果redis真的出了问题，其实是需要手动地去修复问题的。
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次了！");
            return;
        }

        // 扣减库存
        boolean update = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0).update();

        if (!update) {
            // 扣减失败
            log.error("库存不足！");
            return;
        }
        // 创建订单
        save(voucherOrder);
    }


    @Override
    public Result seckillVocher(Long voucherId) throws InterruptedException {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorder.nextId("order");
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        int r = result.intValue();
        if (r != 0) {
            return Result.fail( r==1 ? "库存不足" : "不能重复下单");
        }
        // TODO: 2023/6/15 保存阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        ordersTask.add(voucherOrder);
        // 返回订单id
        return Result.ok(orderId);
    }

    /* @Override // 未使用redis进行业务流程上的优化
    public Result seckillVocher(Long voucherId) throws InterruptedException {
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

        // ILock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
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
    }*/

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
