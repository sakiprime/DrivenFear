package com.sakiprime.DrivenFear.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {//统一返回体。
    private int code;
    private String msg;
    private T data;

    // 默认成功
    public static <T> Result<T> success(T data){
        return build(200, "操作成功", data);
    }

    // 自定义提示成功
    public static <T> Result<T> success(String msg, T data){
        return build(200, msg, data);
    }

    // 默认失败
    public static <T> Result<T> fail(){
        return build(500, "操作失败", null);
    }

    // 自定义失败
    public static <T> Result<T> fail(int code, String msg){
        return build(code, msg, null);
    }
    private static <T> Result<T> build(int code, String msg, T data){
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
