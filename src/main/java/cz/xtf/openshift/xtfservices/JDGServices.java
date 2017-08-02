package cz.xtf.openshift.xtfservices;

import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.RouteBuilder;

public final class JDGServices {
	private static final String SERVICE_CACHE_REST = "-rest";
	private static final String SERVICE_CACHE_MEMCACHED = "-mcd";
	private static final String SERVICE_CACHE_HOTROD = "-htrd";
	private static final String SERVICE_JMX = "-jmx";

	private static final String ROUTE_CACHE_REST = "-http-route";

	private JDGServices() {
	}

	public static void addRestService(ApplicationBuilder appBuilder, String appName) {
		appBuilder.service(appName + SERVICE_CACHE_REST)
				.addContainerSelector("application", appName)
				.addLabel("application", appName).setContainerPort(8080)
				.setPort(80);
	}

	public static void addMemcachedService(ApplicationBuilder appBuilder, String appName) {
		appBuilder.service(appName + SERVICE_CACHE_MEMCACHED)
				.addContainerSelector("application", appName)
				.addLabel("application", appName).setContainerPort(11211)
				.setPort(11211);
	}

	public static void addHotrodService(ApplicationBuilder appBuilder, String appName) {
		appBuilder.service(appName + SERVICE_CACHE_HOTROD)
				.addContainerSelector("application", appName)
				.addLabel("application", appName).setContainerPort(11222)
				.setPort(11222);
	}

	public static void addJMXService(ApplicationBuilder appBuilder, String appName) {
		appBuilder.service(appName + SERVICE_JMX)
				.addContainerSelector("application", appName)
				.addLabel("application", appName).setContainerPort(9999)
				.setPort(9999);
	}

	public static void addRestRoute(ApplicationBuilder appBuilder, String appName) {
		appBuilder.route(appName + ROUTE_CACHE_REST)
				.addLabel("application", appName)
				.forService(appName + SERVICE_CACHE_REST)
				.exposedAsHost(RouteBuilder.createHostName(appName));
	}

}
