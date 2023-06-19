-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
local orderId = ARGV[3]

-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order:' .. voucherId

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

-- 消费者组是在队列的基础上建立的，消费者组中的消费者在消费消息后会在队列上添加一个标记，组内消费者再消费时只会消费这之后的消息。
-- 消费者消费后如果没ack，则消息会加入消费者组的pending list结构中，消费者可以在pending list中重新消费没成功消费的消息
-- 3.7.发送消息到消息队列， XADD stream.order * k1 v1 k2 v2 ...  要先确保已经创建了消息队列，创建语句：XGROUP CREATE stream.orders g1 0 MKSTREAM
--                                                            解释：创建某队列（stream.orders）的消费者组（g1），该消费者组从队列起始开始读（0），如果队列不存在则新建一个队列(MKSTREAM)
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
--                                  * 标识id由redis自动生成
return 0
