package cz.xtf.core.service.logs.streaming;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This interfaces defines the annotation which can be used to enable the Service Logs Streaming component.
 * What log(s) can be inspected and how is determined by the type of service and its life cycle (e.g.: accessing logs
 * in the provisioning or execution phases) and by the type of cloud platform in which the service is living
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServiceLogsStreaming {
    /**
     * Returns the log level filter which is set for services annotated with {@link ServiceLogsStreaming}
     *
     * @return A String identifying the log level filter which is set for services annotated with
     *         {@link ServiceLogsStreaming}
     */
    String filter() default ServiceLogsSettings.UNASSIGNED;

    /**
     * Defines the base path where log files produced by services annotated with {@link ServiceLogsStreaming} should be
     * streamed
     *
     * @return A String representing the base path log files produced by annotated services should be streamed.
     */
    String output() default ServiceLogsSettings.UNASSIGNED;
}
