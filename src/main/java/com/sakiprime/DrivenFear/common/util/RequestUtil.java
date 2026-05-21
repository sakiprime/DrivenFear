package com.sakiprime.DrivenFear.common.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
@Slf4j
public class RequestUtil {
    public static Map<String, String> convertNotifyParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String key = entry.getKey();
            String value = String.join(",", entry.getValue());
            params.put(key, value);
        }
        return params;
    }
    public static Optional<HttpServletRequest> getRequest() {
        SaRequest saRequest = SaHolder.getRequest();
        if (saRequest == null) {//非请求线程。
            log.error("非请求线程调用getRequest方法。线程：{}",Thread.currentThread().getName());
            return Optional.empty();
        }
        Object source = saRequest.getSource();
        //先校验是非HttpServletRequest再强转。
        if (!(source instanceof HttpServletRequest)) {
            log.error("Request原始对象不是HttpServletRequest类型,原始对象：{}", source);
            return Optional.empty();
        }
        return Optional.of((HttpServletRequest) source);
    }
    public static Optional<String> getIp() {
        Optional<HttpServletRequest> request = getRequest();
        return request.map(JakartaServletUtil::getClientIP);
    }
}

