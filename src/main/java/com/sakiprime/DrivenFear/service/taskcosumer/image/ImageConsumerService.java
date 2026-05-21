package com.sakiprime.DrivenFear.service.taskcosumer.image;

import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;

public interface ImageConsumerService {
    boolean saveOrderAndDeduction(AICallRequestDTO request);
    boolean markFailedTask(AICallTaskEntity task);
    Result<Void> sendTaskToApi(Long orderId);
    void markApiCallFailed(Long orderId);
}
