package com.sakiprime.DrivenFear.service.userfile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.common.util.RoleUtil;
import com.sakiprime.DrivenFear.entity.*;
import com.sakiprime.DrivenFear.mapper.*;
import com.sakiprime.DrivenFear.service.aicall.impl.ImageTaskStrategy;
import com.sakiprime.DrivenFear.service.aicall.impl.TextTaskStrategy;
import com.sakiprime.DrivenFear.service.aicall.impl.VideoTaskStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.hash.Jackson2HashMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.sakiprime.DrivenFear.service.aigallery.AIGalleryServiceImpl.ALLOWED_TASK_TYPES;

/**
 * 用户管理服务实施
 *
 * @author 凋零
 * @since 2026/05/06
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminServiceImpl implements UserAdminService {
    private final UserMapper userMapper;
    private final RechargePackageMapper rechargePackageMapper;
    private final AIModelConfigMapper aiModelConfigMapper;
    private final AICallTaskMapper aiCallTaskMapper;
    private final UserRechargeMapper userRechargeMapper;
    private final TextTaskStrategy textTaskStrategy;
    private final ImageTaskStrategy imageTaskStrategy;
    private final VideoTaskStrategy videoTaskStrategy;
    private final UserCommonService userCommonService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Jackson2HashMapper hashMapper = new Jackson2HashMapper(objectMapper, false);
    private static final String DASHBOARD_CACHE_KEY = "admin:dashboard";
    private static final long DASHBOARD_CACHE_TTL_SEC = 60;
    private static final List<String> ALLOWED_TASK_STATUSES = List.of(
            "SUCCESS", "FAILED", "PENDING", "PROCESSING"
    );

    /**
     * 获取用户页面
     *
     * @param current               当前
     * @param size                  尺寸
     * @param hasTaskNeedManual     有任务需要人工
     * @param hasPayOrderNeedManual 有支付订单需要人工
     * @param isBanned              封禁状态
     * @param orderBy               按...排序
     * @param orderType             订单类型
     * @return {@link Result }<{@link IPage }<{@link UserEntity }>>
     */
    @Override
    public Result<IPage<UserEntity>> getUserPage(
            long current, long size,
            Boolean hasTaskNeedManual, Boolean hasPayOrderNeedManual, Boolean isBanned,
            String orderBy, String orderType) {

        current = Math.min(Math.max(current, 1), 500);
        size = Math.min(Math.max(size, 1), 50);

        orderBy = ("userId".equals(orderBy) || "createTime".equals(orderBy) || "updateTime".equals(orderBy))
                ? orderBy : "userId";
        orderType = ("asc".equals(orderType) || "desc".equals(orderType)) ? orderType : "desc";

        Page<UserEntity> page = new Page<>(current, size);

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        //排除密码。太过优雅。
        wrapper.select(UserEntity.class, field -> !"password".equals(field.getProperty()));

        if (Boolean.TRUE.equals(hasTaskNeedManual)) {
            wrapper.eq(UserEntity::isHasTaskNeedManual, true);
        }
        if (Boolean.TRUE.equals(hasPayOrderNeedManual)) {
            wrapper.eq(UserEntity::isHasPayOrderNeedManual, true);
        }
        if (Boolean.TRUE.equals(isBanned)) {
            wrapper.eq(UserEntity::isBanned, true);
        }
        //三个排序索引分支。
        if ("userId".equals(orderBy)) {
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(UserEntity::getUserId);
            } else {
                wrapper.orderByDesc(UserEntity::getUserId);
            }
        }
        else if ("updateTime".equals(orderBy)) {
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(UserEntity::getUpdateTime);
            } else {
                wrapper.orderByDesc(UserEntity::getUpdateTime);
            }
        }
        else {
            if ("asc".equals(orderType)) {
                wrapper.orderByAsc(UserEntity::getCreateTime);
            } else {
                wrapper.orderByDesc(UserEntity::getCreateTime);
            }
        }
        IPage<UserEntity> infoResult;
        try {
            infoResult = userMapper.selectPage(page, wrapper);
        }
        catch (Exception e) {
            log.warn("查询用户列表失败。{}",e.getMessage());
            return Result.fail(500,"查询用户列表失败。");
        }
        return Result.success(infoResult);
    }

    /**
     * 根据ID获取用户
     *
     * @param userId 用户ID
     * @return {@link Result }<{@link UserEntity }>
     */
    @Override
    public Result<UserEntity> getUserById(String userId) {

        UserEntity user = userMapper.selectById(userId);
        if(user == null){
            return Result.fail(400,"不存在此用户。");
        }
        //user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 切换用户封禁
     *
     * @param userId   用户ID
     * @param isBanned 封禁状态
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> toggleUserBan(String userId, Boolean isBanned){

        Result<UserEntity> checkResult = getUserById(userId);
        if(checkResult.getCode() != 200){
            return Result.fail(400,"用户不存在。");
        }
        if(RoleUtil.isAdmin(checkResult.getData())){
            return Result.fail(403,"您无权封禁另一个管理员。");
        }
        //状态相同时无需更新。
        if(Objects.equals(checkResult.getData().isBanned(),isBanned)){
            return Result.success(null);
        }
        boolean success = new LambdaUpdateChainWrapper<>(userMapper)
                .eq(UserEntity::getUserId, userId)
                .set(UserEntity::isBanned, isBanned)
                .set(UserEntity::getUpdateTime, LocalDateTime.now())
                .update();
        if(success){
            log.info("将用户{}的账号状态切换为了{}",userId,isBanned);
            return Result.success(null);
        }
        log.warn("[需要人工核查]封禁用户失败。用户ID:{}",userId);
        return Result.fail(500,"封禁用户失败。");
    }

    /**
     * 调整用户Token
     *
     * @param userId 用户ID
     * @param token  Token
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> adjustUserToken(String userId, Integer token) {

        Result<UserEntity> checkResult = getUserById(userId);
        if(checkResult.getCode() != 200){
            return Result.fail(400,"用户不存在。");
        }
        if(Objects.equals(token, 0)){
            return Result.success(null);
        }
        if(token.compareTo(0) > 0){//增加点数时。
            if(userMapper.increaseTokenBalanceAtomic(userId,token)>0){
                log.info("成功给用户{}增加了{}点Token。",userId,token);
                userCommonService.refreshUserTokenRedisFromMySQL(userId);
                return Result.success(null);
            }
            log.warn("给用户{}增加{}点Token失败。",userId,token);
            return Result.fail(500,"增加用户Token点数失败。");
        }
        else{//扣除点数时。
            if(userMapper.increaseTokenBalanceAtomic(userId,token)>0){
                log.info("成功给用户{}扣除了{}点Token。",userId,-token);
                userCommonService.refreshUserTokenRedisFromMySQL(userId);
                return Result.success(null);
            }
            log.warn("给用户{}扣除{}点Token失败。",userId,token);
            return Result.fail(500,"扣除用户Token点数失败。");
        }
    }

    /**
     * 创建充值套餐
     *
     * @param packageEntity 包实体
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> createRechargePackage(RechargePackageEntity packageEntity){

        boolean insertSuccess =rechargePackageMapper.insert(packageEntity) > 0;
        if(insertSuccess){
            redisTemplate.delete("recharge:package:list");
            log.info("[Admin]创建了充值套餐{}",packageEntity.getPackageName());
            return Result.success("创建充值套餐成功。",null);
        }
        return Result.fail();
    }

    /**
     * 更新充值套餐
     *
     * @param packageEntity 包实体
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> updateRechargePackage(RechargePackageEntity packageEntity) {

        if (packageEntity.getId() == null) {
            return Result.fail(400, "套餐ID不能为空");
        }
        if (rechargePackageMapper.selectById(packageEntity.getId()) == null) {
            return Result.fail(400, "套餐ID对应的充值套餐不存在。");
        }
        if (rechargePackageMapper.updateById(packageEntity) > 0) {
            redisTemplate.delete(List.of("recharge:package:list", "recharge:package:" + packageEntity.getId()));
            log.info("[Admin]更新了充值套餐{}", packageEntity.getPackageName());
            return Result.success("更新充值套餐成功。", null);
        }
        return Result.fail(500, "更新充值套餐失败。");
    }

    /**
     * 切换充值套餐促销
     *
     * @param packageId 软件包ID
     * @param isOnSale  正在促销
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> toggleRechargePackageSale(Long packageId, Boolean isOnSale){

        if (rechargePackageMapper.selectById(packageId) == null) {
            return Result.fail(400,"packageId对应的充值套餐不存在。");
        }
        //这里用了updateLambda，故不再做状态相同校验。
        LambdaUpdateWrapper<RechargePackageEntity> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(RechargePackageEntity::getId, packageId);
        updateWrapper.set(RechargePackageEntity::getOnSale, isOnSale);

        if(rechargePackageMapper.update(null, updateWrapper) > 0){
            redisTemplate.delete(List.of("recharge:package:list", "recharge:package:" + packageId));
            log.info("[Admin]成功调整充值套餐{}的上架状态为{}",packageId,isOnSale);
            return Result.success("充值套餐删除成功。",null);
        }
        log.warn("调整充值套餐{}的上架状态为{}失败",packageId,isOnSale);
        return Result.fail(500,"调整上架状态失败。");
    }

    /**
     * 删除充值套餐
     *
     * @param packageId 软件包ID
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> deleteRechargePackage(Long packageId) {
        if(rechargePackageMapper.deleteById(packageId) > 0){
            redisTemplate.delete(List.of("recharge:package:list", "recharge:package:" + packageId));
            log.info("[Admin]删除了充值套餐{}。",packageId);
            return Result.success("充值套餐删除成功。",null);
        }
        log.warn("删除充值套餐{}失败。",packageId);
        return Result.fail(500,"删除充值套餐失败。");
    }

    /**
     * 获取模型配置列表
     *
     * @return {@link Result }<{@link List }<{@link AIModelConfigEntity }>>
     */
    @Override
    public Result<List<AIModelConfigEntity>> getAIModelConfigList(){

        return Result.success(aiModelConfigMapper.selectList(null));
    }

    /**
     * 保存或更新模型配置
     *
     * @param aiModelConfigEntity 人工智能模型配置实体
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> saveOrUpdateAIModelConfig(AIModelConfigEntity aiModelConfigEntity) {

        if(aiModelConfigMapper.insertOrUpdate(aiModelConfigEntity)){
            String modelType = aiModelConfigEntity.getModelType();
            boolean refreshed;
            switch (modelType != null ? modelType : "") {
                case "IMAGE" -> refreshed = imageTaskStrategy.refreshModelTemplateMap();
                case "VIDEO" -> refreshed = videoTaskStrategy.refreshModelTemplateMap();
                default -> refreshed = textTaskStrategy.refreshModelTemplateMap();
            }
            if(!refreshed){
                return Result.fail(500,"初始化模型配置表失败，已启用兜底配置，请立即重试。");
            }
            log.info("[Admin]修改了模型{}的设置。",aiModelConfigEntity.getId());
            return Result.success("模型设置修改成功。",null);
        }
        log.warn("修改模型{}的设置失败。",aiModelConfigEntity.getId());
        return Result.fail(500,"修改模型设置失败。");
    }

    /**
     * 删除人工智能模型
     *
     * @param modelId 模型ID
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> deleteAIModel(Long modelId) {

        if(aiModelConfigMapper.deleteById(modelId) > 0){
            if(!(textTaskStrategy.refreshModelTemplateMap() && imageTaskStrategy.refreshModelTemplateMap() && videoTaskStrategy.refreshModelTemplateMap())){
                return Result.fail(500,"初始化模型配置表失败，已启用兜底配置，请立即重试。");
            }
            log.info("[Admin]删除了模型{}设置。",modelId);
            return Result.success("模型设置删除成功。",null);
        }
        log.warn("删除模型{}设置失败。",modelId);
        return Result.fail(500,"删除模型设置失败。");
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
    @Override
    public Result<IPage<AICallTaskEntity>> getAICallTaskList(
            long current, long size, String taskType, String taskStatus,Boolean requireManual,
            String orderBy, String orderType
            ) {

        current = Math.min(Math.max(current, 1), 500);
        size = Math.min(Math.max(size, 1), 50);

        orderBy = ("heat".equals(orderBy) || "time".equals(orderBy)) ? orderBy : "heat";
        orderType = ("asc".equals(orderType) || "desc".equals(orderType)) ? orderType : "desc";

        Page<AICallTaskEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<AICallTaskEntity> wrapper = new LambdaQueryWrapper<>();

        if (taskStatus != null && !taskStatus.isBlank()) {
            if (!ALLOWED_TASK_STATUSES.contains(taskStatus)) {
                taskStatus = "SUCCESS";
            }
            wrapper.eq(AICallTaskEntity::getTaskStatus, taskStatus);
        }

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
    @Override
    public Result<IPage<UserRechargeOrderEntity>> getRechargeOrderList(long current, long size, String taskType,
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

        wrapper.eq(UserRechargeOrderEntity::getTradeStatus, taskStatus);

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

    /**
     * 获取软件包信息列表
     *
     * @return {@link Result }<{@link List }<{@link RechargePackageEntity }>>
     */
    @Override
    public Result<List<RechargePackageEntity>> getPackageInfoList() {
        //这个方法相较于AliPay里面获取充值套餐的方法的不同点在于：可以获取未上架的充值套餐。
        return  Result.success(rechargePackageMapper.selectList(null));
    }

    /**
     * 切换aicall任务私有管理员
     *
     * @param taskId    任务ID
     * @param userId    用户ID
     * @param isPrivate 是私密
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> toggleAICallTaskPrivateAdmin(Long taskId, String userId, Boolean isPrivate) {

        if (Boolean.FALSE.equals(isPrivate)) {
            return Result.fail(400, "管理员不能将私有任务设置为公开");
        }

        if(aiCallTaskMapper.updateTaskIsPrivate(taskId,userId,isPrivate)>0){
            log.info("[Admin]更新了用户{}的生成任务{}的隐私状态为{}",taskId,userId,isPrivate);
            return Result.success("任务隐私状态已更新",null);
        }
        return Result.fail(400, "任务不存在");
    }

    /**
     * 切换充电订单申诉
     *
     * @param userId   用户ID
     * @param orderId  订单号
     * @param isAppeal 是申诉
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> toggleRechargeOrderAppealAdmin(String userId, Long orderId, Boolean isAppeal) {

        UserRechargeOrderEntity rechargeOrder = userRechargeMapper.selectById(orderId);
        if(rechargeOrder != null && rechargeOrder.getUserId().equals(userId)) {
            if(Objects.equals(rechargeOrder.getRequireManual(),isAppeal)) {
                return Result.fail(409,"支付订单申诉状态已是对应状态，无需修改。");
            }
            if(userRechargeMapper.updateRechargeOrderRequireManual(orderId,isAppeal)>0){
                log.info("[Admin]修改了用户{}的支付订单{}的申诉状态为{}",orderId,userId,isAppeal);
                return Result.success("修改支付订单申诉状态成功。",null);
            }
        }
        return Result.fail(400,"修改申诉状态失败，提交的订单号有误。");
    }

    /**
     * 切换aicall任务申诉
     *
     * @param userId   用户ID
     * @param orderId  订单号
     * @param isAppeal 是申诉
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> toggleAICallTaskAppealAdmin(String userId, Long orderId, Boolean isAppeal) {

        AICallTaskEntity callTask =aiCallTaskMapper.selectById(orderId);
        if(callTask != null && callTask.getUserId().equals(userId)) {
            if(Objects.equals(callTask.getRequireManual(),isAppeal)) {
                return Result.fail(409,"生成任务申诉状态已是对应状态，无需修改。");
            }
            if(aiCallTaskMapper.updateTaskRequireManual(orderId,true)>0){
                log.info("[Admin]修改了用户{}的生成任务{}的申诉状态为{}",orderId,userId,isAppeal);
                return Result.success("修改生成任务申诉状态成功。",null);
            }
        }
        return Result.fail(400,"修改申诉状态失败，提交的订单号有误。");
    }

    /**
     * 获取仪表板
     *
     * @return {@link Result }<{@link DashboardVO }>
     */
    @Override
    @SuppressWarnings("unchecked")
    public Result<DashboardVO> getDashboard() {
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(DASHBOARD_CACHE_KEY);
            if (!cached.isEmpty()) {
                Map<String, Object> hash = (Map<String, Object>) (Map<?, ?>) cached;
                DashboardVO vo = objectMapper.convertValue(hash, DashboardVO.class);
                log.info("[缓存命中]在Redis查询了Dashboard");
                return Result.success(vo);
            }
        } catch (Exception e) {
            log.warn("读取Dashboard在Redis缓存失败", e);
        }

        DashboardVO vo;
        try {
            vo = aiCallTaskMapper.selectDashboard();
            log.info("在MySQL查询了Dashboard");
        } catch (Exception e) {
            log.error("[缓存未命中]Dashboard在MySQL查询异常", e);
            return Result.fail(500, "获取概览数据失败");
        }

        try {
            Map<String, Object> hash = hashMapper.toHash(vo);
            redisTemplate.opsForHash().putAll(DASHBOARD_CACHE_KEY, hash);
            redisTemplate.expire(DASHBOARD_CACHE_KEY, DASHBOARD_CACHE_TTL_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("回写Dashboard在Redis缓存失败", e);
        }

        return Result.success(vo);
    }
}
