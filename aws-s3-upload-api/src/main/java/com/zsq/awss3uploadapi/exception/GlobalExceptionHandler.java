package com.zsq.awss3uploadapi.exception;


import com.zsq.awss3uploadapi.entity.Result;
import com.zsq.awss3uploadapi.enums.ResultCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

//AOP 面向切面
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class) //异常处理器
    public Result<?> error(Exception e) {
        log.error(e.getMessage());
        return Result.fail(ResultCodeEnum.FAIL.getCode(), e.getMessage());
    }


    //自定义异常处理
    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> error(SystemException e) {
        log.error(e.getMessage());
        return Result.build(null, e.getCode(), e.getMessage());
    }


    /**
     * 前端传来的是form表单参数 或者 url参数，后端用对象接收
     *
     * @param e e
     * @return {@link Result}
     */
    @ExceptionHandler(BindException.class)
    @ResponseBody
    public Result<?> BindExceptionHandler(BindException e) {
        String message = e.getBindingResult().getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.joining());
        log.error(message);
        return Result.fail(ResultCodeEnum.FAIL.getCode(), message);
    }

    /**
     * 前端传来的是form表单参数 或者 url参数，后端用多个参数接收，在参数前加@NotBlank等注解
     *
     * @param e e
     * @return {@link Result}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseBody
    public Result<?> ConstraintViolationExceptionHandler(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).collect(Collectors.joining());
        log.error(message);
        return Result.fail(ResultCodeEnum.FAIL.getCode(), message);
    }

    /**
     * 前端传来的是Json数据，后端用对象接收
     * 处理请求参数格式错误 @RequestBody上validate失败后抛出的异常是MethodArgumentNotValidException异常。
     *
     * @param e e
     * @return {@link Result}<{@link ?}>
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<?> handler(MethodArgumentNotValidException e) {
        log.error("", e);        // 获取异常信息
        BindingResult exceptions = e.getBindingResult();        // 判断异常中是否有错误信息，如果存在就使用异常中的消息，否则使用默认消息
        StringBuilder stringBuilder = new StringBuilder();
        if (exceptions.hasErrors()) {
            List<ObjectError> errors = exceptions.getAllErrors();
            if (!errors.isEmpty()) {
                errors.forEach(err -> stringBuilder.append(err.getDefaultMessage()).append("; "));
                //log.error(stringBuilder.toString());
                // FieldError fieldError = (FieldError) errors.get(0);// 这里列出了全部错误参数，按正常逻辑，只需要第一条错误即可
            }
        }
        log.error(stringBuilder.toString());
        return Result.fail(ResultCodeEnum.FAIL.getCode(), stringBuilder.toString());
    }

    /**
     * 请求参数缺失的异常
     *
     * @param e 忽略参数异常
     * @return {@link Result}<{@link ?}>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> parameterMissingExceptionHandler(MissingServletRequestParameterException e) {
        log.error("请求参数异常", e);
        return Result.fail(ResultCodeEnum.REQUEST_PARAMETER_ERROR.getCode(), ResultCodeEnum.REQUEST_PARAMETER_ERROR.getMessage());
    }

    /**
     * 缺少请求体异常处理器
     *
     * @param e 缺少请求体异常
     * @return ResponseResult
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> parameterBodyMissingExceptionHandler(HttpMessageNotReadableException e) {
        log.error("参数体不能为空", e);
        return Result.fail(ResultCodeEnum.BODY_PARAMETER_ERROR.getCode(), ResultCodeEnum.BODY_PARAMETER_ERROR.getMessage());
    }

    /**
     * 请求方式不支持
     *
     * @param e e
     * @return {@link Result}<{@link ?}>
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    public Result<?> handleException(HttpRequestMethodNotSupportedException e) {
        log.error(e.getMessage(), e);
        return Result.fail(ResultCodeEnum.METHOD_ERROR.getCode(), ResultCodeEnum.METHOD_ERROR.getMessage());
    }


}
