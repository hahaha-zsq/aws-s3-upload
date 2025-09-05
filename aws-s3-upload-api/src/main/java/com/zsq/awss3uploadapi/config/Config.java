package com.zsq.awss3uploadapi.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@EnableAsync
@Configuration
@EnableConfigurationProperties
@RequiredArgsConstructor
public class Config implements WebMvcConfigurer {



    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 防全表更新与删除插件
        //interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }


    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        // 创建并配置 ObjectMapper
        ObjectMapper objectMapper = configureObjectMapper();
        // 创建并配置 SimpleModule
        SimpleModule simpleModule = configureSimpleModule();
        // 注册模块到 ObjectMapper
        objectMapper.registerModule(new JavaTimeModule());
        //SimpleModule配置时间的序列化，用于覆盖前者JavaTimeModule默认的时间格式
        objectMapper.registerModule(simpleModule);
        // 配置 MappingJackson2HttpMessageConverter
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    /**
     * 配置 ObjectMapper 的核心方法
     * 该方法主要用于设置 ObjectMapper 的一系列配置，以使其符合我们的序列化和反序列化需求
     * ObjectMapper 是一个用于处理 JSON 数据的核心类，通过它可以配置如何读取和写入 JSON 数据
     *
     * @return ObjectMapper 实例，用于执行 JSON 数据的序列化和反序列化操作
     */
    private ObjectMapper configureObjectMapper() {
        // 创建一个新的 ObjectMapper 实例
        ObjectMapper objectMapper = new ObjectMapper();

        // 忽略未知属性
        // 设置反序列化时，如果 JSON 数据中包含 Java 对象中不存在的属性，不抛出异常
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 允许所有字段序列化
        // 设置所有字段对序列化和反序列化操作可见，无论其访问权限如何
        // objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 禁用时间戳格式
        // 设置日期/时间值在序列化时不会以时间戳形式输出，而是以 ISO-8601 日期/时间格式输出
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 排除空值
        // 设置序列化时仅包含非空值的属性，避免输出 "null" 值
       /*
        1:ALWAYS
        默认值。
        无论字段是否为 null 或空值，都会被序列化到 JSON 中。
        2:NON_NULL
        只有字段值不为 null 时才会被序列化。
        如果字段值为 null，则不会出现在生成的 JSON 中。
        3:NON_ABSENT
        类似于 NON_NULL，但额外支持 Optional 类型。
        如果字段是 Optional 类型且为空（Optional.empty()），则不会被序列化。
        4:NON_EMPTY
        不仅排除 null 值，还排除“空”值。
        对于集合、数组、Map 等，“空”指的是大小为 0；对于字符串，“空”指的是长度为 0 或仅为空白字符；对于其他对象，“空”通常指其自然状态未被修改。
        5:NON_DEFAULT
        排除使用默认构造函数初始化后的值。
        适用于数值类型（如 int、double）或对象类型的默认值（如 0、0.0 或 false）。
        6:CUSTOM
        允许自定义包含逻辑。
        需要通过 ValueProvider 接口实现自定义规则。*/
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        // 返回配置好的 ObjectMapper 实例
        return objectMapper;
    }

    /**
     * 配置 SimpleModule 的核心方法
     * 该方法用于添加自定义的序列化器和反序列化器，以处理特定类型的数据转换
     * 注意：此处未使用的代码行（如 BigInteger 和 Long 的序列化）已被注释掉
     */
    private SimpleModule configureSimpleModule() {
        try {
            SimpleModule simpleModule = new SimpleModule();
            //simpleModule.addSerializer(BigInteger.class, ToStringSerializer.instance); // BigInteger 序列化为字符串
            //simpleModule.addSerializer(Long.class, ToStringSerializer.instance); // Long 序列化为字符串
            simpleModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // LocalDateTime 序列化
            simpleModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd"))); // LocalDate 序列化
            simpleModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss"))); // LocalTime 序列化

            simpleModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // LocalDateTime 反序列化
            simpleModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd"))); // LocalDate 反序列化
            simpleModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss"))); // LocalTime 反序列化

            return simpleModule;
        } catch (Exception e) {
            // 当日期时间格式不正确时抛出运行时异常
            throw new RuntimeException("Failed to configure SimpleModule due to invalid date-time pattern", e);
        }
    }


    /**
     * 配置消息转换器
     * <p>
     * 此方法用于向Spring的HTTP输入/输出处理机制中添加自定义的消息转换器
     * 它重写了父类的configureMessageConverters方法，以定制化消息转换过程
     *
     * @param converters 消息转换器列表，用于存储初始化后的消息转换器
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 创建自定义的Jackson消息转换器
        MappingJackson2HttpMessageConverter converter = mappingJackson2HttpMessageConverter();
        // 设置支持的媒体类型
        converter.setSupportedMediaTypes(getSupportedMediaTypes());
        // 添加到转换器列表
        converters.add(converter);
    }


    /**
     * 获取支持的媒体类型列表
     * 该方法初始化并返回一个包含多种媒体类型的列表，这些媒体类型表示了
     * 该应用或组件能够处理的数据格式包括标准的JSON、XML、图像格式、文本格式等
     *
     * @return List<MediaType> 返回一个包含支持媒体类型的列表
     */
    public List<MediaType> getSupportedMediaTypes() {
        //创建fastJson消息转换器
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        // 添加支持的媒体类型到列表中
        supportedMediaTypes.add(MediaType.APPLICATION_JSON);
        supportedMediaTypes.add(MediaType.APPLICATION_ATOM_XML);
        supportedMediaTypes.add(MediaType.APPLICATION_XML);
        supportedMediaTypes.add(MediaType.APPLICATION_FORM_URLENCODED);
        supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        supportedMediaTypes.add(MediaType.APPLICATION_PDF);
        supportedMediaTypes.add(MediaType.APPLICATION_RSS_XML);
        supportedMediaTypes.add(MediaType.APPLICATION_XHTML_XML);
        supportedMediaTypes.add(MediaType.IMAGE_GIF);
        supportedMediaTypes.add(MediaType.IMAGE_JPEG);
        supportedMediaTypes.add(MediaType.IMAGE_PNG);
        supportedMediaTypes.add(MediaType.TEXT_EVENT_STREAM);
        supportedMediaTypes.add(MediaType.TEXT_HTML);
        supportedMediaTypes.add(MediaType.TEXT_MARKDOWN);
        //supportedMediaTypes.add(MediaType.TEXT_PLAIN);
        //supportedMediaTypes.add(MediaType.TEXT_XML);
        //supportedMediaTypes.add(MediaType.ALL);
        return supportedMediaTypes;
    }
}
