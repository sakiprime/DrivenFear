package com.sakiprime.DrivenFear.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.annotation.RequireRole;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.service.aigallery.AIGalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aigaller控制器
 *
 * @author 凋零
 * @since 2026/05/04
 */
@RestController
@RequestMapping("/ai/gallery")
@RequiredArgsConstructor
public class AIGalleryController {
    private final AIGalleryService aiGalleryService;


    /**
     * 获取图库列表
     *
     * @param current   当前
     * @param size      尺寸
     * @param taskType  任务类型
     * @param orderBy   按...排序
     * @param orderType 订单类型
     * @return {@link Result }<{@link IPage }<{@link AICallTaskEntity }>>
     */
    @GetMapping("/list")
    @ApiRateLimit(interFace = "getGalleryList",
            ipLimit = 30, globalLimit = 1200, expire = 60)
    @RequireRole(needLogin = false)
    public Result<IPage<AICallTaskEntity>> getGalleryList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "heat") String orderBy,
            @RequestParam(defaultValue = "desc") String orderType
    ) {
        IPage<AICallTaskEntity> pageResult = aiGalleryService.getGalleryPage(
                current, size, taskType, orderBy, orderType);
        //if(pageResult.getRecords().isEmpty()){
        //    return Result.fail(500,"系统繁忙，请稍后重试。");}无需判空。有可能就是没有符合条件的作品。

        return Result.success(pageResult);
    }

}
