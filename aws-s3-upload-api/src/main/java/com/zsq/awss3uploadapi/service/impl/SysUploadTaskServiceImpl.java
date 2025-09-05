package com.zsq.awss3uploadapi.service.impl;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.amazonaws.services.s3.model.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zsq.awss3uploadapi.entity.SysUploadChunk;
import com.zsq.awss3uploadapi.entity.SysUploadTask;
import com.zsq.awss3uploadapi.entity.dto.InitTaskParamDTO;
import com.zsq.awss3uploadapi.entity.vo.TaskInfoVO;
import com.zsq.awss3uploadapi.enums.ResultCodeEnum;
import com.zsq.awss3uploadapi.mapper.SysUploadTaskMapper;
import com.zsq.awss3uploadapi.service.ISysUploadChunkService;
import com.zsq.awss3uploadapi.service.ISysUploadTaskService;
import com.zsq.winter.minio.service.AmazonS3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUploadTaskServiceImpl extends ServiceImpl<SysUploadTaskMapper, SysUploadTask> implements ISysUploadTaskService {
    final AmazonS3Template amazonS3Template;
    final ISysUploadChunkService iSysUploadChunkService;

    @Override
    public TaskInfoVO checkFileByMd5(String identifier) {
        TaskInfoVO taskInfoVO = new TaskInfoVO();

        // 检查文件标识符是否为空
        if (ObjectUtils.isEmpty(identifier)) {
            taskInfoVO.setCode(ResultCodeEnum.NOT_UPLOADED.getCode());
            return taskInfoVO;
        }

        // 根据MD5查找上传任务
        SysUploadTask sysUploadTask = getOne(new LambdaQueryWrapper<SysUploadTask>()
                .eq(SysUploadTask::getFileIdentifier, identifier));

        // 如果没有找到上传记录
        if (ObjectUtils.isEmpty(sysUploadTask)) {
            taskInfoVO.setCode(ResultCodeEnum.NOT_UPLOADED.getCode());
            return taskInfoVO;
        }

        // 根据上传状态处理不同情况
        Byte status = sysUploadTask.getStatus();
        if (status == null) {
            taskInfoVO.setCode(ResultCodeEnum.UPLOAD_FILE_FAILED.getCode());
            return taskInfoVO;
        }

        switch (status) {
            case 1: // 上传已完成
                return handleCompletedUpload(sysUploadTask, taskInfoVO);
            case 0: // 上传中
                return handleUploadingTask(sysUploadTask, taskInfoVO);
            default: // 未上传 需要对文件进行分片初始化
                taskInfoVO.setCode(ResultCodeEnum.NOT_UPLOADED.getCode());
                return taskInfoVO;
        }
    }

    /**
     * 上传分片并将上传后的文件存入分片信息表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uploadPart(MultipartFile file, String uploadId, String objectName, int partNumber) throws Exception {
        // 1. 上传分片到 S3
        UploadPartResult uploadPartResult = amazonS3Template.uploadPart(uploadId, objectName, partNumber, file);
        // 2. 保存分片信息（etag + partNumber）
        SysUploadChunk sysUploadChunk = SysUploadChunk.builder()
                .uploadId(uploadId)
                .partNumber(partNumber)
                .etag(uploadPartResult.getETag())
                .build();
        iSysUploadChunkService.save(sysUploadChunk);
        return true;
    }

    /**
     * 初始化分片上传任务,当数据库中不存在该文件的上传任务时，进行初始化，并返回初始化后的上传ID，前端根据上传ID进行分片上传
     */
    @Override
    public String initMultiPartFile(InitTaskParamDTO initTaskParamDTO) {
        // 检查是否已存在相同的上传任务，
        SysUploadTask sysUploadTask = getOne(new LambdaQueryWrapper<SysUploadTask>()
                .eq(SysUploadTask::getFileIdentifier, initTaskParamDTO.getFileIdentifier()));

        String uploadId;

        // 按道理来说，sysUploadTask肯定是空的，因为需要当checkFileByMd5返回NOT_UPLOADED时，才会调用初始化，但还是做一下判断，以防万一
        if (!ObjectUtils.isEmpty(sysUploadTask)) {
            // 如果任务已存在，直接使用已有的信息
            uploadId = sysUploadTask.getUploadId();
        } else {
            // 如果是第一次上传，初始化分片上传
            LocalDateTime localDateTime = LocalDateTime.now();
            String format = localDateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            // 原文件名称
            String originFileName = initTaskParamDTO.getFileName();
            // 获取文件后缀
            String suffix = FileUtil.extName(originFileName);
            // 获取文件名称
            String fileName = FileUtil.mainName(originFileName);
            // 对文件重新命名，并以年月日文件夹格式存储
            String objectKey = StrUtil.format("{}/{}_{}.{}", format, fileName, initTaskParamDTO.getFileIdentifier(), suffix);
            // 设置内容类型
            String contentType = MediaTypeFactory.getMediaType(objectKey)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();

            // 初始化分片上传
            InitiateMultipartUploadResult initiateMultipartUploadResult =
                    amazonS3Template.initiateMultipartUpload(objectKey, contentType);
            // 获取初始化后的上传ID
            uploadId = initiateMultipartUploadResult.getUploadId();
            // 创建新的上传任务记录
            sysUploadTask = SysUploadTask.builder()
                    .fileIdentifier(initTaskParamDTO.getFileIdentifier())
                    .chunkSize(initTaskParamDTO.getChunkSize())
                    .totalChunks(initTaskParamDTO.getChunkNum())
                    .objectKey(objectKey)
                    .fileName(initTaskParamDTO.getFileName())
                    .bucketName(amazonS3Template.getBucketName())
                    .totalSize(initTaskParamDTO.getTotalSize())
                    .uploadId(uploadId)
                    .status((byte) 0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            // 保存到数据库
            save(sysUploadTask);
        }
        return uploadId;
    }


    /**
     * 处理已完成的上传任务
     */
    private TaskInfoVO handleCompletedUpload(SysUploadTask sysUploadTask, TaskInfoVO taskInfoVO) {
        taskInfoVO.setCode(ResultCodeEnum.UPLOAD_SUCCESS.getCode());
        String gatewayUrl = amazonS3Template.getGatewayUrl(sysUploadTask.getBucketName(), sysUploadTask.getObjectKey());
        taskInfoVO.setUrl(gatewayUrl);
        taskInfoVO.setUploadId(sysUploadTask.getUploadId());
        return taskInfoVO;
    }

    /**
     * 处理正在上传中的任务
     */
    private TaskInfoVO handleUploadingTask(SysUploadTask sysUploadTask, TaskInfoVO taskInfoVO) {
        try {
            // 列出一个正在进行的分片上传操作的所有已上传部分
            PartListing partListing = amazonS3Template.listParts(
                    sysUploadTask.getBucketName(),
                    sysUploadTask.getObjectKey(),
                    sysUploadTask.getUploadId());
            taskInfoVO.setCode(ResultCodeEnum.UPLOADING.getCode());
            taskInfoVO.setExitPartList(partListing.getParts());
            taskInfoVO.setUploadId(sysUploadTask.getUploadId());
        } catch (Exception e) {
            // 如果获取分片信息失败，返回未上传状态
            taskInfoVO.setCode(ResultCodeEnum.NOT_UPLOADED.getCode());
        }
        return taskInfoVO;
    }

}
