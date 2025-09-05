package com.zsq.awss3uploadapi.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zsq.awss3uploadapi.entity.SysUploadChunk;
import com.zsq.awss3uploadapi.mapper.SysUploadChunkMapper;
import com.zsq.awss3uploadapi.service.ISysUploadChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;



@Service
@RequiredArgsConstructor
public class SysUploadChunkServiceImpl extends ServiceImpl<SysUploadChunkMapper, SysUploadChunk> implements ISysUploadChunkService {

}
