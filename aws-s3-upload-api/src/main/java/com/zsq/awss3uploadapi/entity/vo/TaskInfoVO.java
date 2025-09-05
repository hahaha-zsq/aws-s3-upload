package com.zsq.awss3uploadapi.entity.vo;

import com.amazonaws.services.s3.model.PartSummary;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskInfoVO {

    /**
     * 上传状态
     * UPLOAD_SUCCESS(2001, "上传成功"),sys_upload_task表中的status值为0时
     * UPLOADING(2002, "上传中"),sys_upload_task表中的status值为0时
     * NOT_UPLOADED(2003, "未上传"),
     * UPLOAD_FILE_FAILED(5001, "文件上传失败");
     */
    private Integer code;
    /**
     * 上传任务ID(sys_upload_task表中的status值为0时或者为1时，表示已经初始化过了)
     */
    private String uploadId = "";
    /**
     * 已上传完的分片，sys_upload_task表中的status值为0时
     */
    private List<PartSummary> exitPartList;
    /**
     * 秒传时需要返回该内容，sys_upload_task表中的status值为1时
     */
    private String url;

}
