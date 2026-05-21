package com.sakiprime.DrivenFear.component;

import com.sakiprime.DrivenFear.common.util.RequestUtil;
import com.sakiprime.DrivenFear.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimiter {
    private final RedisTemplate<String, Object> redisTemplate;
    private final String LUA_SCRIPT = "redis.call('SET', KEYS[1], '0', 'NX', 'EX', ARGV[1]) " +
            "return redis.call('INCR', KEYS[1])";
    private final RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);
    private static final long IP_LIMIT = 15;
    private static final long GLOBAL_LIMIT = 200;
    private static final long EXPIRE = 3 * 60;
    //该方法已弃用。用于组件式的快速调用。
    public Result<Void> rateLimiter(String interFace) {
        //首先获取IP，最轻的操作。
        Optional<String> ipOpt = RequestUtil.getIp();
        if (ipOpt.isEmpty()) {
            return Result.fail(400, "请求异常，请检查网络环境。");
        }
        String ip = ipOpt.get();

        String globalLimit = "rate:global:uri:"+ interFace;
        String limit = "rate:ip:uri:" + ip + ":" + interFace;
        try {
            long result = redisTemplate.execute(script, Collections.singletonList(limit), EXPIRE);
            if (result>=IP_LIMIT) {//事实上，几乎无可能出错，除非对应value被其他方法操作并不为数字类型。
                log.info("触发防刷器。接口:{},IP:{}", interFace,ip);
                return Result.fail(429,"请求繁忙，请稍后再试。");
            }
            result = redisTemplate.execute(script, Collections.singletonList(globalLimit), EXPIRE);
            if (result>=GLOBAL_LIMIT) {
                log.error("触发限流器。接口:{}", interFace);
                return Result.fail(429,"服务器繁忙，请稍后再试。");
            }
        }
        catch (Exception e) {//不应发生，因为只用上面的lua脚本操作这两个key，必然生成数字类型。
            log.error("「降级放行」限流/防刷器计数值不为数字。{}",e.getMessage());
            return Result.success(null);
        }
        return Result.success(null);
    }
    //对上面方法的重载，用以指定最大限流防刷量和过期时间，现在被用于注解式调用。
    public Result<Void> rateLimiter(String interFace,long ipLimitCount,long globalLimitCount,long expire) {
        //首先获取IP，最轻的操作。
        Optional<String> ipOpt = RequestUtil.getIp();
        if (ipOpt.isEmpty()) {
            return Result.fail(400, "请求异常，请检查网络环境。");
        }
        String ip = ipOpt.get();

        String globalLimit = "rate:global:uri:"+ interFace;
        String limit = "rate:ip:uri:" + ip + ":" + interFace;
        try {
            long result = redisTemplate.execute(script, Collections.singletonList(limit), expire);
            if (result>=ipLimitCount) {//事实上，几乎无可能出错，除非对应value被其他方法操作并不为数字类型。
                log.info("触发防刷器。接口:{},IP:{}", interFace,ip);
                return Result.fail(429,"请求繁忙，请稍后再试。");
            }
            result = redisTemplate.execute(script, Collections.singletonList(globalLimit), expire);
            if (result>=globalLimitCount) {
                log.error("触发限流器。接口:{}", interFace);
                return Result.fail(429,"服务器繁忙，请稍后再试。");
            }
        }
        catch (Exception e) {//不应发生，因为只用上面的lua脚本操作这两个key，必然生成数字类型。
            log.error("「降级放行」限流/防刷器计数值不为数字。{}",e.getMessage());
            return Result.success(null);
        }
        return Result.success(null);
    }
}
