package com.sakiprime.DrivenFear.service.aigallery;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;

/**
 * Aigalery服务
 *
 * @author 凋零
 * @since 2026/05/04
 */
public interface AIGalleryService {
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
    IPage<AICallTaskEntity> getGalleryPage(
            long current, long size, String taskType, String orderBy, String orderType
    );
}
