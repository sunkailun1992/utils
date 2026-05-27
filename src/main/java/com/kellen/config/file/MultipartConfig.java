package com.kellen.config.file;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;
@Configuration
public class MultipartConfig {
	/**
	 * 文件上传配置
	 *
	 * @return
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
