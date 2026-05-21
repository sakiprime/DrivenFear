package com.sakiprime.DrivenFear.service.aicall;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AITaskFactory {
    private final Map<String, AITaskStrategy> strategyMap;
    public AITaskFactory(Map<String, AITaskStrategy> strategyMap){
        this.strategyMap = strategyMap;
    }
    public AITaskStrategy getStrategy(String taskType){
        return strategyMap.get(taskType);
    }
}
