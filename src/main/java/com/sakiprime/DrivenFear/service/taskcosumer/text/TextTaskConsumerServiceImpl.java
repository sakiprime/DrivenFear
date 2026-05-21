package com.sakiprime.DrivenFear.service.taskcosumer.text;

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
 * 任务消费者服务实现
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextTaskConsumerServiceImpl implements TextTaskConsumerService {
    private final AICallTaskMapper aiCallTaskMapper;
    private final UserMapper userMapper;
    private final TaskConsumerTransactionService transactionService;
    private final RabbitTemplate rabbitTemplate;
    private final MailDirectComponent mailDirectComponent;
    private final UserCommonService userCommonService;
    private final AITaskFactory aiTaskFactory;
    /**
     * 保存订单和扣款
     *
     * @param request 请求
     * @return boolean
     */
    @Override
    public boolean saveOrderAndDeduction(AICallRequestDTO request) {

        for(int i=0;i<2;i++) {
            try {
                transactionService.saveOrderAndDeductionTransaction(request);
                return true;
            } catch (RuntimeException e) {
            //log打印已在事务方法内部完成
            }
        }
        String logInfo = String.format("文本生成任务存储/扣款失败|用户:%s,订单:%s", request.getUserId(),request.getOrderId());
        userCommonService.refreshUserTokenRedisFromMySQL(request.getUserId());
        MessageCorrelationData correlation = new MessageCorrelationData(
                FAILED_TASK_QUEUE,
                request,
                logInfo,
                MessageCorrelationData.AI_TASK
        );
        //推送任务到死信队列
        rabbitTemplate.convertAndSend(FAILED_TASK_QUEUE,correlation,correlation);
        return false;

    }

    /**
     * 标记失败任务
     *
     * @param task 任务
     * @return boolean
     */
    @Override
    public boolean markFailedTask(AICallTaskEntity task) {
        //如果任务已经被标记，则直接返回true。
    if (aiCallTaskMapper.selectById(task.getOrderId()) != null) {
            return true;
    }
    String userId = task.getUserId();
    task.setTaskStatus("FAILED");
    task.setRequireManual(true);
    //标记用户
    boolean markSuccess = userMapper.updateTaskNeedManual(userId,true)>0;
    boolean insertSuccess = aiCallTaskMapper.insert(task)>0;
    return markSuccess && insertSuccess;
    }


    /**
     * 将任务发送到API
     *
     * @param orderId 订单号
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public void markApiCallFailed(Long orderId) {
        AICallTaskEntity task = aiCallTaskMapper.selectById(orderId);
        if (task == null) {
            log.warn("文本任务API调用标记失败：订单不存在, orderId:{}", orderId);
            return;
        }
        aiCallTaskMapper.updateTaskStatus(orderId, TaskStatusEnum.FAILED.getCode());
        aiCallTaskMapper.updateTaskRequireManual(orderId, true);
        userMapper.updateTaskNeedManual(task.getUserId(), true);
        log.warn("文本任务API调用已标记为失败，订单号:{}", orderId);
    }

    @Override
    public Result<Void> sendTaskToApi(Long orderId) {
    /*返回假数据。如果要接入API，则会采用策略模式。
       目前只支持TEXT任务——我觉得这足够证明平台的架构能力。
       通过订单号再查任务是为了保证订单切实在库中。我觉得更安全。
       也许DB操作量可以优化一下。
     */
        AICallTaskEntity task = aiCallTaskMapper.selectById(orderId);
        if(task == null) {
            log.warn("生成任务执行失败:未知的订单{}",orderId);
            return Result.fail();
        }
        String userId = task.getUserId();
        if(!task.getTaskStatus().equals(TaskStatusEnum.PENDING.getCode())) {
            log.warn("生成任务执行失败:任务已在执行或执行完毕{}",task.getTaskStatus());
        }
        aiCallTaskMapper.updateTaskStatus(orderId,TaskStatusEnum.PROCESSING.getCode());

        AITaskStrategy strategy = aiTaskFactory.getStrategy(task.getTaskType());
        String result = strategy.processTask(task);
        task.setTextMessage(result);
        task.setTaskStatus(TaskStatusEnum.SUCCESS.getCode());
        aiCallTaskMapper.updateById(task);
        log.info("生成任务执行成功,订单号:{}",orderId);
        String timeStr = task.getCreateTime().toString();

        //由于是返回假数据，所以成功与否只和通知有关。
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
