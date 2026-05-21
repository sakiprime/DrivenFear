package com.sakiprime.DrivenFear.annotation;


import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    boolean needLogin() default true;
    String role() default "user";
}
