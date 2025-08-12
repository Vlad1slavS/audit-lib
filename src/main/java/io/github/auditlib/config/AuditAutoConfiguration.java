package io.github.auditlib.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.auditlib.aspect.MethodAspect;
import io.github.auditlib.http.IncomingHttpFilter;
import io.github.auditlib.http.OutgoingHttpInterceptor;
import io.github.auditlib.service.AuditLogServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.client.RestTemplate;

/**
 * Автоконфигурация стартера
 */
@AutoConfiguration
@EnableConfigurationProperties(AuditProperties.class)
@EnableAspectJAutoProxy
public class AuditAutoConfiguration {

    @Bean
    public AuditLogServiceImpl auditLogService(AuditProperties properties,
                                               ObjectMapper objectMapper,
                                               KafkaTemplate<String, String> auditKafkaTemplate) {
        return new AuditLogServiceImpl(properties, objectMapper, auditKafkaTemplate);
    }

    @Bean
    public MethodAspect methodAspect(AuditLogServiceImpl auditLogService) {
        return new MethodAspect(auditLogService);
    }

    @Bean
    public IncomingHttpFilter incomingHttpFilter(AuditLogServiceImpl auditLogService) {
        return new IncomingHttpFilter(auditLogService);
    }

    @Bean
    public FilterRegistrationBean<IncomingHttpFilter> httpAuditFilter(IncomingHttpFilter filter) {
        FilterRegistrationBean<IncomingHttpFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean
    public OutgoingHttpInterceptor outgoingHttpInterceptor(AuditLogServiceImpl auditLogService) {
        return new OutgoingHttpInterceptor(auditLogService);
    }

    @Bean
    public RestTemplate restTemplate(OutgoingHttpInterceptor outgoingHttpInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(outgoingHttpInterceptor);
        return restTemplate;
    }

}
