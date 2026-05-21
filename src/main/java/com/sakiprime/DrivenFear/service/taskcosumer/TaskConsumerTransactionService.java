package com.sakiprime.DrivenFear.service.taskcosumer;

import com.sakiprime.DrivenFear.entity.AICallRequestDTO;

public interface TaskConsumerTransactionService {
    void saveOrderAndDeductionTransaction(AICallRequestDTO request);
}
