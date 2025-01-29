package org.snomed.snowstorm.config;

import org.snomed.otf.request.domain.RequestConfiguration;
import org.snomed.otf.request.domain.RequestIndexNameProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestConfig {
	@Bean
	public RequestConfiguration requestConfiguration() {
		return new RequestConfiguration();
	}

	@Bean
	public RequestIndexNameProvider requestIndexNameProvider() {
		return new RequestIndexNameProvider(requestConfiguration().getEnvironment());
	}
}
