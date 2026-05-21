package com.sakiprime.DrivenFear.service.aicall.impl;

import com.sakiprime.DrivenFear.component.PromptTemplate;
import com.sakiprime.DrivenFear.service.aicall.TextOptimizerService;
import lombok.RequiredArgsConstructor;
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
public class TextOptimizerServiceImpl implements TextOptimizerService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

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
