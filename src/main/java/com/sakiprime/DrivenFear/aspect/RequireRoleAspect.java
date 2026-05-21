package com.sakiprime.DrivenFear.aspect;
import cn.dev33.satoken.stp.StpUtil;
import com.sakiprime.DrivenFear.annotation.RequireRole;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.common.util.RoleUtil;
import com.sakiprime.DrivenFear.entity.UserEntity;
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
public class RequireRoleAspect {

    @Pointcut("@annotation(com.sakiprime.DrivenFear.annotation.RequireRole)")
    public void interceptor(){}

    @Around("interceptor() && @annotation(roleAnn)")
    public Object around(ProceedingJoinPoint point, RequireRole roleAnn) throws Throwable {
        //无需登录，直接放行。
        if(!roleAnn.needLogin()){
            return point.proceed();
        }
        //需要登录但未登录。
        if(!StpUtil.isLogin()){
            log.info("拦截游客行为");
            return Result.fail(401,"请先登录");
        }
        //安全转换。
        UserEntity user;
        Object obj = StpUtil.getSession().get("loginUser");
        if (obj instanceof UserEntity) {
            user = (UserEntity) obj;
        }
        else{//不太可能出现这个错误。
            log.error("Session缓存的用户信息类型非法。");
            return Result.fail(500,"系统繁忙，请稍后再试。");
        }
        //当用户为管理员或有接口所需角色时放行。
        if(RoleUtil.isAdmin(user) || RoleUtil.hasRole(user, roleAnn.role())){
            return point.proceed();
        }

        return Result.fail(403,"您无权访问此资源。");

    }
}
