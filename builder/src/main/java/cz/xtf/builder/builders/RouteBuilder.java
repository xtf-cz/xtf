package cz.xtf.builder.builders;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteSpecBuilder;
import io.fabric8.openshift.api.model.TLSConfigBuilder;

public class RouteBuilder extends AbstractBuilder<Route, RouteBuilder> {
    private String hostName;
    private String serviceName;
    private String routeKey;
    private String routeCertificate;
    private String routeCA;
    private String serverCA;
    private TLSType tlsType;
    private int targetPort = 0;

    public RouteBuilder(String routeName) {
        this(null, routeName);
    }

    RouteBuilder(ApplicationBuilder applicationBuilder, String routeName) {
        super(applicationBuilder, routeName);
        tlsType = TLSType.NONE;
    }

    public RouteBuilder forService(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public RouteBuilder exposedAsHost(String hostName) {
        this.hostName = hostName;
        return this;
    }

    public RouteBuilder edge() {
        tlsType = TLSType.EDGE;
        return this;
    }

    public RouteBuilder passthrough() {
        tlsType = TLSType.PASSTHROUGH;
        return this;
    }

    public RouteBuilder reencrypt() {
        tlsType = TLSType.REENCRYPT;
        return this;
    }

    public RouteBuilder defaultRouteCA() {
        routeCA("authority");
        return this;
    }

    public RouteBuilder routeCA(String routeCA) {
        this.routeCA = routeCA;
        return this;
    }

    public RouteBuilder routeKey(String routeKey) {
        this.routeKey = routeKey;
        return this;
    }

    public RouteBuilder routeCertificate(String routeCertificate) {
        this.routeCertificate = routeCertificate;
        return this;
    }

    public RouteBuilder serverCA(String serverCA) {
        this.serverCA = serverCA;
        return this;
    }

    public RouteBuilder targetPort(int targetPort) {
        this.targetPort = targetPort;
        return this;
    }

    @Override
    public Route build() {
        RouteSpecBuilder spec = new RouteSpecBuilder()
                .withHost(hostName)
                .withNewTo()
                .withKind("Service")
                .withName(serviceName)
                .endTo();

        if (targetPort != 0) {
            spec.withNewPort().withNewTargetPort(targetPort).endPort();
        }

        if (tlsType != TLSType.NONE) {
            TLSConfigBuilder tls = new TLSConfigBuilder()
                    .withTermination(tlsType.toString().toLowerCase());

            if (StringUtils.isNotBlank(routeKey)) {
                tls.withKey(routeKey);
            }
            if (StringUtils.isNotBlank(routeCertificate)) {
                tls.withCertificate(routeCertificate);
            }
            if (StringUtils.isNotBlank(routeCA)) {
                tls.withCaCertificate(routeCA);
            }
            if (StringUtils.isNotBlank(serverCA)) {
                tls.withDestinationCACertificate(serverCA);
            }
            spec.withTls(tls.build());
        }

        return new io.fabric8.openshift.api.model.RouteBuilder()
                .withMetadata(metadataBuilder().build())
                .withSpec(spec.build())
                .build();
    }

    @Override
    protected RouteBuilder getThis() {
        return this;
    }

    private enum TLSType {
        NONE,
        EDGE,
        PASSTHROUGH,
        REENCRYPT
    }
}
