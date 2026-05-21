package com.sakiprime.DrivenFear.service.aicall;

import com.sakiprime.DrivenFear.entity.AICallRequestDTO;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;

public interface AITaskStrategy {
    boolean execute(AICallRequestDTO request);
    String processTask(AICallTaskEntity task);
}
