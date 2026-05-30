package com.sakiprime.DrivenFear.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_model_config")
public class AIModelConfigEntity {
    /**
     * 标识符
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 型号类型
     */
    private String modelType;//任务类型，比如TEXT
    /**
     * 型号名称
     */
    private String modelName;//模型名称
    /**
     * 成本Token
     */
    private Integer costToken=20;//调用花费的token
    /**
     * 状态
     */
    private Boolean status;//是否启用
    /**
     * 请求体模板JSON
     */
    private String template;//预编码模板: {requestBody, paramsSchema, responsePath}
}
