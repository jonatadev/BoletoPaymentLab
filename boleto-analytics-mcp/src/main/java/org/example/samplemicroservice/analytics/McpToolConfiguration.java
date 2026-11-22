package org.example.samplemicroservice.analytics;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class McpToolConfiguration {
    @Bean
    ToolCallbackProvider boletoAnalyticsToolCallbacks(AnalyticsTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
