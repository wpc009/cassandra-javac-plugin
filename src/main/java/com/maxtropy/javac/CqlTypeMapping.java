package com.maxtropy.javac;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.*;

public enum CqlTypeMapping {
    ASCII(String.class),
    BIGINT(Long.class),
    BLOB(ByteBuffer.class),
    BOOLEAN(Boolean.class),
    COUNTER(Long.class),
    DATE(LocalDate.class),
    DECIMAL(BigDecimal.class),
    DOUBLE(Double.class),
    FLOAT(Float.class),
    INET(InetAddress.class),
    INT(Integer.class),
    LIST(List.class),
    MAP(Map.class),
    SET(Set.class),
    SMALLINT(Short.class),
    TEXT(String.class),
    TIMESTAMP(Date.class),
    TIMEUUID(java.util.UUID.class),
    TINYINT(Byte.class),
    VARINT(BigInteger.class),
    UUID(UUID.class),
    VARCHAR(String.class)
    ;

    private static Map<Class<? extends Object>,String> _reverseMapping = new HashMap<>(20);


    static {
        for(CqlTypeMapping tpe:CqlTypeMapping.values()){
            CqlTypeMapping._reverseMapping.put(tpe.javaType,tpe.name().toLowerCase());
        }
    }


    private Class<? extends Object> javaType;

    CqlTypeMapping(Class<? extends Object> javaType) {
        this.javaType = javaType;
    }


    public static void addType(String name,Class<? extends Object> javaType){
        _reverseMapping.put(javaType,name);
    }

    public static Optional<String> resolveType(Class<? extends Object> javaType){
        if(_reverseMapping.containsKey(javaType)){
            return Optional.of(_reverseMapping.get(javaType));
        }else{
            return Optional.empty();
        }
    }
}
