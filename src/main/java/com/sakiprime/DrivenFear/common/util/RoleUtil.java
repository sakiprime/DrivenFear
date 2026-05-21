package com.sakiprime.DrivenFear.common.util;

import com.sakiprime.DrivenFear.entity.UserEntity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RoleUtil {

    public static Set<String> getRoleSet(UserEntity user) {
        if (user == null || user.getRoleCodes() == null) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(user.getRoleCodes().split(",")));
    }

    public static boolean hasRole(UserEntity user, String role) {
        return getRoleSet(user).contains(role);
    }
    //当用户是管理员的时候，无需进行额外权限判断。不过只有普通用户和管理员二元场景.....
    public static boolean isAdmin(UserEntity user) {
        return hasRole(user, "admin");
    }
}