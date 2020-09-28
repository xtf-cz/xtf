package cz.xtf.builder.builders;

import java.util.Random;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.builder.builders.route.TransportProtocol;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;

public class PortBuilder extends AbstractBuilder<ServicePort, PortBuilder> {
    private TransportProtocol protocol = TransportProtocol.TCP;
    private int port = 80;
    private int targetPort = 0;

    public PortBuilder() {
        this(null, "port-" + Integer.toString(Math.abs(new Random().nextInt()), 36));
    }

    public PortBuilder(String name) {
        this(null, name);
    }

    PortBuilder(ApplicationBuilder applicationBuilder, String name) {
        super(applicationBuilder, name);
    }

    public PortBuilder useTCP() {
        protocol = TransportProtocol.TCP;
        return this;
    }

    public PortBuilder useUDP() {
        protocol = TransportProtocol.UDP;
        return this;
    }

    public PortBuilder port(int port) {
        this.port = port;
        return this;
    }

    public PortBuilder targetPort(int targetPort) {
        this.targetPort = targetPort;
        return this;
    }

    @Override
    public ServicePort build() {
        if (targetPort == 0) {
            throw new IllegalStateException("targetPort must be set for service");
        }
        ServicePortBuilder servicePort = new ServicePortBuilder();
        if (StringUtils.isNotBlank(getName())) {
            servicePort.withName(getName());
        }
        servicePort.withProtocol(protocol.uppercase())
                .withPort(port)
                .withNewTargetPort(targetPort);
        return servicePort.build();
    }

    @Override
    protected PortBuilder getThis() {
        return this;
    }
}
