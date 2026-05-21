package com.sakiprime.DrivenFear.service.userfile;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakiprime.DrivenFear.common.util.Result;
import com.sakiprime.DrivenFear.entity.AICallTaskEntity;
import com.sakiprime.DrivenFear.entity.UserDTO;
import com.sakiprime.DrivenFear.entity.UserEntity;
import com.sakiprime.DrivenFear.entity.UserRechargeOrderEntity;

import java.util.List;

public interface UserCommonService {
    boolean uploadAvatar(String userId, String avatarKey);
    Result<Void> uploadInfo(UserDTO toUpdateUser);
    UserEntity getUserById(String id);
    Result<UserEntity> refreshUserSession(String userId);
    Result<Void> handleSign(String userId);
    Result<Void> refreshUserTokenRedis(String userId);
    Result<Void> refreshUserTokenRedisFromMySQL(String userId);
    Result<Long> getUserTokenRedis(String userId);
    Result<IPage<AICallTaskEntity>> getAICallTaskList( String userId,
            long current, long size, String taskType, String taskStatus,Boolean requireManual,
            String orderBy, String orderType
    );
    Result<IPage<UserRechargeOrderEntity>> getRechargeOrderList( String userId,
            long current, long size, String taskType,
            String taskStatus, Boolean requireManual, String orderBy, String orderType);
    Result<Void> appealRechargeOrder(String userId, Long orderId);
    Result<Void> appealAICallTask(String userId, Long orderId);
    Result<Void> toggleAICallTaskPrivate(Long taskId, String userId, Boolean isPrivate);
}
