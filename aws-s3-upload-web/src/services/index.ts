/**
 * 服务模块入口文件
 */

// HTTP 状态码枚举
export enum HttpCodeEnum {
  SUCCESS = 200,
  FAIL = 500,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  PARAM_ERROR = 400
}

// 上传相关状态码枚举
export enum HttpCodeUploadEnum {
  SUCCESS = 200,  // 成功
  FAIL = 500,     // 失败
  PROCESSING = 201, // 处理中
  UPLOAD_SUCCESS = 2001, // 上传成功
  UPLOADING = 2002, // 上传中
  NOT_UPLOADED = 2003, // 未上传
  UPLOAD_FILE_FAILED = 5001 // 文件上传失败
}

// 通用响应接口
export interface ResponseType<T = any> {
  code: number
  data: T
  message?: string
}

export * from './apis'