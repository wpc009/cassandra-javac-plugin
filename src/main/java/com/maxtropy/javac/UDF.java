package com.maxtropy.javac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
public @interface UDF {

    String name() default "";
    String keySpace();
    boolean isReplaceOld() default false;

    int version() default 1;

}
