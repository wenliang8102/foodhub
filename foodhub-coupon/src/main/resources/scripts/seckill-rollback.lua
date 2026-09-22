local currentRequest = redis.call('GET', KEYS[3])
if not currentRequest or currentRequest ~= ARGV[2] then
    return 0
end

if redis.call('EXISTS', KEYS[1]) == 1 then
    redis.call('INCR', KEYS[1])
end
redis.call('SREM', KEYS[2], ARGV[1])
redis.call('DEL', KEYS[3])
return 1
