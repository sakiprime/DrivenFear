package com.sakiprime.DrivenFear.service.aigallery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.mapper.AICallTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Aigalery服务实施
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Service
@RequiredArgsConstructor
public class AIGalleryServiceImpl implements AIGalleryService {
    private final AICallTaskMapper aiCallTaskMapper;
    public static final List<String> ALLOWED_TASK_TYPES =
            List.of("TEXT", "IMAGE", "VIDEO");

    /**
     * 获取图库页面
     *
     * @param current   当前
     * @param size      尺寸
     * @param taskType  任务类型
     * @param orderBy   按...排序
     * @param orderType 订单类型
     * @return {@link IPage }<{@link AICallTaskEntity }>
     */
    public IPage<AICallTaskEntity> getGalleryPage(
            long current, long size, String taskType, String orderBy, String orderType){
        //限定size和current范围，共支持25000条数据。足够了。
        current = Math.min(Math.max(current, 1), 500);
        size = Math.min(Math.max(size, 1), 50);

        orderBy = ("heat".equals(orderBy) || "time".equals(orderBy)) ? orderBy : "heat";
        orderType = ("asc".equals(orderType) || "desc".equals(orderType)) ? orderType : "desc";

        Page<AICallTaskEntity> page = new Page<>(current, size);

        LambdaQueryWrapper<AICallTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AICallTaskEntity::getIsPrivate, false)
                .eq(AICallTaskEntity::getRequireManual, false)
                .eq(AICallTaskEntity::getTaskStatus, "SUCCESS");

        if (taskType != null && !taskType.isBlank()) {
            //再次降级。
            if (!ALLOWED_TASK_TYPES.contains(taskType)) {
                taskType = ALLOWED_TASK_TYPES.get(0);
            }
            wrapper.eq(AICallTaskEntity::getTaskType, taskType);
        }

        if ("heat".equals(orderBy)) {
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(AICallTaskEntity::getHeatScore)
                        .orderByDesc(AICallTaskEntity::getUpdateTime);
            } else {
                wrapper.orderByDesc(AICallTaskEntity::getHeatScore)
                        .orderByDesc(AICallTaskEntity::getUpdateTime);
            }
        } else {
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(AICallTaskEntity::getUpdateTime);
            } else {
                wrapper.orderByDesc(AICallTaskEntity::getUpdateTime);
            }
        }


        return aiCallTaskMapper.selectPage(page, wrapper);
    }


}
