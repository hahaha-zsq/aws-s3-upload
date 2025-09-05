import axios, {AxiosError, AxiosRequestConfig} from 'axios'
import storage from './storage'
import env from '@/config'
import {Result} from '@/types/api'
import {message} from '@/components/antdGlobal'
import config from "@/config";
import {browserRouter} from "@/router";
import {removeFileExtension} from "@/utils/index";

console.log('config', env)
// 创建实例
const instance = axios.create({
    timeout: 20000,
    baseURL: config.baseApi,
    timeoutErrorMessage: '请求超时，请稍后再试',
})

// 添加请求映射表
const pendingMap = new Map<string, AbortController>()

// 生成请求标识键
const getPendingKey = (config: AxiosRequestConfig) => {
    const {url, method, params, data} = config
    return [url, method, JSON.stringify(params), JSON.stringify(data)].join('&')
}

// 添加请求到pendingMap
const addPending = (config: AxiosRequestConfig) => {
    const pendingKey = getPendingKey(config)
    const controller = new AbortController()
    config.signal = controller.signal
    if (!pendingMap.has(pendingKey)) {
        pendingMap.set(pendingKey, controller)
    }
}

// 移除请求从pendingMap
const removePending = (config: AxiosRequestConfig) => {
    const pendingKey = getPendingKey(config)
    if (pendingMap.has(pendingKey)) {
        const controller = pendingMap.get(pendingKey)
        controller?.abort()
        pendingMap.delete(pendingKey)
    }
}

// 请求拦截器
instance.interceptors.request.use(
    (config) => {
        removePending(config) // 检查是否存在重复请求，若存在则取消已发的请求
        addPending(config) // 把当前请求添加到pendingMap中
        // if (config.showLoading) showLoading()
        const token = storage.get('Authorization')
        const language = storage.get('Accept-Language')
        config.headers.Authorization = token ? token : ""
        config.headers["Accept-Language"] = language ? language : "zh-CN"
        return config
    },
    (error: AxiosError) => {
        return Promise.reject(error)
    }
)

// 响应拦截器
instance.interceptors.response.use(
    (response) => {
        removePending(response.config) // 从pendingMap中移除请求
        console.log(response)
        const data: Result = response.data
        // hideLoading()
        if (response.config.responseType === 'blob') return response
        // 和后端约定好，没用携带凭证的错误码，是这个错误码就重定向到登录页
        if ((data.code >= 400 && data.code <= 407) || data.code == 512) {
            message.error(data.message)
            storage.clear()
            browserRouter.navigate("/")
        } else if (data.code != 200) {
            message.error(data.message)
            return Promise.reject(data)
        }
        return data.data
    },
    error => {
        console.log(error)
        if (error.name === 'AbortError' || error.code === 'ERR_CANCELED') {
            console.log('重复请求被取消')
            return Promise.reject(new Error('重复请求被取消'))
        }else if(error.status === 400){
            message.error(error.response.data.message)
        }else{
            // hideLoading()
            message.error(error.message)
        }
        return Promise.reject(error.message)
    }
)
export default {
    get<T>(url: string, params?: object): Promise<T> {
        return instance.get(url, {params})
    },
    post<T>(url: string, params?: object): Promise<T> {
        return instance.post(url, params)
    },
    put<T>(url: string, params?: object): Promise<T> {
        return instance.put(url, params)
    },
    delete<T>(url: string, params?: object): Promise<T> {
        return instance.delete(url, params)
    },
    request<T>(config: AxiosRequestConfig): Promise<T> {
        return instance.request(config)
    },
    uploadFile(url: string, formData: FormData) {
        return instance.post(url, formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            },
            responseType: 'blob'
        })
    },

    downloadFile(url: string, method = 'get', fileName: string) {
        instance({
            url,
            method,
            responseType: 'blob'
        }).then(response => {
            console.log(response.headers)
            const str = response.headers['content-disposition']
            console.log(str)
            if (!response || !str) {
                return
            }
            let suffix = ''
            // 截取文件名和文件类型
            if (str.lastIndexOf('.')) {
                fileName = fileName ? removeFileExtension(fileName) : decodeURI(str.substring(str.indexOf('=') + 1, str.lastIndexOf('.')))
                suffix = str.substring(str.lastIndexOf('.'), str.length)
            }

            const blob = new Blob([response.data], {
                type: response.data.type
            })

            const link = document.createElement('a')
            link.style.display = 'none'
            link.href = URL.createObjectURL(blob)
            link.download = decodeURIComponent(fileName + suffix)
            document.body.append(link)
            link.click()
            document.body.removeChild(link)
            window.URL.revokeObjectURL(link.href)
        })
    }

}
