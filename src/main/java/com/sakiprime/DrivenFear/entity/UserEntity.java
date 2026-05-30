package com.sakiprime.DrivenFear.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("user_data")
public class UserEntity implements Serializable {
    /**
     * 用户ID
     */
    @TableId
    private String userId;
    /**
     * Token余额
     */
    private Long tokenBalance;
    /**
     * 头像地址
     */
    private String avatarKey;
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    @TableField(select = false)
    private String password;
    /**
     * 电子邮件
     */
    private String email;
    /**
     * 电话
     */
    private String phone;
    /**
     * 有任务需要人工
     */
    private boolean hasTaskNeedManual = false;
    /**
     * 有支付订单需要人工
     */
    private boolean hasPayOrderNeedManual = false;
    /**
     * 封禁
     *///把你关起来。这个命名其实不好，因为MP会解析成banned。
    @TableField("is_banned")
    private boolean banned = false;
    /**
     * 角色代码
     */
    private String roleCodes = "user";
    /**
     * 创建时间
     *///创建以及更新时间。
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    public UserEntity(UserDTO userDTO) {
        this.userId = userDTO.getUserId();
        this.email = userDTO.getEmail();
        this.password = userDTO.getPassword();
    }
}
