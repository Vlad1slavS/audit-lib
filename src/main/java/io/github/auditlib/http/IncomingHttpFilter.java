package io.github.auditlib.http;

import io.github.auditlib.service.AuditLogServiceImpl;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Фильтр для логирования входящих HTTP запросов
 */
@Component
public class IncomingHttpFilter implements Filter {

    private final AuditLogServiceImpl auditLogService;

    public IncomingHttpFilter(AuditLogServiceImpl auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        try {

            chain.doFilter(wrappedRequest, wrappedResponse);

        } finally {

            logHttpRequest(wrappedRequest, wrappedResponse);

            wrappedResponse.copyBodyToResponse();

        }
    }

    private void logHttpRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {

        Map<String, Object> event = new HashMap<>();
        event.put("direction", "Incoming");
        event.put("timestamp", LocalDateTime.now());
        event.put("method", request.getMethod());
        event.put("uri", request.getRequestURI());
        event.put("requestBody", new String(request.getContentAsByteArray()).isEmpty() ? "{}" : new String(request.getContentAsByteArray()));
        event.put("statusCode", response.getStatus());
        event.put("responseBody", new String(response.getContentAsByteArray()).isEmpty() ? "{}" : new String(response.getContentAsByteArray()));

        auditLogService.logHttpEvent(event);
    }

}
