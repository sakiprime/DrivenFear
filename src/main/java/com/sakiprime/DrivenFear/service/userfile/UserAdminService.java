package com.sakiprime.DrivenFear.service.userfile;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.*;

import java.util.List;

/**
 * 用户管理服务
 *
 * @author 凋零
 * @since 2026/05/04
 */
public interface UserAdminService {
    /**
     * “获取用户” 页
     *
     * @param current               当前
     * @param size                  尺寸
     * @param hasTaskNeedManual     有任务需要人工
     * @param hasPayOrderNeedManual 有支付订单需要人工
     * @param isBanned              是封禁
     * @param orderBy               按...排序
     * @param orderType             订单类型
     * @return {@link Result }<{@link IPage }<{@link UserEntity }>>
     */
    Result<IPage<UserEntity>> getUserPage(
            long current, long size,
            Boolean hasTaskNeedManual,
            Boolean hasPayOrderNeedManual,
            Boolean isBanned,
            String orderBy,
            String orderType);

    /**
     * 按id获取用户
     *
     * @param userId 用户ID
     * @return {@link Result }<{@link UserEntity }>
     */
    Result<UserEntity> getUserById(String userId);


    /**
     * 切换用户封禁
     *
     * @param userId   用户ID
     * @param isBanned 封禁状态
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> toggleUserBan(String userId, Boolean isBanned);

    /**
     * 调整用户Token
     *
     * @param userId 用户ID
     * @param token  Token
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> adjustUserToken(String userId, Integer token);

    /**
     * 创建充值包
     *
     * @param packageEntity 包实体
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> createRechargePackage(RechargePackageEntity packageEntity);

    /**
     * 切换充值套餐销售
     *
     * @param packageId 包id
     * @param isOnSale  正在打折
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> toggleRechargePackageSale(Long packageId, Boolean isOnSale);

    /**
     * 更新充值套餐
     *
     * @param packageEntity 包实体
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> updateRechargePackage(RechargePackageEntity packageEntity);

    /**
     * 删除充值套餐
     *
     * @param packageId 软件包ID
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> deleteRechargePackage(Long packageId);

    /**
     * 获取aimodel配置列表
     *
     * @return {@link Result }<{@link List }<{@link AIModelConfigEntity }>>
     */
    Result<List<AIModelConfigEntity>> getAIModelConfigList();

    /**
     * 保存或更新aimodel配置
     *
     * @param aiModelConfigEntity ai模型配置实体
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> saveOrUpdateAIModelConfig(AIModelConfigEntity aiModelConfigEntity);

    /**
     * 删除aimodel
     *
     * @param modelId 型号id
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> deleteAIModel(Long modelId);

    /**
     * 获取aicall任务列表
     *
     * @param current       当前
     * @param size          尺寸
     * @param taskType      任务类型
     * @param taskStatus    任务状态
     * @param requireManual 要求人工
     * @param orderBy       按...排序
     * @param orderType     订单类型
     * @return {@link Result }<{@link IPage }<{@link AICallTaskEntity }>>
     */
    Result<IPage<AICallTaskEntity>> getAICallTaskList(long current, long size, String taskType,
                                  String taskStatus, Boolean requireManual,String orderBy, String orderType);

    /**
     * 获取充值订单列表
     *
     * @param current       当前
     * @param size          尺寸
     * @param taskType      任务类型
     * @param taskStatus    任务状态
     * @param requireManual 要求人工
     * @param orderBy       按...排序
     * @param orderType     订单类型
     * @return {@link Result }<{@link IPage }<{@link UserRechargeOrderEntity }>>
     */
    Result<IPage<UserRechargeOrderEntity>> getRechargeOrderList(long current, long size, String taskType,
                                                                String taskStatus, Boolean requireManual, String orderBy, String orderType);

    /**
     * 获取包信息列表
     *
     * @return {@link Result }<{@link List }<{@link RechargePackageEntity }>>
     */
    Result<List<RechargePackageEntity>> getPackageInfoList();

    /**
     * 切换aicall任务私有管理员
     *
     * @param taskId    任务ID
     * @param userId    用户ID
     * @param isPrivate 是私密
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> toggleAICallTaskPrivateAdmin(Long taskId, String userId, Boolean isPrivate);

    /**
     * 切换充电订单申诉
     *
     * @param userId   用户ID
     * @param orderId  订单号
     * @param isAppeal 是申诉
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> toggleRechargeOrderAppealAdmin(String userId, Long orderId, Boolean isAppeal);

    /**
     * 切换aicall任务申诉
     *
     * @param userId   用户ID
     * @param orderId  订单号
     * @param isAppeal 是申诉
     * @return {@link Result }<{@link Void }>
     */
    Result<Void> toggleAICallTaskAppealAdmin(String userId, Long orderId, Boolean isAppeal);

    /** 管理后台概览数据 */
    Result<DashboardVO> getDashboard();
}
