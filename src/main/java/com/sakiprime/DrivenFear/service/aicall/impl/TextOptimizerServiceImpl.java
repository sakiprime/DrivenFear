package com.sakiprime.DrivenFear.service.aicall.impl;

import com.sakiprime.DrivenFear.component.PromptTemplate;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.service.aicall.TextOptimizerService;
import com.sakiprime.DrivenFear.service.userfile.UserCommonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 流式提示词优化器实现
 *
 * @author 凋零
 * @since 2026/05/21
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextOptimizerServiceImpl implements TextOptimizerService {

    private static final int OPTIMIZE_COST = 8;

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final UserMapper userMapper;
    private final UserCommonService userCommonService;

    @Override
    public boolean tryDeductAndRefresh(String userId) {
        int affected = userMapper.deductToken(userId, OPTIMIZE_COST);
        if (affected == 0) {
            log.warn("用户{}流式优化器扣款失败，余额不足", userId);
            return false;
        }
        userCommonService.refreshUserTokenRedisFromMySQL(userId);
        return true;
    }

    @Override
    public Flux<String> optimize(String taskType, String prompt) {

        return chatClient.prompt()
                .system(promptTemplate.getTemplate(taskType))
                .user(prompt)
                .options(
                        OpenAiChatOptions.builder()
                                .temperature(1.0)
                                .topP(0.9)
                                .maxTokens(2000)
                                .frequencyPenalty(0.2)
                                .presencePenalty(0.1)
                                .build()
                )
                .stream()
                .content();
    }
}
