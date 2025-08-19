package io.github.auditlib.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для логгирования методов
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    LogLevel logLevel() default LogLevel.DEBUG;

    enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }

}
