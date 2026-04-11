-- 秒杀库存原子扣减脚本
-- KEYS[1]: 库存Key (seckill:stock:{productId})
-- KEYS[2]: 用户已购标记Key (seckill:dup:{userId}:{productId}:{activityId})
-- ARGV[1]: 扣减数量
-- ARGV[2]: 过期时间(秒)

local stockKey = KEYS[1]
local dupKey = KEYS[2]
local deductQuantity = tonumber(ARGV[1])
local expireSeconds = tonumber(ARGV[2])

-- 1. 检查用户是否已参与
local hasParticipated = redis.call('exists', dupKey)
if hasParticipated == 1 then
    return -2  -- 用户已参与
end

-- 2. 获取当前库存
local currentStock = redis.call('get', stockKey)
if currentStock == false then
    return -3  -- 库存未初始化
end

currentStock = tonumber(currentStock)

-- 3. 检查库存充足
if currentStock < deductQuantity then
    return -1  -- 库存不足
end

-- 4. 扣减库存
local remainingStock = redis.call('decrby', stockKey, deductQuantity)

-- 5. 标记用户已参与
redis.call('setex', dupKey, expireSeconds, '1')

-- 6. 返回剩余库存
return remainingStock
