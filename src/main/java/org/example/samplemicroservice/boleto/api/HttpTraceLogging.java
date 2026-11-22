package org.example.samplemicroservice.boleto.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class HttpTraceLogging implements WebMvcConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTraceLogging.class);

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String traceId = MDC.get("traceId");
                if (traceId != null) {
                    response.setHeader("X-Trace-Id", traceId);
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                    Object handler, Exception exception) {
                LOGGER.atInfo().addKeyValue("method", request.getMethod())
                        .addKeyValue("route", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                        .addKeyValue("status", response.getStatus()).log("Boleto request completed");
            }
        }).addPathPatterns("/api/v1/boletos", "/api/v1/boletos/**");
    }
}
