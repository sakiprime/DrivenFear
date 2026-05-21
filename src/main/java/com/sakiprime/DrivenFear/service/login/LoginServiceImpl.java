package com.sakiprime.DrivenFear.service.login;

import cn.hutool.core.lang.Validator;
import com.sakiprime.DrivenFear.entity.UserDTO;
import com.sakiprime.DrivenFear.entity.UserEntity;
import com.sakiprime.DrivenFear.mapper.UserMapper;
import com.sakiprime.DrivenFear.common.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

/**
 * 登录服务实施
 *
 * @author 凋零
 * @since 2026/05/04
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginServiceImpl implements LoginService {
    private final UserMapper userMapper;

    /**
     * 合法性检查
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> LegalityCheck(UserDTO user){

        String userId = user.getUserId();
        String password = user.getPassword();
        String email = user.getEmail();
        //只再做一次兜底的空指针校验。正则检测被集成在UserDTO中。
        if(userId==null) return Result.fail(400,"账户名不能为空！");
        if(password==null) return Result.fail(400,"密码不能为空！");
        if(email==null) return Result.fail(400,"邮箱不能为空！");
        if(userMapper.selectById(user.getUserId()) != null){
            return Result.fail(409,"该用户名已被占用。");
        }
        //采用账号-邮箱一一对应的限制。
        if(userMapper.selectByEmail(user.getEmail()) != null){
            return Result.fail(409,"该邮箱已注册。");
        }
        return Result.success(null);
    }

    /**
     * 注册
     *
     * @param user 用户
     * @return {@link Result }<{@link Void }>
     */
    @Override
    public Result<Void> register(UserDTO user){
        if(userMapper.selectById(user.getUserId()) != null){
            return Result.fail(409,"该用户名已被占用。");
        }//兜底检查用户名是否被占用，防止覆盖。
        String hashpw = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hashpw);
        user.setUsername(user.getUserId());
        UserEntity userEntity = new UserEntity(user);
        if(userMapper.insert(userEntity)>0){
            log.info("注册信息入库成功，用户名：{}",userEntity.getUserId());
            return Result.success(null);
        }
        //insert在主键相同的数据行存在时会拒绝插入，因此是并发安全的。
        return Result.fail(500,"数据异常，请联系管理员。");
    }

    /**
     * 登录
     *
     * @param id       标识符
     * @param password 密码
     * @return {@link Result }<{@link UserEntity }>
     */
    @Override
    public Result<UserEntity> login(String id, String password){

        UserEntity user;
        if(Validator.isEmail(id)){
            user = userMapper.selectByEmail(id);
        }
        else {
            user = userMapper.selectById(id);
        }
        if(user == null || user.isBanned()){
            return Result.fail();
        }
        if(BCrypt.checkpw(password,user.getPassword())){
            user.setPassword(null);//去敏密码，虽然sessions存储在后端。
            return Result.success(user);
        }
        else {
            return Result.fail();
        }
    }


    /**
     * 通过用户ID获取邮箱
     *
     * @param userId 用户ID
     * @return {@link String }
     */
    @Override
    public String getEmailByUserId(String userId){

        return userMapper.selectEmailById(userId);
    }

    @Override
    public String getUserIdByEmail(String email){

        return userMapper.selectIdByEmail(email);
    }
}
