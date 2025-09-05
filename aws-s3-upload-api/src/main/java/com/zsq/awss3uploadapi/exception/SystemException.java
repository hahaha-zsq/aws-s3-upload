package com.zsq.awss3uploadapi.exception;


import com.zsq.awss3uploadapi.enums.ResultCodeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SystemException extends RuntimeException {

    //异常状态码
    private Integer code;
    private String message;

    /**
     * 通过状态码和错误消息创建异常对象
     *
     * @param code    密码
     * @param message 消息
     */
    public SystemException(Integer code, String message) {
        this.message = message;
        this.code = code;
    }


    /**
     * 接收枚举类型对象
     *
     * @param resultCodeEnum 结果代码枚举
     */
    public SystemException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMessage());
        this.code = resultCodeEnum.getCode();
    }

}
