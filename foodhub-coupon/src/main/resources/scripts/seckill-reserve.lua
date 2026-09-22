local expectedPath = redis.call('GET', KEYS[3])
if not expectedPath or expectedPath ~= ARGV[2] then
    return 4
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 or redis.call('EXISTS', KEYS[4]) == 1 then
    return 2
end

local stock = redis.call('GET', KEYS[1])
if not stock then
    return -1
end
if tonumber(stock) <= 0 then
    return 1
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('EXPIRE', KEYS[2], ARGV[4])
redis.call('SET', KEYS[4], ARGV[3], 'EX', ARGV[4])
redis.call('DEL', KEYS[3])
return 0
