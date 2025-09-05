package com.zsq.awss3uploadapi.enums;

import lombok.Getter;

/**
 * 统一返回结果状态信息类
 */
@Getter
public enum ResultCodeEnum {

    // 下面的message属性的值全来自i18n配置文件
    SUCCESS(200, "ResultCodeEnum.success"),
    FAIL(400, "ResultCodeEnum.fail"),
    NOT_TOKEN(401,"ResultCodeEnum.notToken"),
    INVALID_TOKEN(402,"ResultCodeEnum.invalidToken"),
    TOKEN_TIMEOUT(403,"ResultCodeEnum.tokenTimeout"),
    BE_REPLACED(404,"ResultCodeEnum.beReplaced"),
    KICK_OUT(405,"ResultCodeEnum.kickOut"),
    NO_PREFIX_MESSAGE(406,"ResultCodeEnum.noPrefixMessage"),
    DEFAULT_MESSAGE(407,"ResultCodeEnum.defaultMessage"),
    METHOD_ERROR(408,"ResultCodeEnum.methodError"),
    REQUEST_PARAMETER_ERROR(409, "ResultCodeEnum.requestParameterError"),
    BODY_PARAMETER_ERROR(410, "ResultCodeEnum.bodyParameterError"),

    LOGIN_ERROR(500,"ResultCodeEnum.loginError"),
    ILLEGAL_REQUEST(600, "ResultCodeEnum.IllegalRequest"),


    USER_EXISTS_UNDER_THE_ROLE(701, "该角色下存在用户，请先重新分配角色或删除用户!"),
    BOARDCOLUMN_EXISTS_UNDER_THE_BOARD(701, "ResultCodeEnum.boardColumnExistsUnderTheBoard"),

    SERVICE_ERROR(2012, "服务异常"),
    DATA_ERROR(204, "数据异常"),

    REPEAT_SUBMIT(206, "重复提交"),
    LOGIN_AUTH(208, "未登陆"),
    PERMISSION(209, "没有权限"),





    ILLEGAL_CALLBACK_REQUEST_ERROR(217, "非法回调请求"),
    FETCH_ACCESSTOKEN_FAILD(218, "获取accessToken失败"),
    FETCH_USERINFO_ERROR(219, "获取用户信息失败"),
    SKU_LIMIT_ERROR(230, "购买个数不能大于限购个数"),
    NEED_LOGIN(401, "需要登录后操作"),
    NO_OPERATOR_AUTH(403, "无权限操作"),
    SYSTEM_ERROR(500, "执行过程异常，请重试！"),
    USERNAME_EXIST(501, "用户名已存在"),
    PHONENUMBER_EXIST(502, "手机号已被注册"),
    EMAIL_EXIST(503, "邮箱已存在"),
    REQUIRE_USERNAME(504, "必需填写用户名"),
    CONTENT_NOT_NULL(506, "评论内容不能为空"),
    ORINGINALPASSWOR_ERROR(507, "原密码错误"),
    CODE_valid(508, "验证码依然有效，请勿重复发送"),
    CODE_EXPIRE(509, "验证码过期或错误"),
    ICON_EXIST(510, "图标已存在"),
    COMMENT_CONTENT_OVER_LENGTH(511, "评论内容过多"),
    PARAMETER_ERROR(512, "参数不能为空"),
    USERNAME_NOT_NULL(513, "用户名不能为空!"),
    PASSWORD_NOT_NULL(514, "密码不能为空!"),
    ACCOUNT_FROZEN(515, "账户已冻结!"),

    IP_IS_BLOCKED(517, "ip已被封锁!"),
    JWT_EXPIRE_MES(401, "token过期"),
    JWT_SIGNER_MES(401, "验签失败"),


    UPLOAD_SUCCESS(2001, "上传成功"),
    UPLOADING(2002, "上传中"),
    NOT_UPLOADED(2003, "未上传"),
    UPLOAD_FILE_FAILED(5001, "文件上传失败");
    private Integer code;

    private String message;

    private ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
