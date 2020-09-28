package cz.xtf.client;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;

public class HttpWaiters {
    private final Http client;

    HttpWaiters(Http httpClient) {
        this.client = httpClient;
    }

    public Waiter ok() {
        return code(200);
    }

    public Waiter code(int code) {
        BooleanSupplier bs = () -> {
            try {
                return client.execute().code() == code;
            } catch (IOException e) {
                return false;
            }
        };
        return new SimpleWaiter(bs, "Waiting for " + client.getUrl().toExternalForm() + " to return " + code)
                .timeout(WaitingConfig.timeout());
    }

    public Waiter responseContains(final String... strings) {
        BooleanSupplier bs = () -> {
            try {
                final String response = client.execute().response();
                return Stream.of(strings).allMatch(response::contains);
            } catch (IOException e) {
                return false;
            }
        };
        return new SimpleWaiter(bs,
                "Waiting for " + client.getUrl().toExternalForm() + " to contain (" + String.join(",", strings) + ")")
                        .timeout(WaitingConfig.timeout());
    }
}
