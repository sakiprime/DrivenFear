package com.sakiprime.DrivenFear.aspect;

import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.component.RateLimiter;
import com.sakiprime.DrivenFear.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RateLimiter rateLimiter;

    @Pointcut("@annotation(com.sakiprime.DrivenFear.annotation.ApiRateLimit)")
    public void limiter(){}

    @Around("limiter() && @annotation(rateAnn)")
    public Object around(ProceedingJoinPoint point, ApiRateLimit rateAnn) throws Throwable {

            Result<Void> result =
            rateLimiter.rateLimiter(
                    rateAnn.interFace(),
                    rateAnn.ipLimit(),
                    rateAnn.globalLimit(),
                    rateAnn.expire()
            );
            if(result.getCode() != 200){
                return  result;
            }

        return point.proceed();
    }
}
