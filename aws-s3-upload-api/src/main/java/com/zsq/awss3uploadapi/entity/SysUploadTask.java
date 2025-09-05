package com.zsq.awss3uploadapi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Date;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName(value = "sys_upload_task")
public class SysUploadTask {
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Long id;

    /**
     * S3的uploadId
     */
    @TableField(value = "upload_id")
    @Size(max = 255,message = "S3的uploadId最大长度要小于 255")
    private String uploadId;

    /**
     * 文件唯一标识（md5/sha256）
     */
    @TableField(value = "file_identifier")
    @NotBlank(message = "文件唯一标识（md5/sha256）不能为空")
    private String fileIdentifier;

    /**
     * 文件原始名称
     */
    @TableField(value = "file_name")
    @Size(max = 500,message = "文件原始名称最大长度要小于 500")
    @NotBlank(message = "文件原始名称不能为空")
    private String fileName;

    /**
     * S3 bucket
     */
    @TableField(value = "bucket_name")
    @Size(max = 255,message = "S3 bucket最大长度要小于 255")
    @NotBlank(message = "S3 bucket不能为空")
    private String bucketName;

    /**
     * S3对象路径
     */
    @TableField(value = "object_key")
    @Size(max = 500,message = "S3对象路径最大长度要小于 500")
    @NotBlank(message = "S3对象路径不能为空")
    private String objectKey;

    /**
     * 文件总大小
     */
    @TableField(value = "total_size")
    @NotNull(message = "文件总大小不能为null")
    private Long totalSize;

    /**
     * 分片大小
     */
    @TableField(value = "chunk_size")
    @NotNull(message = "分片大小不能为null")
    private Long chunkSize;

    /**
     * 总分片数
     */
    @TableField(value = "total_chunks")
    @NotNull(message = "总分片数不能为null")
    private Integer totalChunks;

    /**
     * 状态: 0=上传中,1=已完成,2=失败
     */
    @TableField(value = "`status`")
    @NotNull(message = "状态: 0=上传中,1=已完成,2=失败不能为null")
    private Byte status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}