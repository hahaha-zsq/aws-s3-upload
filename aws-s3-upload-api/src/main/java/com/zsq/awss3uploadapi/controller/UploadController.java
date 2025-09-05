package com.zsq.awss3uploadapi.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

import com.zsq.awss3uploadapi.entity.Result;
import com.zsq.awss3uploadapi.entity.dto.InitTaskParamDTO;
import com.zsq.awss3uploadapi.entity.vo.TaskInfoVO;
import com.zsq.awss3uploadapi.service.ISysUploadTaskService;
import com.zsq.winter.minio.service.AmazonS3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/bunUpload")
public class UploadController {
    final AmazonS3Template amazonS3Template;
    final ISysUploadTaskService iSysUploadTaskService;


    @PostMapping("/singleUpload")
    public Result<?> uploadFiles(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        LocalDateTime now = LocalDateTime.now();

        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        // 文件名称
        String key = StrUtil.format("{}/zsqyyds/{}.{}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), IdUtil.randomUUID(), suffix);
        amazonS3Template.putObject(key, file, null);
        String gatewayUrl = amazonS3Template.getGatewayUrl(key);
        return Result.build(gatewayUrl, 200, "");
    }


    /**
     * 通过md5检查文件是否存在
     *
     * @param md5 md5
     * @return 结果 <任务信息vo>
     */

    @GetMapping("/multipart/check/{md5}")
    public Result<TaskInfoVO> checkFileByMd5(@PathVariable("md5") String md5) {
        return Result.ok(iSysUploadTaskService.checkFileByMd5(md5));
    }

    /**
     * 创建一个上传任务,前端需要根据checkFileByMd5函数有没有返回uploadId信息来判断需不需要调用/multipart/init该接口,没有uploadId则调用该接口
     *
     * @param initTaskParamDTO
     * @return
     */

    @PostMapping("/multipart/init")
    public Result<String> initMultiPartFile(@Valid @RequestBody InitTaskParamDTO initTaskParamDTO) {
        return Result.ok(iSysUploadTaskService.initMultiPartFile(initTaskParamDTO));
    }


    @PostMapping("/multipart/uploadPart")
    public Result<?> preSignUploadUrl(@RequestParam(value = "file") MultipartFile file
            , @RequestParam String uploadId, @RequestParam String objectName, @RequestParam int partNumber) throws Exception {
        return Result.ok(iSysUploadTaskService.uploadPart(file, uploadId, objectName, partNumber));
    }
}
