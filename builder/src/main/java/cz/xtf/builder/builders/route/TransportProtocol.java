package cz.xtf.builder.builders.route;

public enum TransportProtocol {
    TCP,
    UDP;

    public String lowercase() {
        return toString().toLowerCase();
    }

    public String uppercase() {
        return toString().toUpperCase();
    }
}