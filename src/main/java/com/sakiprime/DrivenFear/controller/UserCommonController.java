package com.sakiprime.DrivenFear.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakiprime.DrivenFear.annotation.ApiRateLimit;
import com.sakiprime.DrivenFear.annotation.RequireRole;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.component.QiniuOSS;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.UserDTO;
import com.sakiprime.DrivenFear.entity.UserEntity;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;
import com.sakiprime.DrivenFear.service.userfile.UserCommonService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户通用控制器
 *
 * @author 凋零
 * @since 2026/05/04
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserCommonController {
    private final QiniuOSS qiniuOSS;
    private final UserCommonService userCommonService;


    /**
     * 上传头像
     *
     * @param file 文件
     * @return {@link Result }<{@link Void }>
     * @throws Exception 异常
     */ //接收文件，从 Session获取当前登录用户
    @PostMapping("/uploadavatar")
    @ApiRateLimit(interFace = "uploadAvatar",ipLimit = 5)
    @RequireRole
    public Result<Void> uploadAvatar(
            @NotNull @RequestParam("file") MultipartFile file) throws Exception {

        String userId = StpUtil.getLoginIdAsString();
        Result<String> uploadResult =qiniuOSS.uploadAvatar(file, userId);
        if(uploadResult.getCode() != 200){
            return Result.fail(uploadResult.getCode(),uploadResult.getMsg());
        }
        //当头像地址存在时，由于头像地址不变，无需更新缓存以及数据库。
        if(StpUtil.getSessionByLoginId(StpUtil.getLoginId())
                .getModel("loginUser", UserEntity.class).getAvatarKey() != null){
            return Result.success(null);
        }
        //第一次上传头像时，执行更新。
        if(!userCommonService.uploadAvatar(userId, uploadResult.getData())){
            return Result.fail();
        }
        userCommonService.refreshUserSession(userId);


        return Result.success(null);
    }

    /**
     * 上传信息
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/uploadinfo")
    @ApiRateLimit(interFace = "uploadInfo",ipLimit = 10)
    @RequireRole
    public Result<Void> uploadInfo(@RequestBody UserDTO user){
        //稳稳接住.....
        String userId = StpUtil.getLoginIdAsString();
        //userId不从前端获取，防止篡改他人信息。
        user.setUserId(userId);
        Result<Void> uploadResult = userCommonService.uploadInfo(user);
        if(uploadResult.getCode() !=200){
            return uploadResult;
        }
        userCommonService.refreshUserSession(userId);
        return Result.success(null);
    }

    /**
     * 下载信息
     *
     * @return {@link Result }<{@link UserEntity }>
     */
    @GetMapping("/downloadinfo")
    @ApiRateLimit(interFace = "downloadInfo",expire = 60)
    @RequireRole
    public Result<UserEntity> downloadInfo() {
        //当session中有缓存时，直接从缓存中取。不应该没有。
        UserEntity loginUser = StpUtil.getSessionByLoginId(StpUtil.getLoginId())
                .getModel("loginUser", UserEntity.class);
        if(loginUser!=null){
            //loginUser.setPassword(null); //早就去敏过了。也许有点多余。
            return Result.success(loginUser);
        }
        String userId = StpUtil.getLoginIdAsString();
        Result<UserEntity> refreshResult = userCommonService.refreshUserSession(userId);
        if(refreshResult.getCode() != 200){
            return Result.fail(500,"服务器繁忙，请稍后再试。");
        }
        
        return refreshResult;
    }

    /**
     * 签到
     *
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/sign")
    @ApiRateLimit(interFace = "sign",ipLimit = 5,expire = 60)
    @RequireRole
    public Result<Void> sign() {

        String userId = StpUtil.getLoginIdAsString();
        return userCommonService.handleSign(userId);
    }

    /**
     * 获取用户Token余额（从Redis）
     *
     * @return {@link Result }<{@link Long }>
     */
    @GetMapping("/tokens")
    @ApiRateLimit(interFace = "getUserTokens")
    @RequireRole
    public Result<Long> getUserTokens() {

        return userCommonService.getUserTokenRedis(StpUtil.getLoginIdAsString());
    }

    /**
     * 获取AI呼叫任务列表
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
    @GetMapping("/aitask")
    @ApiRateLimit(interFace = "getAIModelList")
    @RequireRole
    Result<IPage<AICallTaskEntity>> getAICallTaskList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Boolean requireManual,
            @RequestParam(defaultValue = "time") String orderBy,
            @RequestParam(defaultValue = "desc") String orderType){
            //只能查询自己userId的任务。
        return userCommonService.getAICallTaskList( StpUtil.getLoginIdAsString(),
                current,size,taskType,taskStatus,requireManual,orderBy,orderType);
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
    @RequireRole
    Result<IPage<UserRechargeOrderEntity>> getRechargeOrderList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(defaultValue = "TEXT") String taskType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Boolean requireManual,
            @RequestParam(defaultValue = "time") String orderBy,
            @RequestParam(defaultValue = "desc") String orderType){
            //只能查询自己userId的订单。
        return userCommonService.getRechargeOrderList( StpUtil.getLoginIdAsString(),
                current,size,taskType,taskStatus,requireManual,orderBy,orderType);
    }

    /**
     * 申诉aicall任务
     *
     * @param orderId 订单号
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/aitask/appeal")
    @ApiRateLimit(interFace = "appealAICallTask", ipLimit = 10)
    @RequireRole
    Result<Void> appealAICallTask(@RequestParam long orderId){
        //只能申诉到自己的生成任务。
        return userCommonService.appealAICallTask(StpUtil.getLoginIdAsString(),orderId);
    }

    /**
     * 申诉充值订单
     *
     * @param orderId 订单号
     * @return {@link Result }<{@link Void }>
     */
    @PostMapping("/rechargeorder/appeal")
    @ApiRateLimit(interFace = "appealRechargeOrder", ipLimit = 10)
    @RequireRole
    Result<Void> appealRechargeOrder(@RequestParam long orderId){
        //只能申诉到自己的充值订单。
        return userCommonService.appealRechargeOrder(StpUtil.getLoginIdAsString(),orderId);
    }

    /**
     * 切换aicall任务私有
     *
     * @param taskId    任务ID
     * @param isPrivate 是私密
     * @return {@link Result }<{@link Void }>
     */
    @PutMapping("/aitask/private")
    @ApiRateLimit(interFace = "toggleAICallTaskPrivate")
    @RequireRole
    Result<Void> toggleAICallTaskPrivate(
            @RequestParam long taskId,
            @RequestParam Boolean isPrivate
    ){
        //只能修改自己的生成任务的隐私状态。
        return userCommonService.toggleAICallTaskPrivate(taskId,StpUtil.getLoginIdAsString(),
                isPrivate);
    }
}
