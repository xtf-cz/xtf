package cz.xtf.core.service.logs.streaming;

/**
 * Helper class exposing utilities to the Service Logs Streaming component.
 */
public class ServiceLogUtils {

    public static final String XTF_SLS_HEADER_LABEL = "XTF Service Logs Streaming";

    /**
     * Adds a prefix to the message which is passed in, in order to display that it is part of the logs streaming
     * process, i.e. by adding a conventional part to the beginning of the message.
     *
     * @param originalMessage the original message
     * @return A string made up by the conventional prefix, followed by the original message
     */
    public static String getConventionallyPrefixedLogMessage(final String originalMessage) {
        return String.format("[" + XTF_SLS_HEADER_LABEL + "] > %s", originalMessage);
    }

    /**
     * Formats the given prefix into a conventional header for a any streamed log line so that it is clear to the end
     * user that it is coming from a service log rather than from the current process one
     *
     * @param prefix The prefix that will be part of the header
     * @return A string representing the header for a streamed log line which will be written to the output stream
     */
    public static String formatStreamedLogLine(final String prefix) {
        return String.format("[" + XTF_SLS_HEADER_LABEL + " - %s] > ", prefix);
    }
}
