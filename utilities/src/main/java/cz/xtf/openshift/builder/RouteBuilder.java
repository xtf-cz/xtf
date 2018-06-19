package cz.xtf.openshift.builder;

import cz.xtf.TestConfiguration;
import cz.xtf.keystore.XTFKeyStore;
import cz.xtf.openshift.OpenshiftUtil;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteSpecBuilder;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import org.apache.commons.lang3.StringUtils;

public class RouteBuilder extends AbstractBuilder<Route, RouteBuilder> {
	private static final OpenshiftUtil openshift = OpenshiftUtil.getInstance();
	private static String routeSuffix = null;

	static {
		routeSuffix = TestConfiguration.routeDomain();
		if(routeSuffix == null) {
			Route route = new Route();
			route.setMetadata(new ObjectMetaBuilder().withName("probing-route").build());
			route.setSpec(new RouteSpecBuilder().withNewTo().withKind("Service").withName("imaginary-service").endTo().build());

			route = openshift.createRoute(route);
			openshift.deleteRoute(route);

			routeSuffix = route.getSpec().getHost().replaceAll("^[^\\.]*\\.(.*)", "$1");
		}
	}

	private String hostName;
	private String serviceName;
	private String routeKey;
	private String routeCertificate;
	private String routeCA;
	private String serverCA;
	private TLSType tlsType;
	private int targetPort = 0;

	public static String createHostName(String appName) {
		return createHostName(appName, "-", ".");
	}

	public static String createHostName(String appName, String namespaceSeparator, String domainSeparator) {
		return String.format("%s%s%s%s%s",
				appName, namespaceSeparator, openshift.getContext().getNamespace(), domainSeparator, routeSuffix);
	}

	public static String createProxiedHostName(String appName) {
		return createProxiedHostName(appName, "-", ".");
	}

	public static String createProxiedHostName(String appName, String namespaceSeparator, String domainSeparator) {
		return String.format("%s%s%s%s%s%s%s",
				appName, namespaceSeparator, openshift.getContext().getNamespace(), domainSeparator, routeSuffix, domainSeparator, TestConfiguration.proxyDomain());
	}

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

	public RouteBuilder routeCA(String alias) {
		this.routeCA = XTFKeyStore.getInstance().getCertificate(alias);
		return this;
	}

	public RouteBuilder routeKey(String alias) {
		this.routeKey = XTFKeyStore.getInstance().getKey(alias);
		return this;
	}

	public RouteBuilder routeCertificate(String alias) {
		this.routeCertificate = XTFKeyStore.getInstance().getCertificate(alias);
		return this;
	}

	public RouteBuilder serverCA(String alias) {
		this.serverCA = XTFKeyStore.getInstance().getCertificate(alias);
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
		NONE, EDGE, PASSTHROUGH, REENCRYPT
	}
}
