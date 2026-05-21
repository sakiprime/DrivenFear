package com.sakiprime.DrivenFear.service.aicall;

import reactor.core.publisher.Flux;

/**
 * 流式提示词优化器
 *
 * @author 凋零
 * @since 2026/05/21
 */
public interface TextOptimizerService {

    Flux<String> optimize(String taskType, String userPrompt);
}
