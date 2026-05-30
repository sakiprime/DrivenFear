package com.sakiprime.DrivenFear.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.annotation.RequireRole;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.component.QiniuOSS;
import com.sakiprime.DrivenFear.entity.AICallFastRequest;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.service.aicall.AITaskFactory;
import com.sakiprime.DrivenFear.service.aicall.AITaskStrategy;
import com.sakiprime.DrivenFear.service.aicall.TextOptimizerService;
import com.sakiprime.DrivenFear.service.aicall.impl.ImageTaskStrategy;
import com.sakiprime.DrivenFear.service.aicall.impl.TextTaskStrategy;
import com.sakiprime.DrivenFear.service.aicall.impl.VideoTaskStrategy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * aicall控制器
 *
 * @author 凋零
 * @since 2026/05/04
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AICallController {
    private final AITaskFactory aiTaskFactory;
    private final TextOptimizerService textOptimizerService;
    private final QiniuOSS qiniuOSS;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REFS_KEY_PREFIX = "user:refs:";
    private static final long REFS_TTL_HOURS = 6;



    @PostMapping("/refs")
    @ApiRateLimit(interFace = "uploadRefFile", ipLimit = 30)
    @RequireRole
    public Result<List<String>> uploadRefFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("taskType") String taskType) {

        Result<List<String>> result = qiniuOSS.uploadRefs(files, taskType);
        if (result.getCode() == 200) {
            String redisKey = REFS_KEY_PREFIX + StpUtil.getLoginIdAsString();
            redisTemplate.opsForValue().set(redisKey, result.getData(), REFS_TTL_HOURS, TimeUnit.HOURS);
        }
        return result;
    }

    @GetMapping("/refs")
    @RequireRole
    public Result<List<String>> getCachedRefs() {

        String redisKey = REFS_KEY_PREFIX + StpUtil.getLoginIdAsString();
        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) redisTemplate.opsForValue().get(redisKey);
        return Result.success(urls != null ? urls : List.of());
    }

    @PostMapping("/tasks")
    @ApiRateLimit(interFace = "createTask")
    @RequireRole
    public Result<Void> createTask(@RequestBody AICallRequestDTO request){//策略模式
        //稳稳接住。
    request.setUserId(StpUtil.getLoginIdAsString());
    AITaskStrategy strategy = aiTaskFactory.getStrategy(request.getTaskType());
    if (strategy == null) {
        return Result.fail(400,"非法的任务类型");
    }
    boolean isSuccess = strategy.execute(request);
    if (!isSuccess) { //调用失败的情况下Redis不会扣款，也不会推送MQ消息。
        return Result.fail(500,"调用失败");
    }
        return Result.success("任务创建成功，排队中",null);
    }

    /**
     * 获取全部可用模型
     *
     * @return {@link Result }<{@link Map }<{@link String },{@link Map }<{@link String },{@link Integer }>>>
     */
    @GetMapping("/tasks/models")
    @ApiRateLimit(interFace = "getModels")
    @RequireRole
    public Result<Map<String, Map<String, ?>>> getModels(){
        Map<String, Map<String, ?>> allModels = new java.util.HashMap<>(4);
        allModels.put("TEXT", TextTaskStrategy.TEXT_MODEL_TEMPLATE_MAP);
        allModels.put("IMAGE", ImageTaskStrategy.IMAGE_MODEL_TEMPLATE_MAP);
        allModels.put("VIDEO", VideoTaskStrategy.VIDEO_MODEL_TEMPLATE_MAP);
        return Result.success(allModels);
    }


    /**
     * 流式提示词优化器
     *
     * @param request 请求
     * @return {@link Flux }<{@link String }>
     */
    @PostMapping(value = "/api/text_prompt/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiRateLimit(interFace = "streamPromptOptimizer",ipLimit = 10,expire = 120)
    public Flux<String> streamPromptOptimizer(
            @RequestBody AICallFastRequest request,
            HttpServletResponse response
    ) {

        if (!StpUtil.isLogin()) {
            response.setStatus(401);
            return Flux.empty();
        }

        if (!textOptimizerService.tryDeductAndRefresh(StpUtil.getLoginIdAsString())) {
            response.setStatus(402);
            return Flux.empty();
        }

        return textOptimizerService.optimize(request.getTaskType(), request.getUserPrompt());
    }
}
