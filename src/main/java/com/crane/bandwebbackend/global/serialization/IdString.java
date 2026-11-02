package com.crane.bandwebbackend.global.serialization;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// ID는 JSON 문자열로 내린다("id": "21"). Long, List<Long> 필드에 붙인다. 전역 변환은 개수·페이지 번호까지 바뀌어 쓰지 않는다(U2)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@JacksonAnnotationsInside
@JsonSerialize(using = IdStringSerializer.class)
public @interface IdString {}
