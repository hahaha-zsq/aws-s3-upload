package com.zsq.awss3uploadapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zsq.awss3uploadapi.entity.SysUploadTask;
import com.zsq.awss3uploadapi.entity.dto.InitTaskParamDTO;
import com.zsq.awss3uploadapi.entity.vo.FileListVO;
import com.zsq.awss3uploadapi.entity.vo.TaskInfoVO;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

public interface ISysUploadTaskService extends IService<SysUploadTask>{
    TaskInfoVO checkFileByMd5 (String identifier);
    boolean uploadPart(MultipartFile file, String uploadId,  int partNumber) throws Exception;
    String initMultiPartFile(@Valid InitTaskParamDTO initTaskParamDTO);

    String mergeMultipartUpload(String md5);
    
    /**
     * 获取文件列表
     * @param fileName 文件名（支持模糊查询）
     * @return 文件列表
     */
    List<FileListVO> getFileList(String fileName);
    
    /**
     * 删除文件
     * @param fileId 文件ID
     * @return 是否删除成功
     */
    boolean deleteFile(Long fileId);
}
