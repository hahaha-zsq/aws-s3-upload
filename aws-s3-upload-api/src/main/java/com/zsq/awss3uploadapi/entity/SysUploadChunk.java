package com.zsq.awss3uploadapi.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
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
@TableName(value = "sys_upload_chunk")
public class SysUploadChunk {
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Long id;

    /**
     * 关联 sys_upload_task
     */
    @TableField(value = "upload_id")
    @NotNull(message = "关联 sys_upload_task不能为null")
    private String uploadId;

    /**
     * 分片序号
     */
    @TableField(value = "part_number")
    @NotNull(message = "分片序号不能为null")
    private Integer partNumber;

    /**
     * S3返回的etag
     */
    @TableField(value = "etag")
    @Size(max = 255,message = "S3返回的etag最大长度要小于 255")
    private String etag;

    @TableField(value = "created_at")
    private Date createdAt;
}