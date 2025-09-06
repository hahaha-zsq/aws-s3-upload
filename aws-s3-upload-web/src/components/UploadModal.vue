<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import pLimit from 'p-limit'

import { convertFileSizeUnit, cutFile } from '../utils/file/file'
import { checkFileByMd5, initMultPartFile, mergeFileByMd5, uploadPart } from '../services/apis'
import { HttpCodeUploadEnum } from '../services'
import type { UploadFileInfoType } from '../services/apis/typing'
import { MerkleTree } from '../utils/file/MerkleTree'

const limit = pLimit(3)
const CHUNK_SIZE = 1024 * 1024 * 5

/** 分片上传时的 file 和相关信息 */
type ChunkFileType = {
  file: Blob
  partNumber: number
  uploadId: string
}

/** 表格数据类型 */
type FileTableDataType = {
  uid: string
  name: string
  size: number
  unitSize: string
  md5: string
  md5Progress: number
  progress: number
  file: File
  chunkCount: number
  /** 当前文件分片集合 */
  chunkFileList: Blob[]
  /** 已上传的文件大小总和（计算进度条） */
  uploadedSize: number
  /** 计算MD5中（加载中） | 等待上传 | 上传中  | 上传成功 | 上传失败 */
  status: 'preparation' | 'preupload' | 'uploading' | 'success' | 'error' | 'paused'
  /** 上传速度 */
  uploadSpeed?: number
  /** 上次上传时间 */
  lastUploadTime?: number
  /** 上传ID */
  uploadId?: string
}

//  文件上传过程中的多种状态
const tagMap = {
  preparation: { type: 'warning', text: 'MD5计算中' },
  preupload: { type: 'info', text: '等待上传' },
  uploading: { type: 'primary', text: '上传中' },
  success: { type: 'success', text: '上传成功' },
  error: { type: 'danger', text: '上传失败' },
  paused: { type: 'warning', text: '已暂停' }
}

const visible = defineModel<boolean>('visible')
const uploadRef = ref()
const isDragOver = ref(false)
const state = reactive<{ dataSource: FileTableDataType[] }>({
  dataSource: []
})

// 阻止滚动穿透
let originalBodyOverflow = ''
let originalBodyPaddingRight = ''

const preventBodyScroll = () => {
  const scrollBarWidth = window.innerWidth - document.documentElement.clientWidth
  originalBodyOverflow = document.body.style.overflow
  originalBodyPaddingRight = document.body.style.paddingRight
  
  document.body.style.overflow = 'hidden'
  document.body.style.paddingRight = `${scrollBarWidth}px`
}

const restoreBodyScroll = () => {
  document.body.style.overflow = originalBodyOverflow
  document.body.style.paddingRight = originalBodyPaddingRight
}

// ESC键关闭模态框
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && visible.value) {
    closeModal()
  }
}

// 监听模态框显示状态
watch(visible, (newVal) => {
  if (newVal) {
    preventBodyScroll()
    document.addEventListener('keydown', handleKeydown)
  } else {
    restoreBodyScroll()
    document.removeEventListener('keydown', handleKeydown)
  }
})

// 组件卸载时恢复滚动
onUnmounted(() => {
  if (visible.value) {
    restoreBodyScroll()
    document.removeEventListener('keydown', handleKeydown)
  }
})

// 消息提示函数
const showMessage = (message: string, type: 'success' | 'error' | 'warning' = 'success') => {
  const messageEl = document.createElement('div')
  messageEl.className = `message message-${type}`
  messageEl.textContent = message
  document.body.appendChild(messageEl)
  
  setTimeout(() => {
    messageEl.classList.add('message-fade-out')
    setTimeout(() => {
      document.body.removeChild(messageEl)
    }, 300)
  }, 3000)
}

// 确认对话框
const showConfirm = (message: string, title: string = '提示'): Promise<boolean> => {
  return new Promise((resolve) => {
    const overlay = document.createElement('div')
    overlay.className = 'confirm-overlay'
    
    const dialog = document.createElement('div')
    dialog.className = 'confirm-dialog'
    dialog.innerHTML = `
      <div class="confirm-header">${title}</div>
      <div class="confirm-content">${message}</div>
      <div class="confirm-actions">
        <button class="confirm-btn confirm-cancel">取消</button>
        <button class="confirm-btn confirm-ok">确定</button>
      </div>
    `
    
    overlay.appendChild(dialog)
    document.body.appendChild(overlay)
    
    const handleClick = (result: boolean) => {
      document.body.removeChild(overlay)
      resolve(result)
    }
    
    dialog.querySelector('.confirm-cancel')?.addEventListener('click', () => handleClick(false))
    dialog.querySelector('.confirm-ok')?.addEventListener('click', () => handleClick(true))
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) handleClick(false)
    })
  })
}

// 选择文件并计算 md5
const handleFileChange = async (file: File) => {
  const chunkCount = Math.ceil((file.size ?? 0) / CHUNK_SIZE)
  const uid = Date.now() + Math.random().toString(36).substr(2, 9)
  
  // 展示给 table的数据，部分参数用于初始化
  const dataItem: FileTableDataType = {
    uid,
    name: file.name,
    size: file.size ?? 0,
    unitSize: convertFileSizeUnit(file.size),
    md5: '',
    md5Progress: 0,
    progress: 0,
    chunkCount,
    file: file,
    status: 'preparation',
    chunkFileList: [],
    uploadedSize: 0
  }
  state.dataSource.push(dataItem)
  const i = state.dataSource.findIndex((item) => item.uid === dataItem.uid)
  
  try {
    // 采用多线程计算和默克尔树计算树根
    const chunks = await cutFile(file)
    const merkleTree = new MerkleTree(chunks.map((chunk) => chunk.hash))
    const md5 = merkleTree.getRootHash()
    const chunkFileList = chunks.map((chunk) => chunk.blob)

    // 更新数据和状态
    state.dataSource[i] = {
      ...state.dataSource[i],
      md5,
      chunkFileList,
      status: 'preupload'
    }
  } catch (error) {
    state.dataSource[i].status = 'error'
    showMessage('文件处理失败', 'error')
  }
}

// 文件选择处理
const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (files) {
    Array.from(files).forEach(file => {
      handleFileChange(file)
    })
  }
  // 清空input值，允许重复选择同一文件
  target.value = ''
}

// 拖拽上传处理
const handleDrop = (event: DragEvent) => {
  event.preventDefault()
  isDragOver.value = false
  const files = event.dataTransfer?.files
  if (files) {
    Array.from(files).forEach(file => {
      handleFileChange(file)
    })
  }
}

const handleDragOver = (event: DragEvent) => {
  event.preventDefault()
}

const handleDragEnter = (event: DragEvent) => {
  event.preventDefault()
  isDragOver.value = true
}

const handleDragLeave = (event: DragEvent) => {
  event.preventDefault()
  // 只有当离开整个拖拽区域时才设置为false
  const currentTarget = event.currentTarget as HTMLElement
  const relatedTarget = event.relatedTarget as Node
  if (!currentTarget?.contains(relatedTarget)) {
    isDragOver.value = false
  }
}

// 查询文件状态并上传
const onUpload = async () => {
  for (let i = 0; i < state.dataSource.length; i++) {
    // md5 未计算完成和正在上传的跳过（重复点击的情况）
    if (!state.dataSource[i].md5 || state.dataSource[i].status === 'uploading') continue
    if (state.dataSource[i].status === 'success') continue

    try {
      await uploadFile(i, state.dataSource[i])
    } catch (error: any) {
      console.error('文件上传失败:', error)
      
      // 检查是否是uploadId失效错误
      if (error?.message?.includes('上传ID已失效')) {
        console.log('检测到uploadId失效，将文件状态设为等待重新上传')
        state.dataSource[i].status = 'preupload'
        state.dataSource[i].progress = 0
        state.dataSource[i].uploadedSize = 0
        
        showMessage(`文件 ${state.dataSource[i].name} 的上传ID已失效，请重新开始上传`, 'warning')
      } else {
        state.dataSource[i].status = 'error'
        showMessage(`文件 ${state.dataSource[i].name} 上传失败: ${error?.message || '未知错误'}`, 'error')
      }
    }
  }
}

// 暂停上传
const pauseUpload = (index: number) => {
  state.dataSource[index].status = 'paused'
}

// 继续上传
const resumeUpload = async (index: number) => {
  await uploadFile(index, state.dataSource[index])
}

// 删除文件
const removeFile = (index: number) => {
  state.dataSource.splice(index, 1)
}

// 清空列表
const clearAll = async () => {
  try {
    const confirmed = await showConfirm('确定要清空所有文件吗？')
    if (confirmed) {
      state.dataSource = []
    }
  } catch {
    // 用户取消
  }
}

/**
 * 上传状态管理类
 */
class UploadManager {
  private index: number
  private item: FileTableDataType
  
  constructor(index: number, item: FileTableDataType) {
    this.index = index
    this.item = item
  }
  
  // 检查是否被暂停
  private isPaused(): boolean {
    return (state.dataSource[this.index] as any).status === 'paused'
  }
  
  // 更新文件状态
  private updateStatus(status: 'success' | 'preparation' | 'preupload' | 'uploading' | 'error' | 'paused', progress?: number) {
    state.dataSource[this.index].status = status
    if (progress !== undefined) {
      state.dataSource[this.index].progress = progress
    }
  }
  
  // 更新上传进度
  private updateProgress(uploadedSize: number, speed?: number) {
    const progress = Math.min(Math.floor((uploadedSize / this.item.size) * 100), 100)
    state.dataSource[this.index] = {
      ...state.dataSource[this.index],
      uploadedSize,
      progress,
      uploadSpeed: speed || 0,
      lastUploadTime: Date.now()
    }
  }
  
  // 检查文件状态（秒传检测）
  async checkFileStatus(): Promise<any> {
    try {
      const response = await checkFileByMd5(this.item.md5)
      
      if (this.isPaused()) return null
      
      if (response.code !== 200) {
        this.updateStatus('error')
        showMessage(`${this.item.name} 检查文件状态失败: ${response.code}`, 'error')
        return null
      }
      
      return response.data
    } catch (error) {
      console.error('检查文件状态失败:', error)
      this.updateStatus('error')
      showMessage(`${this.item.name} 检查文件状态失败`, 'error')
      return null
    }
  }
  
  // 处理秒传
  handleInstantUpload(): boolean {
    this.updateStatus('success', 100)
    showMessage(`${this.item.name} 秒传成功`, 'success')
    return true
  }
  
  // 处理上传失败
  handleUploadFailed(): boolean {
    this.updateStatus('error')
    showMessage(`${this.item.name} 文件上传失败`, 'error')
    return true
  }
  
  // 处理断点续传
  handleResumeUpload(): void {
    console.log(`${this.item.name} 正在上传中，继续断点续传`)
    showMessage(`${this.item.name} 检测到断点续传`, 'warning')
  }
  
  // 处理新上传
  handleNewUpload(): void {
    console.log(`${this.item.name} 未上传，开始上传`)
  }
  
  // 计算断点续传进度
  calculateResumeProgress(needUploadFile: any[]): void {
    const totalChunks = this.item.chunkCount
    const uploadedChunks = totalChunks - needUploadFile.length
    const initialProgress = Math.floor((uploadedChunks / totalChunks) * 100)
    
    if (uploadedChunks > 0) {
      this.updateProgress(uploadedChunks * CHUNK_SIZE)
      console.log(`断点续传: 已上传${uploadedChunks}/${totalChunks}个分片，进度${initialProgress}%`)
    }
  }
  
  // 合并文件
  async mergeFile(): Promise<boolean> {
    try {
      const { code } = await mergeFileByMd5(this.item.md5)
      if (code === 200) {
        if (!this.isPaused()) {
          this.updateStatus('success', 100)
          showMessage(`${this.item.name} 合并成功`, 'success')
          console.log(`文件上传完成: ${this.item.name}`)
        }
        return true
      } else {
        if (!this.isPaused()) {
          this.updateStatus('error')
          showMessage(`${this.item.name} 合并失败: ${code}`, 'error')
          console.error(`文件合并失败: ${this.item.name}, code: ${code}`)
        }
        return false
      }
    } catch (error) {
      console.error('合并文件失败:', error)
      if (!this.isPaused()) {
        this.updateStatus('error')
        showMessage(`${this.item.name} 合并失败`, 'error')
      }
      return false
    }
  }
  
  // 并发上传分片
  async uploadChunks(needUploadFile: any[]): Promise<boolean> {
    // plimit 并发上传
    const uploadLimit = needUploadFile.map((n) =>
      limit(() => uploadChunkUrl(n, this.index, this.item.size, this.item.file.type))
    )

    const results = await Promise.allSettled(uploadLimit)
    const errResults = results.filter((r) => r.status === 'rejected')

    // 检查最终状态
    if (this.isPaused()) {
      return false
    }

    // 检查是否有上传失败的分片
    if (errResults.length > 0) {
      console.warn(this.item.name + ' 上传失败的分片-----', errResults)
      
      // 检查是否是可重试的错误
      const hasRetryableErrors = errResults.some(result => {
        const error = result.reason
        return error && (error.message?.includes('network') || 
                        error.message?.includes('timeout') ||
                        error.message?.includes('Upload paused'))
      })
      
      if (hasRetryableErrors) {
        this.updateStatus('paused')
        showMessage(`${this.item.name} 上传中断，可点击继续上传`, 'warning')
      } else {
        this.updateStatus('error')
        showMessage(`${this.item.name} 上传失败，请重试`, 'error')
      }
      return false
    }
    
    return true
  }
  
  // 公开方法：检查是否暂停
  public checkPaused(): boolean {
    return this.isPaused()
  }
  
  // 公开方法：设置上传状态
  public setUploading(): void {
    this.updateStatus('uploading')
  }
}

/**
 * 重构后的上传处理方法
 */
const uploadFile = async (index: number, item: FileTableDataType) => {
  const uploadManager = new UploadManager(index, item)
  
  // 检查是否已被暂停
  if (uploadManager.checkPaused()) {
    return
  }
  
  // 1. 检查文件状态（秒传检测）
  const fileStatus = await uploadManager.checkFileStatus()
  if (!fileStatus) return
  
  // 2. 根据状态码处理不同情况
  if (fileStatus.code === HttpCodeUploadEnum.UPLOAD_SUCCESS) {
    return uploadManager.handleInstantUpload()
  }
  
  if (fileStatus.code === HttpCodeUploadEnum.UPLOAD_FILE_FAILED) {
    return uploadManager.handleUploadFailed()
  }
  
  if (fileStatus.code === HttpCodeUploadEnum.UPLOADING) {
    uploadManager.handleResumeUpload()
  } else if (fileStatus.code === HttpCodeUploadEnum.NOT_UPLOADED) {
    uploadManager.handleNewUpload()
  }
  
  // 3. 初始化分片信息
  const needUploadFile = await initSliceFile(item, fileStatus)
  console.log('需要上传的文件', needUploadFile)
  
  if (uploadManager.checkPaused()) return
  
  // 4. 计算断点续传进度
  uploadManager.calculateResumeProgress(needUploadFile)
  
  // 5. 如果所有分片都已上传，直接合并
  if (needUploadFile.length === 0) {
    return await uploadManager.mergeFile()
  }
  
  // 6. 设置上传状态
  uploadManager.setUploading()
  
  // 7. 并发上传分片
  const uploadSuccess = await uploadManager.uploadChunks(needUploadFile)
  if (!uploadSuccess) return
  
  // 8. 合并文件
  await uploadManager.mergeFile()
}

/**
 * 分片初始化管理器
 */
class ChunkInitializer {
  private item: FileTableDataType
  private initData: UploadFileInfoType
  
  constructor(item: FileTableDataType, initData: UploadFileInfoType) {
    this.item = item
    this.initData = initData || {}
  }
  
  // 获取或初始化uploadId
  private async getOrCreateUploadId(): Promise<string> {
    const existingUploadId = this.initData.uploadId || ''
    
    if (existingUploadId) {
      console.log(`${this.item.name}: 使用现有uploadId:`, existingUploadId)
      this.item.uploadId = existingUploadId
      return existingUploadId
    }
    
    // 初始化分片参数
    const param = {
      fileIdentifier: this.item.md5,
      totalSize: this.item.size,
      chunkNum: this.item.chunkCount,
      chunkSize: CHUNK_SIZE,
      fileName: this.item.name
    }
    
    console.log(`${this.item.name}: 开始初始化分片上传:`, param)
    const { code, data } = await initMultPartFile(param)
    console.log(`${this.item.name}: 初始化分片结果:`, code, data)
    
    if (code !== 200) {
      console.error(`${this.item.name}: 初始化分片失败，错误码:`, code)
      throw new Error(`初始化分片失败: ${code}`)
    }
    
    const uploadId = data || ''
    console.log(`${this.item.name}: 新生成的uploadId:`, uploadId)
    
    if (!uploadId) {
      console.error(`${this.item.name}: 获取uploadId失败，服务器返回空值`)
      throw new Error('获取uploadId失败')
    }
    
    this.item.uploadId = uploadId
    return uploadId
  }
  
  // 检查分片是否已上传
  private isChunkUploaded(partNumber: number, exitPartList: any[]): boolean {
    return exitPartList.some((uploadedPart: any) => {
      // 兼容不同的数据结构
      if (typeof uploadedPart === 'number') {
        return uploadedPart === partNumber
      }
      if (uploadedPart && uploadedPart.partNumber) {
        return uploadedPart.partNumber === partNumber
      }
      return false
    })
  }
  
  // 创建需要上传的分片列表
  private createUploadList(uploadId: string): ChunkFileType[] {
    const needUploadFile: ChunkFileType[] = []
    const exitPartList = this.initData.exitPartList || []
    
    // 如果没有已上传的分片，添加所有分片
    if (exitPartList.length === 0) {
      console.log(`${this.item.name}: 全新上传，添加所有${this.item.chunkCount}个分片`)
      
      this.item.chunkFileList.forEach((chunkFile, index) => {
        needUploadFile.push({ 
          file: chunkFile, 
          partNumber: index + 1,
          uploadId
        })
      })
      
      return needUploadFile
    }
    
    // 过滤已上传的分片
    console.log(`${this.item.name}: 断点续传，已上传${exitPartList.length}个分片`)
    
    this.item.chunkFileList.forEach((chunkFile, index) => {
      const partNumber = index + 1
      
      if (!this.isChunkUploaded(partNumber, exitPartList)) {
        needUploadFile.push({ 
          file: chunkFile, 
          partNumber,
          uploadId
        })
      }
    })
    
    console.log(`${this.item.name}: 需要上传${needUploadFile.length}个分片`)
    return needUploadFile
  }
  
  // 初始化分片上传
  async initialize(): Promise<ChunkFileType[]> {
    try {
      console.log(`${this.item.name}: 开始初始化分片，已上传分片:`, this.initData.exitPartList || [])
      
      // 1. 获取或创建uploadId
      const uploadId = await this.getOrCreateUploadId()
      
      // 2. 创建需要上传的分片列表
      const needUploadFile = this.createUploadList(uploadId)
      
      console.log(`${this.item.name}: 分片初始化完成，待上传分片数:`, needUploadFile.length)
      
      return needUploadFile
    } catch (error) {
      console.error(`${this.item.name}: 分片初始化失败:`, error)
      throw error
    }
  }
}

// 重构后的初始化分片文件函数
const initSliceFile = async (item: FileTableDataType, initData: UploadFileInfoType): Promise<ChunkFileType[]> => {
  const initializer = new ChunkInitializer(item, initData)
  return await initializer.initialize()
}

/**
 * 分片上传管理器
 */
class ChunkUploader {
  private chunkItem: ChunkFileType
  private fileIndex: number
  private totalSize: number
  private fileType: string
  
  constructor(chunkItem: ChunkFileType, fileIndex: number, totalSize: number, fileType: string) {
    this.chunkItem = chunkItem
    this.fileIndex = fileIndex
    this.totalSize = totalSize
    this.fileType = fileType
  }
  
  // 检查上传状态
  private checkUploadStatus(): void {
    if (state.dataSource[this.fileIndex].status === 'paused') {
      throw new Error('Upload paused')
    }
  }
  
  // 创建分片文件对象
  private createChunkFile(): File {
    return new File([this.chunkItem.file], `chunk-${this.chunkItem.partNumber}`, {
      type: this.fileType || 'application/octet-stream'
    })
  }
  
  // 更新上传进度
  private updateProgress(): void {
    const fileItem = state.dataSource[this.fileIndex]
    const newUploadedSize = fileItem.uploadedSize + this.chunkItem.file.size
    const newProgress = Math.floor((newUploadedSize / this.totalSize) * 100)
    
    // 计算上传速度
    const currentTime = Date.now()
    const lastTime = fileItem.lastUploadTime || currentTime
    const timeDiff = (currentTime - lastTime) / 1000 // 转换为秒
    const speed = timeDiff > 0 ? this.chunkItem.file.size / timeDiff : 0
    
    // 更新文件状态
    state.dataSource[this.fileIndex] = {
      ...fileItem,
      uploadedSize: newUploadedSize,
      progress: Math.min(newProgress, 100), // 确保进度不超过100%
      uploadSpeed: speed,
      lastUploadTime: currentTime
    }
    
    console.log(`${fileItem.name}: 分片${this.chunkItem.partNumber}上传成功，进度: ${newProgress}%，速度: ${formatSpeed(speed)}`)
  }
  
  // 处理上传错误
  private handleUploadError(error: any): Error {
    const fileName = state.dataSource[this.fileIndex].name
    console.error(`${fileName}: 分片${this.chunkItem.partNumber}上传失败:`, error)
    
    // 特殊处理NoSuchUpload错误
    if (error?.response?.data?.message?.includes('NoSuchUpload') || 
        error?.message?.includes('NoSuchUpload') ||
        error?.response?.status === 404) {
      console.error(`${fileName}: 检测到NoSuchUpload错误，uploadId可能已失效:`, this.chunkItem.uploadId)
      
      // 清除当前文件的uploadId，强制重新初始化
      delete state.dataSource[this.fileIndex].uploadId
      console.log(`${fileName}: 已清除失效的uploadId，将在下次重试时重新初始化`)
      
      return new Error('上传ID已失效，请重新开始上传')
    }
    
    return error
  }
  
  // 上传分片
  async upload(): Promise<void> {
    try {
      // 1. 检查上传状态
      this.checkUploadStatus()
      
      // 2. 创建分片文件
      const file = this.createChunkFile()
      
      const fileName = state.dataSource[this.fileIndex].name
      console.log(`${fileName}: 开始上传分片 ${this.chunkItem.partNumber}，uploadId: ${this.chunkItem.uploadId}`)
      
      // 3. 调用上传接口
      const res = await uploadPart(file, this.chunkItem.uploadId, this.chunkItem.partNumber)
      
      console.log(`${fileName}: 分片${this.chunkItem.partNumber}上传结果`, res)
      
      if (res.code !== 200) {
        console.error(`${fileName}: 分片${this.chunkItem.partNumber}上传失败`, res)
        throw new Error(`分片上传失败: ${res.code}`)
      }
      
      // 4. 更新进度
      this.updateProgress()
      
    } catch (error) {
      throw this.handleUploadError(error)
    }
  }
}

// 重构后的分片上传函数
const uploadChunkUrl = (
  chunkItem: ChunkFileType,
  i: number,
  totalSize: number,
  type: string
): Promise<void> => {
  const uploader = new ChunkUploader(chunkItem, i, totalSize, type)
  return uploader.upload()
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
}

// 格式化上传速度
const formatSpeed = (speed: number): string => {
  if (speed === 0) return '0 B/s'
  const units = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  let unitIndex = 0
  let formattedSpeed = speed
  
  while (formattedSpeed >= 1024 && unitIndex < units.length - 1) {
    formattedSpeed /= 1024
    unitIndex++
  }
  
  return `${formattedSpeed.toFixed(1)} ${units[unitIndex]}`
}

// 获取文件扩展名
const getFileExtension = (fileName: string): string => {
  const lastDot = fileName.lastIndexOf('.')
  if (lastDot === -1) return 'FILE'
  const ext = fileName.substring(lastDot + 1).toLowerCase()
  return ext.length > 4 ? ext.substring(0, 4) : ext
}

// 格式化字节大小
const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let unitIndex = 0
  let formattedBytes = bytes
  
  while (formattedBytes >= 1024 && unitIndex < units.length - 1) {
    formattedBytes /= 1024
    unitIndex++
  }
  
  return `${formattedBytes.toFixed(1)} ${units[unitIndex]}`
}

// 格式化剩余时间
const formatETA = (remainingBytes: number, speed: number): string => {
  if (speed === 0) return '未知'
  const seconds = Math.ceil(remainingBytes / speed)
  
  if (seconds < 60) return `${seconds}秒`
  if (seconds < 3600) return `${Math.ceil(seconds / 60)}分钟`
  return `${Math.ceil(seconds / 3600)}小时`
}

// 计算统计信息
const completedCount = computed(() => {
  return state.dataSource.filter(item => item.status === 'success').length
})

const uploadingCount = computed(() => {
  return state.dataSource.filter(item => item.status === 'uploading').length
})

const totalUploadSpeed = computed(() => {
  return state.dataSource
    .filter(item => item.status === 'uploading' && item.uploadSpeed)
    .reduce((total, item) => total + (item.uploadSpeed || 0), 0)
})
</script>

<template>
  <div v-if="visible" class="modal-overlay" @click="closeModal">
    <div class="modal-dialog" @click.stop>
      <!-- 优化的模态框头部 -->
      <div class="modal-header">
        <div class="header-content">
          <div class="header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14,2 14,8 20,8"></polyline>
              <line x1="16" y1="13" x2="8" y2="13"></line>
              <line x1="16" y1="17" x2="8" y2="17"></line>
            </svg>
          </div>
          <div class="header-text">
            <h3>智能文件上传</h3>
            <p class="header-subtitle">支持大文件分片上传、断点续传和秒传功能</p>
          </div>
        </div>
        <div class="upload-stats" v-if="state.dataSource.length > 0">
          <div class="stat-card">
            <span class="stat-number">{{ state.dataSource.length }}</span>
            <span class="stat-label">总文件</span>
          </div>
          <div class="stat-card success">
            <span class="stat-number">{{ completedCount }}</span>
            <span class="stat-label">已完成</span>
          </div>
          <div class="stat-card primary" v-if="uploadingCount > 0">
            <span class="stat-number">{{ uploadingCount }}</span>
            <span class="stat-label">上传中</span>
          </div>
          <div class="stat-card speed" v-if="totalUploadSpeed > 0">
            <span class="stat-number">{{ formatSpeed(totalUploadSpeed) }}</span>
            <span class="stat-label">总速度</span>
          </div>
        </div>
        <button class="close-btn" @click="closeModal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
      
      <div class="modal-body">
        <div class="upload-container">
          <!-- 优化的上传区域 -->
          <div class="upload-section">
            <div 
              class="upload-drag"
              :class="{ 'drag-over': isDragOver, 'has-files': state.dataSource.length > 0 }"
              @drop="handleDrop"
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @dragenter="handleDragEnter"
            >
              <input 
                ref="uploadRef"
                type="file" 
                multiple 
                @change="handleFileSelect"
                style="display: none"
              >
              <div class="upload-content" @click="uploadRef?.click()">
                <div class="upload-icon-wrapper">
                  <svg class="upload-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="7,10 12,15 17,10"></polyline>
                    <line x1="12" y1="15" x2="12" y2="3"></line>
                  </svg>
                  <div class="upload-pulse"></div>
                </div>
                <div class="upload-text">
                  <h4>选择文件或拖拽到此处</h4>
                  <p class="upload-description">支持多文件同时上传，自动检测重复文件</p>
                </div>
                <div class="upload-features">
                  <div class="feature-tag">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
                    </svg>
                    断点续传
                  </div>
                  <div class="feature-tag">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"></circle>
                      <polyline points="12,6 12,12 16,14"></polyline>
                    </svg>
                    秒传检测
                  </div>
                  <div class="feature-tag">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                      <line x1="9" y1="9" x2="15" y2="15"></line>
                      <line x1="15" y1="9" x2="9" y2="15"></line>
                    </svg>
                    分片上传
                  </div>
                </div>
              </div>
            </div>

            <div class="upload-actions">
              <button class="btn btn-primary" @click="onUpload" :disabled="state.dataSource.length === 0">
                <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="17,8 12,3 7,8"></polyline>
                  <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
                开始上传
                <span v-if="state.dataSource.length > 0" class="btn-badge">{{ state.dataSource.length }}</span>
              </button>
              <button class="btn btn-secondary" @click="clearAll" :disabled="state.dataSource.length === 0">
                <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 6h18"></path>
                  <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                  <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                </svg>
                清空列表
              </button>
            </div>
          </div>

          <!-- 优化的文件列表 -->
          <div class="file-list-section" v-if="state.dataSource.length > 0">
            <div class="section-header">
              <div class="section-title">
                <h4>文件列表</h4>
                <span class="file-count-badge">{{ state.dataSource.length }} 个文件</span>
              </div>
              <div class="list-controls">
                <div class="scroll-hint" v-if="state.dataSource.length > 3">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M7 13l3 3 7-7"></path>
                    <path d="M7 6l3 3 7-7"></path>
                  </svg>
                  <span>可滚动查看</span>
                </div>
                <div class="view-toggle">
                  <button class="toggle-btn active" title="列表视图">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="8" y1="6" x2="21" y2="6"></line>
                      <line x1="8" y1="12" x2="21" y2="12"></line>
                      <line x1="8" y1="18" x2="21" y2="18"></line>
                      <line x1="3" y1="6" x2="3.01" y2="6"></line>
                      <line x1="3" y1="12" x2="3.01" y2="12"></line>
                      <line x1="3" y1="18" x2="3.01" y2="18"></line>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
            
            <div class="file-list">
              <div class="file-item" v-for="(item, index) in state.dataSource" :key="item.uid" :class="`status-${item.status}`">
                <div class="file-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                    <polyline points="14,2 14,8 20,8"></polyline>
                    <line x1="16" y1="13" x2="8" y2="13"></line>
                    <line x1="16" y1="17" x2="8" y2="17"></line>
                  </svg>
                  <div class="file-type-badge">{{ getFileExtension(item.name) }}</div>
                </div>
                
                <div class="file-info">
                  <div class="file-header">
                    <div class="file-name" :title="item.name">{{ item.name }}</div>
                    <div class="file-status">
                      <span class="status-indicator" :class="`status-${tagMap[item.status].type}`"></span>
                      <span class="status-text">{{ tagMap[item.status].text }}</span>
                    </div>
                  </div>
                  
                  <div class="file-meta">
                    <div class="meta-item">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
                      </svg>
                      <span>{{ item.unitSize }}</span>
                    </div>
                    <div class="meta-item">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                        <line x1="9" y1="9" x2="15" y2="15"></line>
                        <line x1="15" y1="9" x2="9" y2="15"></line>
                      </svg>
                      <span>{{ item.chunkCount }} 分片</span>
                    </div>
                    <div class="meta-item" v-if="item.md5">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                      </svg>
                      <span>{{ item.md5.substring(0, 8) }}...</span>
                    </div>
                    <div class="meta-item" v-if="item.status === 'uploading' && item.uploadSpeed">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="23,6 13.5,15.5 8.5,10.5 1,18"></polyline>
                        <polyline points="17,6 23,6 23,12"></polyline>
                      </svg>
                      <span class="upload-speed-text">{{ formatSpeed(item.uploadSpeed) }}</span>
                    </div>
                  </div>
                  
                  <div class="progress-section" v-if="item.progress > 0 || item.status === 'uploading'">
                    <div class="progress-container">
                      <div class="progress-bar">
                        <div 
                          class="progress-fill" 
                          :class="{ 
                            'progress-error': item.status === 'error',
                            'progress-success': item.status === 'success',
                            'progress-paused': item.status === 'paused'
                          }"
                          :style="{ width: item.progress + '%' }"
                        >
                          <div class="progress-shine" v-if="item.status === 'uploading'"></div>
                        </div>
                      </div>
                      <div class="progress-text">{{ item.progress }}%</div>
                    </div>
                    
                    <div class="progress-details">
                      <span class="size-info">{{ formatBytes(item.uploadedSize) }} / {{ item.unitSize }}</span>
                      <span v-if="item.status === 'uploading' && item.uploadSpeed" class="eta-info">
                        剩余: {{ formatETA(item.size - item.uploadedSize, item.uploadSpeed) }}
                      </span>
                    </div>
                  </div>
                </div>

                <div class="file-actions">
                  <div class="action-buttons">
                    <button
                      v-if="item.status === 'uploading'"
                      class="action-btn pause-btn"
                      @click="pauseUpload(index)"
                      title="暂停上传"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="6" y="4" width="4" height="16"></rect>
                        <rect x="14" y="4" width="4" height="16"></rect>
                      </svg>
                    </button>
                    
                    <button
                      v-else-if="item.status === 'paused' || item.status === 'error'"
                      class="action-btn resume-btn"
                      @click="resumeUpload(index)"
                      :title="item.status === 'paused' ? '继续上传' : '重试上传'"
                    >
                      <svg v-if="item.status === 'paused'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polygon points="5,3 19,12 5,21"></polygon>
                      </svg>
                      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="23,4 23,10 17,10"></polyline>
                        <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
                      </svg>
                    </button>
                    
                    <button
                      v-if="item.status === 'success'"
                      class="action-btn success-btn"
                      title="上传成功"
                      disabled
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="20,6 9,17 4,12"></polyline>
                      </svg>
                    </button>
                    
                    <button
                      class="action-btn remove-btn"
                      @click="removeFile(index)"
                      :disabled="item.status === 'uploading'"
                      title="移除文件"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3,6 5,6 21,6"></polyline>
                        <path d="m19,6v14a2,2 0 0,1 -2,2H7a2,2 0 0,1 -2,-2V6m3,0V4a2,2 0 0,1 2,-2h4a2,2 0 0,1 2,2v2"></path>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 模态框覆盖层 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease-out;
  overflow: hidden;
  overscroll-behavior: contain;
  touch-action: none;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* 模态框主体 */
.modal-dialog {
  background: white;
  border-radius: 24px;
  width: 90%;
  max-width: 900px;
  max-height: 90vh;
  overflow: hidden;
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.15);
  animation: modalSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  overscroll-behavior: contain;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-40px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 模态框头部 */
.modal-header {
  padding: 32px 32px 24px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  position: relative;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.header-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.3);
}

.header-icon svg {
  width: 24px;
  height: 24px;
}

.header-text h3 {
  margin: 0 0 4px 0;
  font-size: 1.75rem;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}

.header-subtitle {
  margin: 0;
  font-size: 1rem;
  color: #64748b;
  font-weight: 500;
}

.upload-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(226, 232, 240, 0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 80px;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.stat-number {
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
}

.stat-label {
  font-size: 0.75rem;
  color: #64748b;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-card.success .stat-number {
  color: #059669;
}

.stat-card.primary .stat-number {
  color: #3b82f6;
}

.stat-card.speed .stat-number {
  color: #8b5cf6;
}

.close-btn {
  position: absolute;
  top: 24px;
  right: 24px;
  background: rgba(148, 163, 184, 0.1);
  border: none;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  transform: scale(1.05);
}

.close-btn svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  display: flex;
  flex-direction: column;
}

.upload-container {
  width: 100%;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* 上传区域 */
.upload-section {
  padding: 32px;
}

.upload-drag {
  border: 2px dashed #cbd5e1;
  border-radius: 20px;
  padding: 48px 32px;
  text-align: center;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  position: relative;
  overflow: hidden;
  margin-bottom: 20px;
}

.upload-drag::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(102, 126, 234, 0.1), transparent);
  transition: left 0.6s;
}

.upload-drag:hover::before {
  left: 100%;
}

.upload-drag:hover,
.upload-drag.drag-over {
  border-color: #667eea;
  background: linear-gradient(135deg, #f0f4ff 0%, #e0e7ff 100%);
  transform: translateY(-4px);
  box-shadow: 0 20px 40px rgba(102, 126, 234, 0.15);
}

.upload-drag.has-files {
  padding: 32px;
}

.upload-content {
  position: relative;
  z-index: 1;
}

.upload-icon-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: 24px;
}

.upload-icon {
  width: 64px;
  height: 64px;
  color: #667eea;
  transition: all 0.4s ease;
}

.upload-drag:hover .upload-icon {
  color: #5a67d8;
  transform: scale(1.1);
}

.upload-pulse {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 80px;
  height: 80px;
  border: 2px solid #667eea;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  animation: pulse 2s infinite;
  opacity: 0;
}

.upload-drag:hover .upload-pulse {
  opacity: 0.6;
}

@keyframes pulse {
  0% {
    transform: translate(-50%, -50%) scale(0.8);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -50%) scale(1.4);
    opacity: 0;
  }
}

.upload-text h4 {
  margin: 0 0 8px 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: #1e293b;
}

.upload-description {
  margin: 0 0 32px 0;
  font-size: 1rem;
  color: #64748b;
  line-height: 1.5;
}

.upload-features {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}

.feature-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
  border-radius: 20px;
  font-size: 0.875rem;
  font-weight: 600;
  transition: all 0.3s ease;
}

.feature-tag:hover {
  background: rgba(102, 126, 234, 0.2);
  transform: translateY(-2px);
}

.feature-tag svg {
  width: 16px;
  height: 16px;
}

.upload-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  position: relative;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

.btn-primary {
  background: #409eff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #66b1ff;
}

.btn-secondary {
  background: #f4f4f5;
  color: #606266;
}

.btn-secondary:hover {
  background: #e9e9eb;
}

.btn-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #ef4444;
  color: white;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

/* 文件列表区域 */
.file-list-section {
  padding: 0 32px 32px;
  flex: 1;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-header h4 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: #1e293b;
}

.file-count-badge {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 12px;
  white-space: nowrap;
}

.scroll-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #64748b;
  font-size: 0.75rem;
  margin-right: 12px;
}

.scroll-hint svg {
  width: 14px;
  height: 14px;
  animation: scrollBounce 2s infinite;
}

@keyframes scrollBounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-3px);
  }
  60% {
    transform: translateY(-1px);
  }
}

.list-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-btn {
  background: none;
  border: 1px solid #e2e8f0;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toggle-btn:hover,
.toggle-btn.active {
  background: #667eea;
  border-color: #667eea;
  color: white;
}

.toggle-btn svg {
  width: 18px;
  height: 18px;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 8px;
  overflow: auto;
  height: 150px;
  padding-bottom: 20px;
}

/* 自定义滚动条样式 */
.file-list-section::-webkit-scrollbar {
  width: 6px;
}

.file-list-section::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 3px;
}

.file-list-section::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
  transition: background 0.3s ease;
}

.file-list-section::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.file-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  transition: all 0.3s ease;
  position: relative;
  overflow: visible;
  min-height: 120px;
}

.file-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  background: #e2e8f0;
  transition: all 0.3s ease;
}

.file-item.status-uploading::before {
  background: #3b82f6;
}

.file-item.status-success::before {
  background: #059669;
}

.file-item.status-error::before {
  background: #ef4444;
}

.file-item.status-paused::before {
  background: #f59e0b;
}

.file-item:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.08);
}

.file-icon {
  position: relative;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  flex-shrink: 0;
}

.file-icon svg {
  width: 24px;
  height: 24px;
}

.file-type-badge {
  position: absolute;
  bottom: -4px;
  right: -4px;
  background: #667eea;
  color: white;
  font-size: 0.625rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 16px;
  min-height: 2.8em;
}

.file-name {
  font-weight: 600;
  color: #1e293b;
  font-size: 1rem;
  word-break: break-all;
  line-height: 1.4;
  flex: 1;
  max-height: 2.8em;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.file-status {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e2e8f0;
}

.status-indicator.status-default {
  background: #94a3b8;
}

.status-indicator.status-primary {
  background: #3b82f6;
  animation: pulse-dot 2s infinite;
}

.status-indicator.status-success {
  background: #059669;
}

.status-indicator.status-danger {
  background: #ef4444;
}

.status-indicator.status-warning {
  background: #f59e0b;
}

@keyframes pulse-dot {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.status-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: #64748b;
}

.file-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  color: #64748b;
}

.meta-item svg {
  width: 14px;
  height: 14px;
}

.upload-speed-text {
  color: #059669;
  font-weight: 600;
}

.progress-section {
  margin-top: 12px;
}

.progress-container {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: #3b82f6;
  border-radius: 4px;
  transition: width 0.3s ease;
  position: relative;
  overflow: hidden;
}

.progress-fill.progress-success {
  background: #059669;
}

.progress-fill.progress-error {
  background: #ef4444;
}

.progress-fill.progress-paused {
  background: #f59e0b;
}

.progress-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  animation: shine 2s infinite;
}

@keyframes shine {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

.progress-text {
  font-size: 0.875rem;
  font-weight: 600;
  color: #1e293b;
  min-width: 40px;
  text-align: right;
}

.progress-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.75rem;
  color: #64748b;
}

.size-info {
  font-weight: 500;
}

.eta-info {
  color: #059669;
  font-weight: 600;
}

.file-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
  color: #64748b;
}

.action-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn svg {
  width: 18px;
  height: 18px;
}

.pause-btn:hover:not(:disabled) {
  background: #fef3c7;
  color: #d97706;
}

.resume-btn:hover:not(:disabled) {
  background: #dbeafe;
  color: #2563eb;
}

.success-btn {
  background: #dcfce7;
  color: #059669;
}

.remove-btn:hover:not(:disabled) {
  background: #fee2e2;
  color: #dc2626;
}

/* 消息提示样式 */
:global(.message) {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 20px;
  border-radius: 4px;
  color: white;
  font-size: 14px;
  z-index: 9999;
  animation: messageSlideIn 0.3s ease;
}

:global(.message-success) {
  background: #67c23a;
}

:global(.message-error) {
  background: #f56c6c;
}

:global(.message-warning) {
  background: #e6a23c;
}

:global(.message-fade-out) {
  animation: messageFadeOut 0.3s ease;
}

/* 确认对话框样式 */
:global(.confirm-overlay) {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}

:global(.confirm-dialog) {
  background: white;
  border-radius: 8px;
  padding: 24px;
  min-width: 300px;
  max-width: 500px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

:global(.confirm-header) {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

:global(.confirm-content) {
  color: #606266;
  margin-bottom: 20px;
  line-height: 1.5;
}

:global(.confirm-actions) {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

:global(.confirm-btn) {
  padding: 8px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

:global(.confirm-cancel) {
  background: white;
  color: #606266;
}

:global(.confirm-cancel:hover) {
  background: #f5f7fa;
  border-color: #c6e2ff;
}

:global(.confirm-ok) {
  background: #409eff;
  color: white;
  border-color: #409eff;
}

:global(.confirm-ok:hover) {
  background: #66b1ff;
  border-color: #66b1ff;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .modal-dialog {
    width: 95%;
    margin: 20px;
    border-radius: 20px;
    max-height: 95vh;
  }
  
  .modal-header {
    padding: 24px 20px 20px;
  }
  
  .header-content {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }
  
  .upload-stats {
    justify-content: center;
  }
  
  .upload-section {
    padding: 24px 20px;
  }
  
  .upload-drag {
    padding: 32px 20px;
  }
  
  .upload-features {
    flex-direction: column;
    gap: 8px;
  }
  
  .file-list-section {
    padding: 0 20px 24px;
    max-height: 40vh;
    min-height: 150px;
  }
  
  .file-item {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 16px;
    min-height: auto;
    overflow: visible;
  }
  
  .file-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    min-height: auto;
  }
  
  .file-name {
    max-height: none;
    -webkit-line-clamp: 3;
  }
  
  .file-meta {
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 12px;
  }
  
  .file-actions {
    align-items: center;
  }
}

@media (max-width: 480px) {
  .modal-dialog {
    width: 98%;
    margin: 10px;
    max-height: 98vh;
  }
  
  .upload-text h4 {
    font-size: 1.25rem;
  }
  
  .upload-description {
    font-size: 0.875rem;
  }
  
  .upload-icon {
    width: 48px;
    height: 48px;
  }
  
  .header-icon {
    width: 40px;
    height: 40px;
  }
  
  .header-icon svg {
    width: 20px;
    height: 20px;
  }
  
  .file-list-section {
    max-height: 35vh;
    min-height: 120px;
    padding: 0 16px 20px;
  }
  
  .file-item {
    padding: 12px;
    gap: 12px;
    min-height: auto;
  }
  
  .file-name {
    font-size: 0.9rem;
    max-height: none;
    -webkit-line-clamp: 4;
  }
  
  .file-meta {
    gap: 8px;
    justify-content: flex-start;
  }
  
  .meta-item {
    font-size: 0.75rem;
    min-width: fit-content;
  }
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@keyframes messageFadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}
</style>
