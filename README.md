# AWS S3 分片上传系统

一个基于 Spring Boot + Vue 3 的高性能文件上传系统，支持大文件分片上传、断点续传、秒传等功能。

## 🚀 功能特性

### 核心功能
- **分片上传**: 支持大文件分片上传，提高上传效率和稳定性
- **断点续传**: 网络中断后可从断点继续上传，无需重新开始
- **秒传功能**: 基于 MD5 校验的文件秒传，相同文件无需重复上传
- **并发上传**: 支持多文件并发上传，最大化带宽利用率
- **实时进度**: 实时显示上传进度、速度和剩余时间
- **文件管理**: 支持文件列表查看、搜索、下载和删除

### 技术特性
- **高性能**: 基于 AWS S3 的分布式存储，支持海量文件存储
- **高可用**: 完善的错误处理和重试机制
- **现代化UI**: 基于 Vue 3 + TypeScript 的响应式界面
- **RESTful API**: 标准化的后端接口设计

## 🏗️ 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端 (Vue 3)   │────│  后端 (Spring)  │────│   AWS S3 存储   │
│                 │    │                 │    │                 │
│ • 文件选择      │    │ • 分片管理      │    │ • 对象存储      │
│ • 进度显示      │    │ • MD5 校验      │    │ • 分片合并      │
│ • 断点续传      │    │ • 接口服务      │    │ • 文件访问      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 📋 环境要求

### 后端环境
- Java 8+
- Maven 3.6+
- Spring Boot 2.7+
- MySQL 5.7+ (可选，用于任务记录)

### 前端环境
- Node.js 16+
- pnpm 7+ (推荐) 或 npm
- Vue 3 + TypeScript

### AWS 配置
- AWS S3 存储桶
- AWS 访问密钥 (Access Key)
- 适当的 S3 权限配置

## 🛠️ 安装部署

### 1. 克隆项目
```bash
git clone <repository-url>
cd aws-s3-upload
```

### 2. 后端部署

#### 配置文件
编辑 `aws-s3-upload-api/src/main/resources/application.yml`：

```yaml
# AWS S3 配置
aws:
  s3:
    access-key: your-access-key
    secret-key: your-secret-key
    region: us-east-1
    bucket-name: your-bucket-name

# 服务器配置
server:
  port: 8080

# 文件上传配置
spring:
  servlet:
    multipart:
      max-file-size: 1GB
      max-request-size: 1GB
```

#### 启动后端服务
```bash
cd aws-s3-upload-api
mvn clean install
mvn spring-boot:run
```

### 3. 前端部署

#### 安装依赖
```bash
cd aws-s3-upload-web
pnpm install
```

#### 配置后端地址
编辑 `src/config/index.ts`：
```typescript
export const API_BASE_URL = 'http://localhost:8080'
```

#### 启动开发服务器
```bash
pnpm dev
```

#### 构建生产版本
```bash
pnpm build
```

## 📖 使用指南

### 文件上传

1. **选择文件**: 点击上传区域或拖拽文件到上传区域
2. **自动分片**: 系统自动将大文件分割为 5MB 的分片
3. **MD5 校验**: 上传前自动计算文件 MD5，支持秒传
4. **并发上传**: 多个分片并发上传，提高效率
5. **实时监控**: 查看上传进度、速度和剩余时间

### 断点续传

- 网络中断或页面刷新后，系统自动检测未完成的上传任务
- 重新上传时，只上传未完成的分片，节省时间和带宽
- 支持暂停和恢复上传操作

### 文件管理

- **文件列表**: 查看所有已上传的文件
- **搜索功能**: 根据文件名快速查找文件
- **下载文件**: 支持单文件下载和批量下载
- **删除文件**: 支持单文件删除和批量删除

## 🔧 API 接口

### 上传相关接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/upload/check/{md5}` | GET | 检查文件是否已存在（秒传） |
| `/api/upload/init` | POST | 初始化分片上传任务 |
| `/api/upload/part` | POST | 上传单个分片 |
| `/api/upload/merge` | POST | 合并所有分片 |
| `/api/upload/single` | POST | 单文件上传（小文件） |

### 文件管理接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/files` | GET | 获取文件列表 |
| `/api/files/{id}` | DELETE | 删除指定文件 |
| `/api/files/download/{id}` | GET | 下载指定文件 |

## 🎯 性能优化

### 上传优化
- **分片大小**: 默认 5MB，可根据网络环境调整
- **并发数量**: 默认 3 个并发，避免过多连接
- **重试机制**: 失败分片自动重试，最多 3 次
- **压缩传输**: 支持 gzip 压缩，减少传输数据量

### 前端优化
- **虚拟滚动**: 大量文件列表使用虚拟滚动
- **懒加载**: 图片和组件按需加载
- **缓存策略**: 合理的浏览器缓存配置

## 🔒 安全考虑

### 文件安全
- **文件类型检查**: 限制允许上传的文件类型
- **文件大小限制**: 防止恶意大文件上传
- **MD5 校验**: 确保文件完整性

### 访问控制
- **预签名 URL**: 使用 AWS S3 预签名 URL 控制访问权限
- **时效性控制**: 上传和下载链接具有时效性
- **权限验证**: 后端接口权限验证

## 🐛 故障排除

### 常见问题

**Q: 上传失败，提示 "NoSuchUpload" 错误**
A: 这通常是因为 uploadId 过期，系统会自动清除并重新初始化上传任务。

**Q: 大文件上传速度慢**
A: 可以尝试调整分片大小和并发数量，或检查网络环境。

**Q: 断点续传不生效**
A: 确保浏览器支持 localStorage，且未清除浏览器数据。

### 日志查看

**后端日志**:
```bash
tail -f logs/application.log
```

**前端调试**:
打开浏览器开发者工具，查看 Console 和 Network 面板。

## 🤝 贡献指南

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发送邮件
- 项目讨论区

---

**注意**: 使用前请确保已正确配置 AWS S3 相关权限和访问密钥。