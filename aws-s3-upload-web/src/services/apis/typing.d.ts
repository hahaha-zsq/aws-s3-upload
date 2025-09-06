/**
 * API 类型定义
 */

// 上传文件信息类型
export interface UploadFileInfoType {
  // 上传ID，用于分片上传
  uploadId?: string
  // 原始文件名
  originFileName?: string
  // 文件大小（字节）
  size?: number
  // 分片大小（字节）
  chunkSize?: number
  // 分片数量
  chunkCount?: number
  // 文件MD5
  md5?: string
  // 文件内容类型
  contentType?: string
  // 已上传的分片列表
  exitPartList?: number[]
  // 分片上传URL列表
  urls?: string[]
}

// 任务信息VO
export interface TaskInfoVO {
  // 状态码
  code?: number
  // 上传ID
  uploadId?: string
  // 已上传的分片列表
  exitPartList?: number[]
  // 对象名称
  objectName?: string
  // 文件URL
  url?: string
}

// 文件列表类型
export interface FilesType {
  // 文件ID
  id: number
  // 原始文件名
  originFileName: string
  // 文件大小（字节）
  size: number
  // 文件URL
  url: string
  // 上传时间
  uploadTime: string
  // 文件MD5
  md5: string
}