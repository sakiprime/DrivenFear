package com.sakiprime.DrivenFear.service.taskcosumer.text;

import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;

public interface TextTaskConsumerService {
    boolean saveOrderAndDeduction(AICallRequestDTO request);
    boolean markFailedTask(AICallTaskEntity task);
    Result<Void> sendTaskToApi(Long orderId);
    void markApiCallFailed(Long orderId);
}
