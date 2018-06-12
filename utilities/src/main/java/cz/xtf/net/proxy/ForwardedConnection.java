package cz.xtf.net.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class ForwardedConnection implements Runnable, Closeable {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(ForwardedConnection.class);

	private final String targetHost;
	private final int targetPort;
	private final ProxiedConnectionManager manager;

	private int port;

	public ForwardedConnection(final ProxiedConnectionManager manager, final String targetHost, final int targetPort) {
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.manager = manager;
	}

	@Override
	public void run() {
	}

	public int getPort() {
		return manager.getListenPort();
	}

	public String getHost() {
		return manager.getProxyHost();
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public String toString() {
		return String.format("Connection(%s:%s -> %s:%s)", manager.getProxyHost(), port, targetHost, targetPort);
	}
}
