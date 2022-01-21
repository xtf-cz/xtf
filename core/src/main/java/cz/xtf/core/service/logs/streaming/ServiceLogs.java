package cz.xtf.core.service.logs.streaming;

/**
 * Defines the contract to manage the life cycle of a cloud Service Logs Streaming component
 */
public interface ServiceLogs {
    /**
     * Start watching Service logs
     */
    void start();

    /**
     * Stop watching Service logs
     */
    void stop();
}
