package com.maxtropy.javac;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;
import java.util.function.Supplier;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
public @interface UDA{


    String name() default "";
    String keySpace();
    boolean isReplaceOld() default false;

    int version() default 1;

    String stateFunc();

    String finalFunc() default "";

    String initCond() ;



}
