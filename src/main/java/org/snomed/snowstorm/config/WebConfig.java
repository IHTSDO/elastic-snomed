package org.snomed.snowstorm.config;

import org.snomed.otf.request.domain.*;
import org.snomed.snowstorm.rest.converter.ItemsPageCSVConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	private final RequestInterceptor requestInterceptor;

	public WebConfig(RequestInterceptor requestInterceptor) {
		this.requestInterceptor = requestInterceptor;
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		// Workaround until we have removed trailing slashes in UI
		configurer.setUseTrailingSlashMatch(true);
	}

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.add(new ItemsPageCSVConverter());
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(requestInterceptor);
	}
}
