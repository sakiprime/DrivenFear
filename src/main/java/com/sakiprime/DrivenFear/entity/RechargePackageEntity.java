package com.sakiprime.DrivenFear.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("top_up_package")
public class RechargePackageEntity {
    /**
     * 标识符
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 排序id
     */
    private Integer sortId;
    /**
     * 包名称
     */
    private String packageName;
    /**
     * 描述
     */
    private String description;
    /**
     * 代币金额
     */
    private Integer tokensAmount;//到账的点数
    /**
     * 原价
     */
    private Integer originalPrice;//单位为分
    /**
     * 折扣价
     */
    private Integer discountedPrice;
    /**
     * 图片网址
     */
    private String imageUrl;
    /**
     * 正在销售
     */
    @TableField("is_on_sale")
    private Boolean onSale;
    /**
     * 版本
     */
    @Version
    private Integer version;
}
