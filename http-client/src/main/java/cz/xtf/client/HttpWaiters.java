package cz.xtf.client;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;

import java.io.IOException;
import java.util.function.BooleanSupplier;

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
		return new SimpleWaiter(bs, "Waiting for " + client.getUrl().toExternalForm() + " to return " + code).timeout(WaitingConfig.timeout());
	}
}
