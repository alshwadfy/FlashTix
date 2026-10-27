-- KEYS[1]: event stock key (e.g. event:evt-101:stock)
-- ARGV[1]: requested ticket count to decrement (e.g. 1)

local stock = tonumber(redis.call('get', KEYS[1]))

-- If key does not exist or stock is lower than requested count:
if not stock or stock < tonumber(ARGV[1]) then
    return -1
end

-- Atomically decrement stock:
redis.call('decrby', KEYS[1], ARGV[1])
return stock - tonumber(ARGV[1])
