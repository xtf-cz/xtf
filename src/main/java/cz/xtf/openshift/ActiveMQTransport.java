package cz.xtf.openshift;

public enum ActiveMQTransport {
	OPENWIRE("OPENWIRE", 61616, 61617),
	AMQP("AMQP", 5672, 5671),
	MQTT("MQTT", 1883, 8883),
	STOMP("STOMP", 61613, 61612);

	private final String value;
	private final int port;
	private final int sslPort;

	ActiveMQTransport(String value, int port, int sslPort) {
		this.value = value;
		this.port = port;
		this.sslPort = sslPort;
	}

	public int getPort() {
		return port;
	}

	public int getSslPort() {
		return sslPort;
	}

	public String toString() {
		return value.toLowerCase();
	}

}
