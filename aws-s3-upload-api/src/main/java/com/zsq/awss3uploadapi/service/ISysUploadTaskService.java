package com.zsq.awss3uploadapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zsq.awss3uploadapi.entity.SysUploadTask;
import com.zsq.awss3uploadapi.entity.dto.InitTaskParamDTO;
import com.zsq.awss3uploadapi.entity.vo.TaskInfoVO;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

public interface ISysUploadTaskService extends IService<SysUploadTask>{
    TaskInfoVO checkFileByMd5 (String identifier);
    boolean uploadPart(MultipartFile file, String uploadId, String objectName, int partNumber) throws Exception;
    String initMultiPartFile(@Valid InitTaskParamDTO initTaskParamDTO);

    String mergeMultipartUpload(String md5);
}
