<script setup lang="ts">
import { reactive, onMounted, ref, computed } from 'vue'
import { convertFileSizeUnit } from '@/utils/file/file'
import { fetchFileList, deleteFile } from '@/services'
import type { FilesType } from '@/services/apis/typing'
import config from '@/config'

type FileDataType = FilesType

const state = reactive<{ dataSource: FileDataType[] }>({
  dataSource: []
})

const searchQuery = ref('')

const filteredFileList = computed(() => {
  return state.dataSource
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

// 搜索文件
const searchFiles = async () => {
  try {
    const { code, data } = await fetchFileList(searchQuery.value || undefined)
    if (code === 200) {
      state.dataSource = data
      if (searchQuery.value) {
        showMessage(`找到 ${data.length} 个匹配的文件`, 'success')
      }
    }
  } catch (error) {
    showMessage('搜索文件失败', 'error')
  }
}

// 监听搜索框变化
let searchTimeout: number | null = null
const handleSearchInput = () => {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }
  searchTimeout = setTimeout(() => {
    searchFiles()
  }, 500) // 防抖，500ms后执行搜索
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



// 获取文件扩展名
const getFileExtension = (fileName: string): string => {
  const lastDot = fileName.lastIndexOf('.')
  return lastDot > 0 ? fileName.substring(lastDot + 1).toUpperCase() : 'FILE'
}

// 刷新文件列表
const refreshList = async () => {
  try {
    const { code, data } = await fetchFileList(searchQuery.value)
    if (code === 200) state.dataSource = data
    showMessage('文件列表已刷新', 'success')
  } catch (error) {
    showMessage('刷新文件列表失败', 'error')
  }
}

// 删除文件
const deleteFileHandler = async (record: FileDataType) => {
  if (!confirm(`确定要删除文件 "${record.originFileName}" 吗？`)) {
    return
  }
  
  try {
    const { code } = await deleteFile(record.id)
    if (code === 200) {
      // 从列表中移除已删除的文件
      const index = state.dataSource.findIndex(item => item.id === record.id)
      if (index > -1) {
        state.dataSource.splice(index, 1)
      }
      showMessage('文件删除成功', 'success')
    } else {
      showMessage('文件删除失败', 'error')
    }
  } catch (error) {
    showMessage('文件删除失败', 'error')
  }
}

// 直接下载文件（使用后端下载URL）
const directDownloadFile = (record: FileDataType) => {
  try {
    // 直接使用后端API地址进行下载
    const link = document.createElement('a')
    link.href = record.url
    link.download = record.originFileName
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    showMessage('开始下载文件', 'success')
  } catch (error) {
    showMessage('下载失败', 'error')
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
          @input="handleSearchInput"
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
            <!-- 直接下载按钮 -->
            <button
              class="action-btn download-btn"
              @click="directDownloadFile(row)"
              :title="'下载 ' + row.originFileName"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="7,10 12,15 17,10"></polyline>
                <line x1="12" y1="15" x2="12" y2="3"></line>
              </svg>
            </button>
            
            <!-- 删除按钮 -->
            <button
              class="action-btn delete-btn"
              @click="deleteFileHandler(row)"
              :title="'删除 ' + row.originFileName"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3,6 5,6 21,6"></polyline>
                <path d="m19,6v14a2,2 0 0,1 -2,2H7a2,2 0 0,1 -2,-2V6m3,0V4a2,2 0 0,1 2,-2h4a2,2 0 0,1 2,2v2"></path>
                <line x1="10" y1="11" x2="10" y2="17"></line>
                <line x1="14" y1="11" x2="14" y2="17"></line>
              </svg>
            </button>

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
  bottom: 0;
  right: 0;
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
  background: #bae7ff;
}

.chunk-download-btn {
  background: #f6ffed;
  color: #52c41a;
}

.chunk-download-btn:hover:not(:disabled) {
  background: #d9f7be;
}

.delete-btn {
  background: #fff2f0;
  color: #ff4d4f;
}

.delete-btn:hover:not(:disabled) {
  background: #ffccc7;
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
