package com.sakiprime.DrivenFear.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流消息
 *
 * @author 凋零
 * @since 2026/05/15
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamMessage {

    private int code;
    private String data;
    private String msg;

    /**
     * 成功
     *
     * @param data 数据
     * @return {@link StreamMessage }
     */
    public static StreamMessage success(String data) {
        StreamMessage message = new StreamMessage();
        message.setCode(200);
        message.setData(data);
        message.setMsg(null);
        return message;
    }

    /**
     * 失败
     *
     * @return {@link StreamMessage }
     */
    public static StreamMessage fail() {
        return fail("服务器异常，请稍后重试");
    }

    /**
     * 失败
     *
     * @param msg 消息
     * @return {@link StreamMessage }
     */
    public static StreamMessage fail(String msg) {
        StreamMessage message = new StreamMessage();
        message.setCode(500);
        message.setData(null);
        message.setMsg(msg);
        return message;
    }

}