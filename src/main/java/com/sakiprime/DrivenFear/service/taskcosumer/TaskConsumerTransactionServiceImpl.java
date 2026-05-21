package com.sakiprime.DrivenFear.service.taskcosumer;

import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.mapper.AICallTaskMapper;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务消费者事务服务impl
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskConsumerTransactionServiceImpl implements TaskConsumerTransactionService {
    private final AICallTaskMapper aiCallTaskMapper;
    private final UserMapper userMapper;

    /**
     * 保存订单和扣款交易记录
     *
     * @param request 请求
     */
    @Transactional(rollbackFor = Exception.class)//事务：扣token和存储订单强一致
    public void saveOrderAndDeductionTransaction(AICallRequestDTO request) {

        AICallTaskEntity task = new AICallTaskEntity(request);
        boolean insertSuccess = aiCallTaskMapper.insert(task)>0;
        boolean deductSuccess = userMapper.deductToken(task.getUserId(), task.getTokenCost())>0;
        if(!insertSuccess){
            log.error("存储任务订单失败,用户:{},订单号:{}",task.getUserId(),task.getOrderId());
            throw new RuntimeException("存储任务订单失败");
        }
        if(!deductSuccess){
            log.error("MySQL用户token扣除失败,用户:{},订单号:{}",task.getUserId(),task.getOrderId());
            throw new RuntimeException("MySQL用户token扣除失败");
        }
    }
}
