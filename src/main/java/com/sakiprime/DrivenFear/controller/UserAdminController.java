package com.sakiprime.DrivenFear.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.annotation.RequireRole;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.*;
import com.sakiprime.DrivenFear.service.userfile.UserAdminService;
import com.sakiprime.DrivenFear.service.userfile.UserCommonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 *
 * @author 凋零
 * @since 2026/05/04
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserAdminController {
    private final UserAdminService userAdminService;
    private final UserCommonService userCommonService;

    /**
     * 下载信息列表
     *
     * @param current               当前
     * @param size                  尺寸
     * @param hasTaskNeedManual     有任务需要人工
     * @param hasPayOrderNeedManual 有支付订单需要人工
     * @param isBanned              被禁止
     * @param orderBy               按...排序
     * @param orderType             订单类型
     * @return {@link Result }<{@link IPage }<{@link UserEntity }>>
     */
    @GetMapping("/downloadinfolist")
    @ApiRateLimit(interFace = "downloadInfoList")
    @RequireRole(role = "admin")
    public Result<IPage<UserEntity>> downloadInfoList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Boolean hasTaskNeedManual,
            @RequestParam(required = false) Boolean hasPayOrderNeedManual,
            @RequestParam(required = false) Boolean isBanned,
            @RequestParam(defaultValue = "userId") String orderBy,
            @RequestParam(defaultValue = "desc") String orderType
    ){

        return userAdminService.getUserPage(
                current,size,hasTaskNeedManual,hasPayOrderNeedManual,isBanned,orderBy,orderType
        );
    }

    /**
     * 下载信息管理员
     *
     * @param userId 用户ID
     * @return {@link Result }<{@link UserEntity }>
     */
    @GetMapping("/downloadinfoadmin")
    @ApiRateLimit(interFace = "downloadInfoAdmin")
    @RequireRole(role = "admin")
    public Result<UserEntity> downloadInfoAdmin(
            @RequestParam String userId){

        return userAdminService.getUserById(userId);
    }

    /**
     * 切换用户禁止
     *
     * @param userId   用户ID
     * @param isBanned 被禁止
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/toggleuserban")
    @ApiRateLimit(interFace = "toggleUserBan")
    @RequireRole(role = "admin")
    public Result<Void> toggleUserBan(
            @RequestParam String userId,
            @RequestParam Boolean isBanned
    ){

        return userAdminService.toggleUserBan(userId,isBanned);
    }

    /**
     * 上传信息管理员
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/uploadinfoadmin")
    @ApiRateLimit(interFace = "uploadInfoAdmin")
    @RequireRole(role = "admin")
    public Result<Void> uploadInfoAdmin(@RequestBody UserDTO user){

        //管理员更新中userId以前端获取的为准。这里的Result可以内联。
        Result<Void> uploadResult = userCommonService.uploadInfo(user);
        if(uploadResult.getCode() !=200){
            return uploadResult;
        }
        return Result.success(null);
    }


    /**
     * 调整用户Token
     *
     * @param userId 用户ID
     * @param token  Token
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/adjustusertoken")
    @ApiRateLimit(interFace = "adjustUserToken")
    @RequireRole(role = "admin")
    public Result<Void> adjustUserToken(
            @RequestParam String userId,@RequestParam Integer token){

        return userAdminService.adjustUserToken(userId,token);
    }

    /**
     * 创建充值包
     *
     * @param packageEntity 包实体
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/rechargepackage")
    @ApiRateLimit(interFace = "createRechargePackage")
    @RequireRole(role = "admin")
    //id为自增主键无需传入。
    Result<Void> createRechargePackage(@RequestBody RechargePackageEntity packageEntity){

        return userAdminService.createRechargePackage(packageEntity);
    }

    /**
     * 切换充值套餐上架状态
     *
     * @param packageId 包id
     * @param isOnSale  正在打折
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/rechargepackage/togglesale")
    @ApiRateLimit(interFace = "toggleRechargePackage")
    @RequireRole(role = "admin")
    Result<Void> toggleRechargePackage(
            @RequestParam Long packageId,@RequestParam Boolean isOnSale){

        return userAdminService.toggleRechargePackageSale(packageId,isOnSale);
    }

    /**
     * 删除充值包
     *
     * @param packageId 软件包ID
     * @return {@link Result }<{@link Void }>
     */
    @DeleteMapping("/rechargepackage")
    @ApiRateLimit(interFace = "deleteRechargePackage")
    @RequireRole(role = "admin")
    Result<Void> deleteRechargePackage(@RequestParam Long packageId){

        return userAdminService.deleteRechargePackage(packageId);
    }

    /**
     * 获取aimodel列表
     *
     * @return {@link Result }<{@link List }<{@link AIModelConfigEntity }>>
     */
    @GetMapping("/aimodelconfig")
    @ApiRateLimit(interFace = "getAIModelList")
    @RequireRole(role = "admin")
    Result<List<AIModelConfigEntity>> getAIModelList(){

        return userAdminService.getAIModelConfigList();
    }

    /**
     * 保存或更新aimodel
     *
     * @param modelConfigEntity 模型配置实体
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/aimodelconfig")
    @ApiRateLimit(interFace = "saveOrUpdateAIModel")
    @RequireRole(role = "admin")
    Result<Void> saveOrUpdateAIModel(@RequestBody AIModelConfigEntity modelConfigEntity){

        return userAdminService.saveOrUpdateAIModelConfig(modelConfigEntity);
    }

    /**
     * 删除aimodel
     *
     * @param modelId 型号id
     * @return {@link Result }<{@link Void }>
     */
    @DeleteMapping("/aimodelconfig")
    @ApiRateLimit(interFace = "deleteAIModel")
    @RequireRole(role = "admin")
    Result<Void> deleteAIModel(@RequestParam Long modelId){

        return userAdminService.deleteAIModel(modelId);
    }

    /**
     * 获取aicall任务列表
     *
     * @param current       当前
     * @param size          尺寸
     * @param taskType      任务类型
     * @param taskStatus    任务状态
     * @param requireManual 需要手动
     * @param orderBy       按...排序
     * @param orderType     订单类型
     * @return {@link Result }<{@link IPage }<{@link AICallTaskEntity }>>
     */
    @GetMapping("/aitask")
    @ApiRateLimit(interFace = "getAIModelList")
    @RequireRole(role = "admin")
    Result<IPage<AICallTaskEntity>> getAICallTaskList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Boolean requireManual,
            @RequestParam(defaultValue = "time") String orderBy,
            @RequestParam(defaultValue = "desc") String orderType){

        return userAdminService.getAICallTaskList(current,size,taskType,taskStatus,requireManual,orderBy,orderType);
    }

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
    @GetMapping("/rechargeorder")
    @ApiRateLimit(interFace = "getRechargeOrderList")
    @RequireRole(role = "admin")
    Result<IPage<UserRechargeOrderEntity>> getRechargeOrderList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(defaultValue = "TEXT") String taskType,
            @RequestParam(defaultValue = "SUCCESS") String taskStatus,
            @RequestParam(required = false) Boolean requireManual,
            @RequestParam(defaultValue = "time") String orderBy,
            @RequestParam(defaultValue = "desc") String orderType){

        return userAdminService.getRechargeOrderList(current,size,taskType,taskStatus,requireManual,orderBy,orderType);
    }

    /**
     * 获取充值套餐信息列表管理员
     *
     * @return {@link Result }<{@link List }<{@link RechargePackageEntity }>>
     */
    @GetMapping("/recharge")
    @ApiRateLimit(interFace = "getRechargePackageInfoListAdmin")
    @RequireRole(role = "admin")
    public Result<List<RechargePackageEntity>> getRechargePackageInfoListAdmin(){

        return userAdminService.getPackageInfoList();
    }

    /**
     * 切换aicall任务私有
     *
     * @param taskId    任务ID
     * @param isPrivate 是私密
     * @param userId    用户ID
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/aitask/private")
    @ApiRateLimit(interFace = "toggleAICallTaskPrivateAdmin")
    @RequireRole(role = "admin")
    Result<Void> toggleAICallTaskPrivate(
            @RequestParam long taskId,
            @RequestParam Boolean isPrivate,
            @RequestParam String userId
    ){

        return userAdminService.toggleAICallTaskPrivateAdmin(taskId, userId,
                isPrivate);
    }

    /**
     * 切换充电订单申诉
     *
     * @param orderId  订单号
     * @param userId   用户ID
     * @param isAppeal 是申诉
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/rechargeorder/appeal")
    @ApiRateLimit(interFace = "toggleRechargeOrderAppeal", ipLimit = 10)
    @RequireRole(role = "admin")
    Result<Void> toggleRechargeOrderAppeal(
            @RequestParam long orderId,
            @RequestParam String userId,
            @RequestParam Boolean isAppeal
    ){

        return userAdminService.toggleRechargeOrderAppealAdmin(userId,orderId,isAppeal);
    }

    /**
     * 切换aicall任务申诉
     *
     * @param orderId  订单号
     * @param userId   用户ID
     * @param isAppeal 是申诉
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/aitask/appeal")
    @ApiRateLimit(interFace = "toggleAICallTaskAppeal", ipLimit = 10)
    @RequireRole(role = "admin")
    Result<Void> toggleAICallTaskAppeal(
            @RequestParam long orderId,
            @RequestParam String userId,
            @RequestParam Boolean isAppeal
    ){

        return userAdminService.toggleAICallTaskAppealAdmin(userId,orderId,isAppeal);
    }
}
