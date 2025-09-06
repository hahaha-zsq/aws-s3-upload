/**
 * API 服务实现
 */
import axios from 'axios'
import config from '@/config'
import type {ResponseType} from '../index'
import type {FilesType, TaskInfoVO} from './typing'

// 创建axios实例
const service = axios.create({
    baseURL: config.baseApi,
    timeout: 60000
})

// 请求拦截器
service.interceptors.request.use(
    (config) => {
        // 可以在这里添加token等认证信息
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截器
service.interceptors.response.use(
    (response) => {
        return response.data
    },
    (error) => {
        return Promise.reject(error)
    }
)

/**
 * 通过MD5检查文件是否存在
 * @param md5 文件MD5值
 * @returns 任务信息
 */
export const checkFileByMd5 = (md5: string): Promise<ResponseType<TaskInfoVO>> => {
    return service.get(`/bunUpload/multipart/check/${md5}`)
}

/**
 * 初始化分片上传
 * @param params 初始化参数
 * @returns 上传ID
 */
export const initMultPartFile = (params: {
    fileIdentifier: string
    totalSize: number
    chunkNum: number
    chunkSize: number
    fileName: string
}): Promise<ResponseType<string>> => {
    return service.post('/bunUpload/multipart/init', params)
}

/**
 * 合并文件分片
 * @param md5 文件MD5值
 * @returns 合并结果
 */
export const mergeFileByMd5 = (md5: string): Promise<ResponseType<string>> => {
    return service.post(`/bunUpload/multipart/merge/${md5}`)
}

/**
 * 上传单个文件（非分片）
 * @param file 文件对象
 * @returns 上传结果
 */
export const uploadSingleFile = (file: File): Promise<ResponseType<string>> => {
    const formData = new FormData()
    formData.append('file', file)
    return service.post('/bunUpload/singleUpload', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}

/**
 * 上传分片
 * @param file 分片文件
 * @param uploadId 上传ID
 * @param partNumber 分片序号
 * @returns 上传结果
 */
export const uploadPart = (file: File, uploadId: string, partNumber: number): Promise<ResponseType<any>> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('uploadId', uploadId)
    formData.append('partNumber', partNumber.toString())
    console.log(formData)
    return service.post('/bunUpload/multipart/uploadPart', formData)
}

/**
 * 获取文件列表
 * @param fileName 文件名（可选，支持模糊查询）
 * @returns 文件列表
 */
export const fetchFileList = (fileName?: string): Promise<ResponseType<FilesType[]>> => {
    const params = fileName ? {fileName} : {}
    return service.get('/bunUpload/files', {params})
}

/**
 * 删除文件
 * @param fileId 文件ID
 * @returns 删除结果
 */
export const deleteFile = (fileId: number): Promise<ResponseType<string>> => {
    return service.delete(`/bunUpload/files/${fileId}`)
}