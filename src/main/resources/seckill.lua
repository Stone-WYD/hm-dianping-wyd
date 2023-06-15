-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARG[1]
-- 1.2.用户id
local userId = ARG[2]
-- 1.3.订单id
local orderId = ARG[3]

-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order:' .. orderId

-- 3.脚本业务
-- 3.1.判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0 ) then
    -- 3.2 库存不足，返回1
    return 1
end
-- 3.3.判断是否重复下单
if (redis.call('sismember', orderKey, userId) == 1 ) then
    -- 3.4.已经下过单
    return 2
end
-- 3.5.扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.6.下单（保存用户）sadd orderKey userId
redis.call('sadd', orderKey, userId)
-- 3.7.发送消息到消息队列， XADD stream.order * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.order', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0
