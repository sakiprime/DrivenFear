package com.sakiprime.DrivenFear.service.userfile;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.common.util.TimeUtil;
import com.sakiprime.DrivenFear.entity.*;
import com.sakiprime.DrivenFear.mapper.AICallTaskMapper;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.mapper.UserRechargeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sakiprime.DrivenFear.service.aigallery.AIGalleryServiceImpl.ALLOWED_TASK_TYPES;


/**
 * 用户公共服务实施
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCommonServiceImpl implements UserCommonService {
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final AICallTaskMapper aiCallTaskMapper;
    private final UserRechargeMapper userRechargeMapper;
    //常量。真是优雅。双倍魔法数字给下一个人。
    private static final int SIGN_REWARD_TOKENS = 10;
    private static final int EXPIRE_TIME = 2 * 24 * 60 * 60;
    private static final List<String> ALLOWED_TASK_STATUSES = List.of(
            "SUCCESS", "FAILED", "PENDING", "PROCESSING"
    );

    /**
     * 上传头像
     *
     * @param userId    用户ID
     * @param avatarKey 头像键
     * @return boolean
     */
    @Override
    public boolean uploadAvatar(String userId, String avatarKey) {//更新数据库对象。
        if(userMapper.updateAvatarKeyAtomic(userId,avatarKey)==0){
            log.error("原子化更新头像地址失败，用户ID:{}",userId);
            return false;
        }
        return true;
    }

    /**
     * 上传信息
     *
     * @param toUpdateUser 更新用户
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> uploadInfo(UserDTO toUpdateUser){
        String userId = toUpdateUser.getUserId();
        String username = toUpdateUser.getUsername();
        if (username != null && !username.isBlank()) {
            if (!username.matches("^[a-zA-Z0-9\\u4e00-\\u9fa5]{1,20}$")) {
                return Result.fail(400,"用户名格式错误：支持20字内中文、字母、数字，不允许特殊字符");
            }
            if(userMapper.updateUsernameAtomic(userId, username)==0){
                log.error("原子化更新用户名失败，用户ID:{}",userId);
                return Result.fail(500,"系统繁忙，请稍后再试。");
            }
        }
        String phone = toUpdateUser.getPhone();
        if (phone != null && !phone.isBlank()) {
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                return Result.fail(400,"手机号格式不正确,仅支持中国大陆运营商手机号");
            }
            if(userMapper.updatePhoneAtomic(userId, phone)==0){
                log.error("原子化更新手机号失败，用户ID:{}",userId);
                return Result.fail(500,"系统繁忙，请稍后再试。");
            }
        }

        return Result.success(null);
    }

    /**
     * 按id获取用户
     *
     * @param id 标识符
     * @return {@link UserEntity }
     */
    @Override
    public UserEntity getUserById(String id){
        //其实在Controller已经去敏过一次咯！
        UserEntity user =userMapper.selectById(id);
        user.setPassword(null);
        return user;
    }


    /**
     * 刷新用户会话
     *
     * @param userId 用户ID
     * @return {@link Result }<{@link UserEntity }>
     *///接受用户ID，从数据库刷新其信息至缓存。
    @Override
    public Result<UserEntity> refreshUserSession(String userId){

        UserEntity doneUpdateUser = getUserById(userId);
        if(doneUpdateUser==null){
            log.error("session缓存刷新失败，用户ID:{}",userId);
            return Result.fail();
        }
        doneUpdateUser.setPassword(null);
        StpUtil.getSessionByLoginId(userId)
                .set("loginUser", doneUpdateUser);
        refreshUserTokenRedis(userId);
        return Result.success(doneUpdateUser);
    }

    /**
     * 签到
     *
     * @param userId 用户ID
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> handleSign(String userId){

        String monthDate = TimeUtil.nowMonth();
        long onlyDay = Long.parseLong(TimeUtil.nowOnlyDay()) ;
        String signKey = "sign:user:" + userId + ":month:" + monthDate;
        try {//可能返回null，要用Boolean包装类。先设置签到状态，杜绝刷代币风险。
            Boolean signed = redisTemplate.opsForValue().setBit(signKey, onlyDay, true);
            if (Boolean.TRUE.equals(signed)) {
                return Result.fail(409,"今日已签到，请勿重复操作");
            }
            if(userMapper.increaseTokenBalanceAtomic(userId,SIGN_REWARD_TOKENS)==0){
                log.warn("用户签到增加token失败。用户ID:{}",userId);
                redisTemplate.opsForValue().setBit(signKey, onlyDay, false);
                //可能没什么必要。但本就是极端场景性能开销很少。
                if (Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(signKey, onlyDay))) {
                    log.error("[需要人工核查]用户签到状态重置失败。用户ID:{}",userId);
                }
                return Result.fail(500, "系统繁忙，签到失败。");
            }
        } catch (Exception e) {
            log.error("用户签到Redis写入异常，userId:{}", userId, e);
            return Result.fail(500, "系统繁忙，签到失败。");
        }
        return Result.success("签到成功。",null);
    }

    @Override
    public Result<Void> refreshUserTokenRedisFromMySQL(String userId) {
        String key = "user:points:" + userId;
        for (int i = 0; i < 3; i++) {
            try {
                UserEntity user = userMapper.selectById(userId);
                if (user == null || user.getTokenBalance() == null) {
                    log.error("从MySQL刷新RedisToken缓存失败，用户不存在或token为空, userId:{}", userId);
                    continue;
                }
                redisTemplate.opsForValue().set(key, String.valueOf(user.getTokenBalance()), EXPIRE_TIME, TimeUnit.SECONDS);
                log.info("已从MySQL刷新用户{}在Redis的Token缓存:{}", userId, user.getTokenBalance());
                return Result.success(null);
            } catch (Exception e) {
                log.warn("从MySQL刷新用户{}在Redis的Token缓存失败，第{}次重试", userId, i, e);
            }
        }
        log.error("从MySQL刷新用户{}在Redis的Token缓存失败，重试耗尽", userId);
        return Result.fail(500, "缓存刷新失败");
    }

    @Override
    public Result<Long> getUserTokenRedis(String userId) {
        String key = "user:points:" + userId;
        try {
            String balance = redisTemplate.opsForValue().get(key);
            if (balance == null) {
                return Result.success(0L);
            }
            return Result.success(Long.parseLong(balance));
        } catch (Exception e) {
            log.warn("读取用户{}在Redis的Token缓存失败", userId, e);
            return Result.fail(500, "读取Token余额失败");
        }
    }

    @Override
    public Result<Void> refreshUserTokenRedis(String userId) {
        String key = "user:points:" + userId;
        Long tokenBalance;
        for (int i = 0; i < 3; i++) {
            try {
                tokenBalance = StpUtil.getSessionByLoginId(userId)
                        .getModel("loginUser",UserEntity.class).getTokenBalance();
                redisTemplate.opsForValue().set(key, String.valueOf(tokenBalance), EXPIRE_TIME, TimeUnit.SECONDS);
                log.info("成功刷新用户{}在Redis的Token数量缓存。",userId);
                return Result.success(null);
            }
            catch (Exception e) {
                log.warn("用户{}在Redis的Token数量缓存刷新失败。第{}次重试",userId,i);
            }
        }
        log.error("[需要人工核查]用户{}在Redis的Token数量缓存刷新失败。",userId);
        return Result.fail(500,"缓存刷新失败");
    }

    @Override
    public Result<IPage<AICallTaskEntity>> getAICallTaskList( String userId,
            long current, long size, String taskType, String taskStatus,Boolean requireManual,
            String orderBy, String orderType
    ) {

        current = Math.min(Math.max(current, 1), 200);
        size = Math.min(Math.max(size, 1), 50);

        orderBy = ("heat".equals(orderBy) || "time".equals(orderBy)) ? orderBy : "heat";
        orderType = ("asc".equals(orderType) || "desc".equals(orderType)) ? orderType : "desc";

        if (taskStatus != null && !taskStatus.isBlank()) {
            if (!ALLOWED_TASK_STATUSES.contains(taskStatus)) {
                taskStatus = "SUCCESS";
            }
        }
        else { //当taskStatus判空时。
            taskStatus = "SUCCESS";
        }

        Page<AICallTaskEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<AICallTaskEntity> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(AICallTaskEntity::getIsPrivate, false)
                .eq(AICallTaskEntity::getTaskStatus, taskStatus)
                .eq(AICallTaskEntity::getUserId, userId);

        if (Boolean.TRUE.equals(requireManual)) {
            wrapper.eq(AICallTaskEntity::getRequireManual, true);
        }

        if (taskType != null && !taskType.isBlank()) {
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

        return Result.success(aiCallTaskMapper.selectPage(page, wrapper));
    }

    @Override
    public Result<IPage<UserRechargeOrderEntity>> getRechargeOrderList( String userId,
            long current, long size, String taskType,
            String taskStatus, Boolean requireManual, String orderBy, String orderType) {
        current = Math.min(Math.max(current, 1), 500);
        size = Math.min(Math.max(size, 1), 50);

        if (!"amount".equals(orderBy) && !"tokens".equals(orderBy) && !"time".equals(orderBy)) {
            orderBy = "time";
        }
        orderType = ("asc".equals(orderType) || "desc".equals(orderType)) ? orderType : "desc";

        if (taskStatus != null && !taskStatus.isBlank()) {
            if (!ALLOWED_TASK_STATUSES.contains(taskStatus)) {
                taskStatus = "SUCCESS";
            }
        } else {
            taskStatus = "SUCCESS";
        }


        Page<UserRechargeOrderEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<UserRechargeOrderEntity> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(UserRechargeOrderEntity::getTradeStatus, taskStatus)
                .eq(UserRechargeOrderEntity::getUserId, userId);

        if (Boolean.TRUE.equals(requireManual)) {
            wrapper.eq(UserRechargeOrderEntity::getRequireManual, true);
        }

        if ("amount".equals(orderBy)) { //用支付金额来查询
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(UserRechargeOrderEntity::getPaymentAmount)
                        .orderByDesc(UserRechargeOrderEntity::getCreateTime);
            } else {
                wrapper.orderByDesc(UserRechargeOrderEntity::getPaymentAmount)
                        .orderByDesc(UserRechargeOrderEntity::getCreateTime);
            }
        } else if ("tokens".equals(orderBy)) { //用获得的token数来查询
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(UserRechargeOrderEntity::getTokensAmount)
                        .orderByDesc(UserRechargeOrderEntity::getCreateTime);
            } else {
                wrapper.orderByDesc(UserRechargeOrderEntity::getTokensAmount)
                        .orderByDesc(UserRechargeOrderEntity::getCreateTime);
            }
        } else {
            if ("asc".equals(orderType)) { //用订单创建时间来查询
                wrapper.orderByAsc(UserRechargeOrderEntity::getCreateTime);
            } else {
                wrapper.orderByDesc(UserRechargeOrderEntity::getCreateTime);
            }
        }

        return Result.success(userRechargeMapper.selectPage(page, wrapper));
    }

    @Override
    public Result<Void> appealRechargeOrder(String userId, Long orderId) {

        UserRechargeOrderEntity rechargeOrder = userRechargeMapper.selectById(orderId);
        if(rechargeOrder != null && rechargeOrder.getUserId().equals(userId)) {
            if(Boolean.TRUE.equals(rechargeOrder.getRequireManual())) {
                return Result.fail(409,"申诉失败，订单已提交过申诉请求。");
            }
            if(userRechargeMapper.updateRechargeOrderRequireManual(orderId,true)>0){
                log.info("用户{}申诉了支付订单{}",orderId,userId);
                return Result.success("申诉支付订单成功。",null);
            }
        }
        return Result.fail(400,"申诉失败，提交的订单号有误。");
    }

    @Override
    public Result<Void> appealAICallTask(String userId, Long orderId) {

        AICallTaskEntity callTask =aiCallTaskMapper.selectById(orderId);
        if(callTask != null && callTask.getUserId().equals(userId)) {
            if(Boolean.TRUE.equals(callTask.getRequireManual())) {
                return Result.fail(409,"申诉失败，任务已提交过申诉请求。");
            }
            if(aiCallTaskMapper.updateTaskRequireManual(orderId,true)>0){
                log.info("用户{}申诉了生成任务{}",orderId,userId);
                return Result.success("申诉生成任务成功。",null);
            }
        }
        return Result.fail(400,"申诉失败，提交的订单号有误。");
    }

    @Override
    public Result<Void> toggleAICallTaskPrivate(Long taskId, String userId, Boolean isPrivate) {
        //高频方法，采用单条SQL更新节省IO。
        if(aiCallTaskMapper.updateTaskIsPrivate(taskId,userId,isPrivate)>0){
            log.info("更新了用户{}的生成任务{}的隐私状态为{}",taskId,userId,isPrivate);
            return Result.success("任务隐私状态已更新",null);
        }
        return Result.fail(400, "任务不存在或无权操作");
    }
}
