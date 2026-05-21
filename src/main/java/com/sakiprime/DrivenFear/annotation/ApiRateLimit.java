package com.sakiprime.DrivenFear.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiRateLimit {
    String interFace();

    long ipLimit() default 15;

    long globalLimit() default 600;

    long expire() default 180;
}
