package com.sakiprime.DrivenFear.service.aicall;

import reactor.core.publisher.Flux;

/**
 * 流式提示词优化器
 *
 * @author 凋零
 * @since 2026/05/21
 */
public interface TextOptimizerService {

    /** 扣款+刷新Redis，返回是否成功 */
    boolean tryDeductAndRefresh(String userId);

    Flux<String> optimize(String taskType, String userPrompt);
}
