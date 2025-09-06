<script setup lang="ts">
import { reactive, onMounted, ref, computed } from 'vue'
import { convertFileSizeUnit } from '../utils/file/file'
import { chunkDownloadFile, fetchFileList } from '../services/apis'
import type { FilesType } from '../services/apis/typing'

const CHUNK_SIZE = 1024 * 1024 * 5

type DownloadStatus = {
  progress?: number
  status?: 'downloading' | 'pause' | 'error'
}
type FileDataType = FilesType & DownloadStatus

const state = reactive<{ dataSource: FileDataType[]; blobRef: Map<number, BlobPart[]> }>({
  dataSource: [],
  blobRef: new Map<number, BlobPart[]>()
})

const searchQuery = ref('')

const filteredFileList = computed(() => {
  if (!searchQuery.value) {
    return state.dataSource
  }
  return state.dataSource.filter(file => 
    file.originFileName.toLowerCase().includes(searchQuery.value.toLowerCase())
  )
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

onMounted(async () => {
  try {
    const { code, data } = await fetchFileList()
    if (code === 200) state.dataSource = data
  } catch (error) {
    showMessage('获取文件列表失败', 'error')
  }
})

// 下载文件为blob
const downloadFileByBlob = (blob: Blob, filename: string) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.style.display = 'none'
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

// 分片下载文件
const downloadFile = async (record: FileDataType) => {
  const index = state.dataSource.findIndex((item) => item.id === record.id)
  state.dataSource[index].status = 'downloading'
  state.dataSource[index].progress = 0

  const totalChunks = Math.ceil(record.size / CHUNK_SIZE)
  // 如果是暂停，根据已下载的，找到断点，偏移长度进行下载
  const offset = state.blobRef.get(record.id)?.length || 0

  try {
    for (let i = offset; i < totalChunks; i++) {
      // 暂停/错误 终止后续请求
      if (state.dataSource[index].status !== 'downloading') return

      const start = CHUNK_SIZE * i
      let end = CHUNK_SIZE * (i + 1) - 1
      if (end >= record.size) end = record.size - 1

      const res = await chunkDownloadFile(record.id, `bytes=${start}-${end}`)
      const currentDataBlob = state.blobRef.get(record.id) || []
      // 记录当前数据的分片 blob
      state.blobRef.set(record.id, [...currentDataBlob, res as unknown as BlobPart])
      state.dataSource[index].progress = Math.floor(((i + 1) / totalChunks) * 100)
    }

    if (state.dataSource[index].status === 'downloading') {
      state.dataSource[index].status = undefined
      state.dataSource[index].progress = undefined
      const blob = new Blob(state.blobRef.get(record.id))
      downloadFileByBlob(blob, record.originFileName)
      state.blobRef.delete(record.id)
      showMessage('文件下载完成', 'success')
    }
  } catch (error) {
    state.dataSource[index].status = 'error'
    showMessage('下载失败', 'error')
  }
}

// 暂停下载
const pauseDownload = (record: FileDataType) => {
  record.status = 'pause'
}

// 继续下载
const resumeDownload = (record: FileDataType) => {
  downloadFile(record)
}

// 取消下载
const cancelDownload = (record: FileDataType) => {
  record.status = undefined
  record.progress = undefined
  state.blobRef.delete(record.id)
}

// 获取文件扩展名
const getFileExtension = (fileName: string): string => {
  const lastDot = fileName.lastIndexOf('.')
  return lastDot > 0 ? fileName.substring(lastDot + 1).toUpperCase() : 'FILE'
}

// 刷新文件列表
const refreshList = async () => {
  try {
    const { code, data } = await fetchFileList()
    if (code === 200) state.dataSource = data
    showMessage('文件列表已刷新', 'success')
  } catch (error) {
    showMessage('刷新文件列表失败', 'error')
  }
}
</script>

<template>
  <div class="file-table-container">
    <!-- 表格头部 -->
    <div class="table-header">
      <div class="header-content">
        <div class="header-info">
          <div class="header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
            </svg>
          </div>
          <div class="header-text">
            <h3>文件管理</h3>
            <p v-if="state.dataSource.length > 0">共 {{ state.dataSource.length }} 个文件</p>
            <p v-else>暂无文件</p>
          </div>
        </div>
        <div class="header-actions">
          <div class="search-box">
            <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"></circle>
              <path d="m21 21-4.35-4.35"></path>
            </svg>
            <input 
              v-model="searchQuery" 
              type="text" 
              placeholder="搜索文件名..."
              class="search-input"
            />
          </div>
          <button @click="refreshList" class="refresh-btn">
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23,4 23,10 17,10"></polyline>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
            </svg>
            刷新
          </button>
        </div>
      </div>
    </div>
    
    <!-- 文件网格视图 -->
    <div class="files-grid" v-if="state.dataSource.length > 0">
      <div 
        v-for="row in state.dataSource" 
        :key="row.id" 
        class="file-card"
        :class="{ 'downloading': row.progress !== undefined }"
      >
        <div class="file-card-header">
          <div class="file-icon-wrapper">
            <div class="file-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14,2 14,8 20,8"></polyline>
              </svg>
            </div>
            <div class="file-type-badge">{{ getFileExtension(row.originFileName) }}</div>
          </div>
          <div class="file-actions">
            <!-- 未开始下载或下载错误 -->
            <button
              v-if="!row.status || row.status === 'error'"
              class="action-btn download-btn"
              @click="downloadFile(row)"
              :title="'下载 ' + row.originFileName"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="7,10 12,15 17,10"></polyline>
                <line x1="12" y1="15" x2="12" y2="3"></line>
              </svg>
            </button>
            
            <!-- 下载中 -->
            <template v-else-if="row.status === 'downloading'">
              <button class="action-btn pause-btn" @click="pauseDownload(row)" title="暂停下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="6" y="4" width="4" height="16"></rect>
                  <rect x="14" y="4" width="4" height="16"></rect>
                </svg>
              </button>
              <button class="action-btn cancel-btn" @click="cancelDownload(row)" title="取消下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            </template>
            
            <!-- 暂停状态 -->
            <template v-else-if="row.status === 'pause'">
              <button class="action-btn resume-btn" @click="resumeDownload(row)" title="继续下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="5,3 19,12 5,21"></polygon>
                </svg>
              </button>
              <button class="action-btn cancel-btn" @click="cancelDownload(row)" title="取消下载">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            </template>
          </div>
        </div>
        
        <div class="file-card-body">
          <div class="file-name" :title="row.originFileName">{{ row.originFileName }}</div>
          <div class="file-meta">
            <div class="meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              </svg>
              <span>{{ convertFileSizeUnit(row.size) }}</span>
            </div>
            <div class="meta-item">
               <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                 <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                 <polyline points="14,2 14,8 20,8"></polyline>
               </svg>
               <span :title="row.md5">MD5: {{ row.md5.substring(0, 8) }}...</span>
             </div>
          </div>
        </div>
        
        <!-- 下载进度 -->
        <div v-if="row.progress !== undefined" class="download-progress">
          <div class="progress-bar">
            <div 
              class="progress-fill" 
              :class="{ 'progress-error': row.status === 'error' }"
              :style="{ width: row.progress + '%' }"
            >
              <div class="progress-shine"></div>
            </div>
          </div>
          <div class="progress-info">
            <span class="progress-text">
              {{ row.status === 'error' ? '下载失败' : 
                  row.status === 'pause' ? '已暂停' : 
                  '下载中' }} {{ row.progress }}%
            </span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 空状态 -->
    <div v-if="state.dataSource.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
        </svg>
      </div>
      <h4>暂无文件</h4>
      <p>还没有上传任何文件，开始上传吧！</p>
    </div>
  </div>
</template>

<style scoped>
.file-table-container {
  width: 100%;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

/* 表格头部 */
.table-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 24px;
  color: white;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 48px;
  height: 48px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon svg {
  width: 24px;
  height: 24px;
}

.header-text h3 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
}

.header-text p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 12px;
  width: 16px;
  height: 16px;
  color: #666;
  z-index: 1;
}

.search-input {
  padding: 10px 12px 10px 36px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  color: #333;
  font-size: 14px;
  width: 200px;
  transition: all 0.3s ease;
}

.search-input:focus {
  outline: none;
  background: white;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}

.search-input::placeholder {
  color: #999;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.refresh-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-1px);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

/* 文件网格 */
.files-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  padding: 24px;
}

.file-card {
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;
  position: relative;
}

.file-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  border-color: #d9d9d9;
}

.file-card.downloading {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.file-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.file-icon-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  position: relative;
}

.file-icon svg {
  width: 24px;
  height: 24px;
}

.file-type-badge {
  position: absolute;
  bottom: -6px;
  right: -6px;
  background: #1890ff;
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  border: 2px solid white;
}

.file-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.download-btn {
  background: #e6f7ff;
  color: #1890ff;
}

.download-btn:hover:not(:disabled) {
  background: #1890ff;
  color: white;
  transform: scale(1.05);
}

.pause-btn {
  background: #fff7e6;
  color: #fa8c16;
}

.pause-btn:hover:not(:disabled) {
  background: #fa8c16;
  color: white;
  transform: scale(1.05);
}

.resume-btn {
  background: #f6ffed;
  color: #52c41a;
}

.resume-btn:hover:not(:disabled) {
  background: #52c41a;
  color: white;
  transform: scale(1.05);
}

.cancel-btn {
  background: #fff2f0;
  color: #ff4d4f;
}

.cancel-btn:hover:not(:disabled) {
  background: #ff4d4f;
  color: white;
  transform: scale(1.05);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
}

.file-card-body {
  padding: 16px;
}

.file-name {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}

.meta-item svg {
  width: 14px;
  height: 14px;
  color: #999;
}

.meta-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 下载进度 */
.download-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(24, 144, 255, 0.05);
  padding: 12px 16px;
  border-top: 1px solid rgba(24, 144, 255, 0.1);
}

.progress-bar {
  height: 4px;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 8px;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 2px;
  transition: width 0.3s ease;
  position: relative;
  overflow: hidden;
}

.progress-fill.progress-error {
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7875 100%);
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
  0% { left: -100%; }
  100% { left: 100%; }
}

.progress-info {
  display: flex;
  justify-content: center;
}

.progress-text {
  font-size: 12px;
  color: #1890ff;
  font-weight: 500;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #666;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  margin-bottom: 20px;
  color: #d9d9d9;
}

.empty-state h4 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #262626;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
  color: #8c8c8c;
}

/* 消息提示 */
:global(.message) {
  position: fixed;
  top: 20px;
  right: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  z-index: 9999;
  animation: messageSlideIn 0.3s ease;
  overflow: hidden;
}

:global(.message-success) {
  border-left: 4px solid #52c41a;
}

:global(.message-error) {
  border-left: 4px solid #ff4d4f;
}

:global(.message-warning) {
  border-left: 4px solid #faad14;
}

:global(.message-fade-out) {
  animation: messageFadeOut 0.3s ease forwards;
}

@keyframes messageSlideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes messageFadeOut {
  from {
    transform: translateX(0);
    opacity: 1;
  }
  to {
    transform: translateX(100%);
    opacity: 0;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    gap: 16px;
  }
  
  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
  
  .search-input {
    width: 150px;
  }
  
  .files-grid {
    grid-template-columns: 1fr;
    padding: 16px;
    gap: 16px;
  }
  
  .table-header {
    padding: 20px 16px;
  }
}

@media (max-width: 480px) {
  .header-info {
    gap: 12px;
  }
  
  .header-icon {
    width: 40px;
    height: 40px;
  }
  
  .header-icon svg {
    width: 20px;
    height: 20px;
  }
  
  .header-text h3 {
    font-size: 18px;
  }
  
  .search-input {
    width: 120px;
  }
}
</style>
