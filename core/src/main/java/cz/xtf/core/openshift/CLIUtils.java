package cz.xtf.core.openshift;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CLIUtils {

    public static String executeCommand(Map<String, String> environmentVariables, String... args) {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().putAll(environmentVariables);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);

        try {
            Process p = pb.start();

            ExecutorService es = Executors.newFixedThreadPool(2);

            Future<String> out = es.submit(() -> {
                try (InputStream is = p.getInputStream(); StringWriter sw = new StringWriter()) {
                    IOUtils.copy(is, sw);
                    return sw.toString();
                }
            });

            Future<String> err = es.submit(() -> {
                try (InputStream is = p.getErrorStream(); StringWriter sw = new StringWriter()) {
                    IOUtils.copy(is, sw);
                    return sw.toString();
                }
            });

            int result = p.waitFor();

            if (result == 0) {
                return out.get();
            } else {
                log.error("Failed while executing (code {}): {}", result, Arrays.toString(args));
                log.error(err.get());
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("Failed while executing: " + Arrays.toString(args), e);
        }

        return null;
    }

    public static String executeCommand(String... args) {
        return executeCommand(Collections.emptyMap(), args);
    }
}
