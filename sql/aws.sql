/*
 Navicat Premium Data Transfer

 Source Server         : 本地docker-mysql8
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : localhost:3306
 Source Schema         : aws

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 06/09/2025 19:40:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_upload_chunk
-- ----------------------------
DROP TABLE IF EXISTS `sys_upload_chunk`;
CREATE TABLE `sys_upload_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `upload_id` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联 sys_upload_task的s3唯一的上传',
  `part_number` int NOT NULL COMMENT '分片序号',
  `etag` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'S3返回的etag',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_part` (`upload_id`,`part_number`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Table structure for sys_upload_task
-- ----------------------------
DROP TABLE IF EXISTS `sys_upload_task`;
CREATE TABLE `sys_upload_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `upload_id` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'S3的uploadId',
  `file_identifier` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件唯一标识（md5/sha256）',
  `file_name` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件原始名称',
  `bucket_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'S3 bucket',
  `object_key` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'S3对象路径',
  `total_size` bigint NOT NULL COMMENT '文件总大小',
  `chunk_size` bigint NOT NULL COMMENT '分片大小',
  `total_chunks` int NOT NULL COMMENT '总分片数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0=上传中,1=已完成,2=未上传',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_identifier` (`file_identifier`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 1;
