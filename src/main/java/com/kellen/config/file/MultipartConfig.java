package com.kellen.config.file;

import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * 文件上传配置。
 *
 * <p>放宽单文件与单次请求的大小上限，供消费者大文件上传场景使用。</p>
 *
 * @author 孙凯伦
 */
@Configuration
public class MultipartConfig {
	/**
	 * 注册 Multipart 配置，单文件与单次请求上限均为 1024MB。
	 *
	 * @return Multipart 配置元素
	 */
	@Bean
	public MultipartConfigElement multipartConfigElement() {
		MultipartConfigFactory factory = new MultipartConfigFactory();
		//  单个数据大小 KB,MB
		factory.setMaxFileSize(DataSize.ofMegabytes(1024));
		/// 总上传数据大小
		factory.setMaxRequestSize(DataSize.ofMegabytes(1024));
		//临时文件
		//factory.setLocation("/data/temporary");
		return factory.createMultipartConfig();
	}
}
