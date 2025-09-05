package com.zsq.awss3uploadapi.entity.dto;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@ToString
@Accessors(chain = true)
public class InitTaskParamDTO {

    /**
     * 文件唯一标识(MD5)
     */
    @NotBlank(message = "文件标识不能为空")
    private String fileIdentifier;
    /**
     * 文件大小（byte）
     */
    @NotNull(message = "文件大小不能为空")
    private Long totalSize;

    @NotNull(message = "分片数量不能为空")
    private Integer chunkNum;
    /**
     * 分片大小（byte）
     */
    @NotNull(message = "分片大小不能为空")
    private Long chunkSize;
    /**
     * 文件名称
     */
    @NotBlank(message = "文件名称不能为空")
    private String fileName;
}
