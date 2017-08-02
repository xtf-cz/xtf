package cz.xtf.model;

public enum TransportProtocol {
	TCP, UDP;

	public String lowercase() {
		return toString().toLowerCase();
	}

	public String uppercase() {
		return toString().toUpperCase();
	}
}