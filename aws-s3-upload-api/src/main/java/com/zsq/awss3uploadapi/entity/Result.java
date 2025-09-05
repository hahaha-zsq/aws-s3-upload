package com.zsq.awss3uploadapi.entity;

import cn.hutool.core.util.ObjectUtil;

import com.zsq.awss3uploadapi.enums.ResultCodeEnum;
import lombok.Data;
import org.springframework.util.ObjectUtils;
@Data
public class Result<T> {

    //状态码
    private Integer code;
    //信息
    private String message;
    private T data;

    //构造私有化
    private Result() {
    }

    //设置数据,返回对象的方法
    public static <T> Result<T> build(T data, Integer code, String message) {
        //创建Result对象，设置值，返回对象
        Result<T> result = new Result<>();
        //判断返回结果中是否需要数据
        if (ObjectUtil.isNotEmpty(data)) {
            //设置数据到result对象
            result.setData(data);
        }
        //设置其他值
        result.setCode(code);
        result.setMessage(message);
        //返回设置值之后的对象
        return result;
    }

    //设置数据,返回对象的方法
    public static <T> Result<T> build(T data, ResultCodeEnum resultCodeEnum) {
        //创建Result对象，设置值，返回对象
        Result<T> result = new Result<>();
        //判断返回结果中是否需要数据
        if (!ObjectUtils.isEmpty(data)) {
            //设置数据到result对象
            result.setData(data);
        }
        //设置其他值
        result.setCode(resultCodeEnum.getCode());
        result.setMessage(resultCodeEnum.getMessage());
        //返回设置值之后的对象
        return result;
    }

    //成功的方法
    public static <T> Result<T> ok(T data) {
        return build(data, ResultCodeEnum.SUCCESS.getCode(),ResultCodeEnum.SUCCESS.getMessage());
    }

    //失败的方法
    public static <T> Result<T> fail(T data) {
        return build(data, ResultCodeEnum.FAIL.getCode(),ResultCodeEnum.FAIL.getMessage());
    }

    public static <T> Result<T> fail(T data, ResultCodeEnum resultCodeEnum) {
        return build(data, resultCodeEnum);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        //创建Result对象，设置值，返回对象
        Result<T> result = new Result<>();
        //设置其他值
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
