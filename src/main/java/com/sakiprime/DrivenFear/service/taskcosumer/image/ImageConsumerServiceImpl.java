package com.sakiprime.DrivenFear.service.taskcosumer.image;

import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.component.MailDirectComponent;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.MessageCorrelationData;
import com.sakiprime.DrivenFear.entity.TaskStatusEnum;
import com.sakiprime.DrivenFear.mapper.AICallTaskMapper;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.service.aicall.AITaskFactory;
import com.sakiprime.DrivenFear.service.aicall.AITaskStrategy;
import com.sakiprime.DrivenFear.service.taskcosumer.TaskConsumerTransactionService;
import com.sakiprime.DrivenFear.service.userfile.UserCommonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import static com.sakiprime.DrivenFear.config.RabbitConfig.FAILED_TASK_QUEUE;

/**
 * 图片任务消费者服务实现
 *
 * @author 凋零
 * @since 2026/05/21
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageConsumerServiceImpl implements ImageConsumerService {
    private final AICallTaskMapper aiCallTaskMapper;
    private final UserMapper userMapper;
    private final TaskConsumerTransactionService transactionService;
    private final RabbitTemplate rabbitTemplate;
    private final MailDirectComponent mailDirectComponent;
    private final UserCommonService userCommonService;
    private final AITaskFactory aiTaskFactory;

    @Override
    public boolean saveOrderAndDeduction(AICallRequestDTO request) {
        for (int i = 0; i < 2; i++) {
            try {
                transactionService.saveOrderAndDeductionTransaction(request);
                return true;
            } catch (RuntimeException e) {
                // log已在事务方法内部完成
            }
        }
        String logInfo = String.format("图片生成任务存储/扣款失败|用户:%s,订单:%s", request.getUserId(), request.getOrderId());
        userCommonService.refreshUserTokenRedisFromMySQL(request.getUserId());
        MessageCorrelationData correlation = new MessageCorrelationData(
                FAILED_TASK_QUEUE,
                request,
                logInfo,
                MessageCorrelationData.AI_TASK
        );
        rabbitTemplate.convertAndSend(FAILED_TASK_QUEUE, correlation, correlation);
        return false;
    }

    @Override
    public boolean markFailedTask(AICallTaskEntity task) {

        if (aiCallTaskMapper.selectById(task.getOrderId()) != null) {
            return true;
        }
        String userId = task.getUserId();
        task.setTaskStatus("FAILED");
        task.setRequireManual(true);
        boolean markSuccess = userMapper.updateTaskNeedManual(userId, true) > 0;
        boolean insertSuccess = aiCallTaskMapper.insert(task) > 0;
        return markSuccess && insertSuccess;
    }

    @Override
    public void markApiCallFailed(Long orderId) {
        AICallTaskEntity task = aiCallTaskMapper.selectById(orderId);
        if (task == null) {
            log.warn("无法标记图片任务执行失败：订单不存在, orderId:{}", orderId);
            return;
        }
        aiCallTaskMapper.updateTaskStatus(orderId, TaskStatusEnum.FAILED.getCode());
        aiCallTaskMapper.updateTaskRequireManual(orderId, true);
        userMapper.updateTaskNeedManual(task.getUserId(), true);
        log.warn("图片任务执行已标记为失败，订单号:{}", orderId);
    }

    @Override
    public Result<Void> sendTaskToApi(Long orderId) {

        AICallTaskEntity task = aiCallTaskMapper.selectById(orderId);
        if (task == null) {
            log.warn("图片生成任务执行失败:未知的订单{}", orderId);
            return Result.fail();
        }
        String userId = task.getUserId();
        //幂等校验。
        if (!task.getTaskStatus().equals(TaskStatusEnum.PENDING.getCode())) {
            log.warn("图片生成任务执行异常:任务已在执行或执行完毕{}", task.getTaskStatus());
            return Result.fail();
        }
        aiCallTaskMapper.updateTaskStatus(orderId, TaskStatusEnum.PROCESSING.getCode());

        AITaskStrategy strategy = aiTaskFactory.getStrategy(task.getTaskType());
        String imageUrl;
        try {
            imageUrl = strategy.processTask(task);
        } catch (Exception e) {
            log.warn("图片生成任务API调用异常，回退状态为PENDING。订单号: {}", orderId, e);
            aiCallTaskMapper.updateTaskStatus(orderId, TaskStatusEnum.PENDING.getCode());
            return Result.fail();
        }
        if (imageUrl == null) {
            aiCallTaskMapper.updateTaskStatus(orderId, TaskStatusEnum.PENDING.getCode());
            log.warn("图片生成任务API调用失败(可能因为请求参数错误或拉取资源失败)，回退状态为PENDING。订单号: {}", orderId);
            return Result.fail();
        }
        task.setImageUrl(imageUrl);
        task.setTaskStatus(TaskStatusEnum.SUCCESS.getCode());
        aiCallTaskMapper.updateById(task);
        log.info("图片生成任务执行成功,订单号:{}", orderId);
        String timeStr = task.getCreateTime().toString();

        return mailDirectComponent.sendNotificationMail(
                userMapper.selectEmailById(userId),
                task.getTaskType(),
                task.getOrderId(),
                timeStr,
                task.getTokenCost(),
                userId
        );
    }
}
