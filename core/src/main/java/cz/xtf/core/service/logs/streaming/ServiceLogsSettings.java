package cz.xtf.core.service.logs.streaming;

import java.util.Objects;

/**
 * Stores a valid Service Logs Streaming component configuration
 */
public class ServiceLogsSettings {

    public static final String ATTRIBUTE_NAME_TARGET = "target";
    public static final String ATTRIBUTE_NAME_FILTER = "filter";
    public static final String ATTRIBUTE_NAME_OUTPUT = "output";
    public static final String UNASSIGNED = "[unassigned]";

    private final String target;
    private final String filter;
    private final String outputPath;

    private ServiceLogsSettings(String target, String filter, String outputPath) {
        this.target = target;
        this.filter = filter;
        this.outputPath = outputPath;
    }

    /**
     * Regular expression which defines the test classes whose services logs must be streamed - e.g. allows the testing
     * engine to check whether a given context test class name is a valid target for a Service Logs Streaming
     * configuration.
     *
     * @return String representing a regular expression which defines the test classes whose services logs must be
     *         streamed.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Regex to filter out the resources which the Service Logs Streaming activation should be monitoring.
     *
     * @return String representing a regex to filter out the resources which the Service Logs Streaming activation
     *         should be monitoring.
     */
    public String getFilter() {
        return filter;
    }

    /**
     * Base path which should be used as the output where the logs stream files must be written
     *
     * @return String identifying a base path to which the service logs output files should be streamed.
     */
    public String getOutputPath() {
        return outputPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ServiceLogsSettings that = (ServiceLogsSettings) o;
        return target.equals(that.target) && filter.equals(that.filter)
                && outputPath.equals(that.outputPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, filter, outputPath);
    }

    public static final class Builder {
        private String target;
        private String filter;
        private String outputPath;

        public Builder withTarget(String target) {
            this.target = target;
            return this;
        }

        public Builder withFilter(String filter) {
            this.filter = filter;
            return this;
        }

        public Builder withOutputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public ServiceLogsSettings build() {
            if ((target == null) || target.isEmpty()) {
                throw new IllegalStateException("The Service Logs Streaming settings must define a target regex");
            }
            if ((filter == null) || filter.isEmpty()) {
                filter = ServiceLogsSettings.UNASSIGNED;
            }
            if ((outputPath == null) || outputPath.isEmpty()) {
                outputPath = ServiceLogsSettings.UNASSIGNED;
            }
            return new ServiceLogsSettings(target, filter, outputPath);
        }
    }
}
