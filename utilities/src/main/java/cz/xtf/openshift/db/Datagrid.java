package cz.xtf.openshift.db;


import cz.xtf.TestConfiguration;
import cz.xtf.model.TransportProtocol;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.ServiceBuilder;
import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.imagestream.ImageRegistry;

import java.util.HashMap;
import java.util.Map;

public class Datagrid extends AbstractDatabase {

	private final String appName;
	private final String username;
	private final String password;
	private final String connectors;
	private final String cacheNames;
	private final String datavirtCacheNames;
	private final String hotrodSvcName;
	private final String jgroupsPasswd;

	private  final boolean createService;
	private final int HOTROD_PORT = 11333;
	private final int JOLOKIA_PORT = 8778;

	public Datagrid(boolean withLivenessProbe, boolean withReadinessProbe) {
		this("datagrid-app", "testdb", "testpwd123", "DATAGRID", null, withLivenessProbe,
				withReadinessProbe, "hotrod,memcached,rest", "default,test", "", "datagrid-app-hotrod", "testpwd123", true);
	}

	public Datagrid(final String appName) {
		this(appName, "testdb", "testpwd123", "DATAGRID", null, true,
				true, "hotrod,memcached,rest", "default,test", "", "datagrid-app-hotrod", "testpwd123", true);
	}

	public Datagrid(String appName, String username, String password, String connectors, String cacheNames, String datavirtCacheNames, String hotrodSvcName, String jgroupsPasswd, boolean createService) {
		this(appName, username, password, "DATAGRID", null, true, true, connectors, cacheNames, datavirtCacheNames, hotrodSvcName, jgroupsPasswd, createService);
	}

	public Datagrid(String appName, String username, String password, String symbolicName, String dataDir,
					boolean withLivenessProbe, boolean withReadinessProbe, String connectors, String cacheNames,
					String datavirtCacheNames, String hotrodSvcName, String jgroupsPasswd, boolean createService) {
		super(username, password, "testdb", symbolicName, dataDir, withLivenessProbe, withReadinessProbe);
		this.appName = appName;
		this.username = username;
		this.password = password;
		this.connectors = connectors;
		this.cacheNames = cacheNames;
		this.datavirtCacheNames = datavirtCacheNames;
		this.hotrodSvcName = hotrodSvcName;
		this.jgroupsPasswd = jgroupsPasswd;
		this.createService = createService;
	}

	@Override
	public String getImageName() {
		return ImageRegistry.get().jdg();
	}

	@Override
	public int getPort() {
		return HOTROD_PORT;
	}

	@Override
	public Map<String, String> getImageVariables() {
		Map<String, String> vars = new HashMap<>();
		vars.put("USERNAME", username);
		vars.put("PASSWORD", password);
		vars.put("OPENSHIFT_KUBE_PING_LABELS", "application=" + appName);
		vars.put("OPENSHIFT_KUBE_PING_NAMESPACE", TestConfiguration.masterNamespace());
		vars.put("INFINISPAN_CONNECTORS", connectors);
		vars.put("CACHE_NAMES", cacheNames);
		vars.put("DATAVIRT_CACHE_NAMES", datavirtCacheNames);
		vars.put("HOTROD_SERVICE_NAME", hotrodSvcName);
		vars.put("JGROUPS_CLUSTER_PASSWORD", jgroupsPasswd);
		vars.put("ENV_FILES", "/etc/datagrid-environment/*");
		return vars;
	}

	@Override
	protected void configureContainer(ContainerBuilder containerBuilder) {
		if (withLivenessProbe) {
			containerBuilder.addLivenessProbe().setInitialDelay(30)
					.createExecProbe("/bin/sh", "-c", "/opt/datagrid/bin/livenessProbe.sh");
		}

		if (withReadinessProbe) {
			containerBuilder.addReadinessProbe().setInitialDelaySeconds(5)
					.createExecProbe("/bin/sh", "-c", "/opt/datagrid/bin/readinessProbe.sh");
		}

		// Configure jolokia
		containerBuilder.port(JOLOKIA_PORT, TransportProtocol.TCP, "jolokia");
	}

	/**
	 * Datagrid as opposite to most of other data sources
	 * needs the service to be created before container
	 * startup.
	 * @param appBuilder
	 * @return
	 */
	@Override
	public void configureService(ApplicationBuilder appBuilder) {
		if (this.createService) {
			super.configureService(appBuilder);
		} else {
			new ServiceBuilder(appName)
					.addLabel("application", appBuilder.getName())
					.setPort(HOTROD_PORT)
					.setContainerPort(HOTROD_PORT)
					.useTCP()
					.addContainerSelector("name", appName);

		}
	}

}
