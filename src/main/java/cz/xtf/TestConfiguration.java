package cz.xtf;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;

public final class TestConfiguration {

	private static final String DOMAIN = "xtf.config.domain";
	private static final String MASTER_URL = "xtf.config.master.url";
	private static final String MASTER_USERNAME = "xtf.config.master.username";
	private static final String MASTER_PASSWORD = "xtf.config.master.password";
	private static final String MASTER_TOKEN = "xtf.config.master.token";
	private static final String MASTER_NAMESPACE = "xtf.config.master.namespace";
	private static final String MASTER_SSH_USERNAME = "xtf.config.master.ssh_username";
	private static final String MASTER_SSH_KEY_PATH = "xtf.config.master.ssh_key_path";
	private static final String ADMIN_USERNAME = "xtf.config.master.admin.username";
	private static final String ADMIN_PASSWORD = "xtf.config.master.admin.password";
	private static final String CLEAN_NAMESPACE = "xtf.cleannamespace";
	private static final String JACOCO_ENABLED = "xtf.jacoco";
	private static final String JACOCO_PATH = "xtf.jacoco.path";
	private static final String NFS_SERVER = "xtf.config.nfs.addr";
	private static final String NFS_SSH_USERNAME = "xtf.config.nfs.ssh_username";
	private static final String PROXY_HOST = "xtf.config.proxy.host";
	private static final String PROXY_HOST_USERNAME = "xtf.config.proxy.host.username";
	private static final String PROXY_HOSTS = "xtf.config.proxy.hosts";
	private static final String PROXY_SQUID_PORT = "xtf.config.proxy.squid.port";
	private static final String PROXY_VERTX_PORT = "xtf.config.proxy.vertx.port";
	private static final String ROUTE_DOMAIN = "xtf.config.route_domain";
	private static final String PROXY_DOMAIN = "xtf.config.domain.proxy";
	private static final String SOAK_TEST_ITERATIONS = "xtf.config.soak_test_iterations";
	private static final String IMAGE_REPO_BASE = "xtf.config.image.repo";
	private static final String MAVEN_BASE_URL = "xtf.config.maven.web.url";
	private static final String MAVEN_PROXY_URL = "xtf.config.maven.proxy.url";
	private static final String MAVEN_PROXY_GROUP = "xtf.config.maven.proxy.group";
	private static final String MAVEN_PROXY_ENABLED = "xtf.config.maven.proxy.enabled";
	private static final String MAVEN_DEPLOY_SNAPSHOT_URL = "xtf.config.maven.deploy.snapshot.url";
	private static final String MAVEN_DEPLOY_RELEASE_URL = "xtf.config.maven.deploy.release.url";
	private static final String GITLAB_URL = "xtf.config.gitlab.url";
	private static final String GITLAB_TOKEN = "xtf.config.gitlab.token";
	private static final String GITLAB_USERNAME = "xtf.config.gitlab.username";
	private static final String GITLAB_PASSWORD = "xtf.config.gitlab.password";
	private static final String GITLAB_GROUP = "xtf.config.gitlab.group";
	private static final String PING_PROTOCOL = "xtf.config.ping.protocol";
	private static final String FABRIC8_VERSION = "xtf.config.fabric8.version";
	private static final String FABRIC8_SERVICE_NAME_LIMIT = "xtf.config.fabric8.service_name_limit";
	private static final String FUSE_CACHED_IMAGES = "xtf.config.fuse.cached.images";
	private static final String FUSE_DISABLE_JOLOKIA = "xtf.config.fuse.disable.jolokia";
	private static final String BUILD_NAMESPACE = "xtf.config.build.namespace";
	private static final String FORCE_REBUILD = "xtf.config.build.force.rebuild";
	private static final String BINARY_BUILD = "xtf.config.build.binary";
	private static final String MAX_HTTP_TRIES = "util.http.maxtries";
	private static final String DEFAULT_WAIT_TIMEOUT = "xtf.config.wait.timeout.default";
	private static final String EXTERNAL_SERVICES_HOST = "xtf.config.services.external";
	private static final String OPENSHIFT_ONLINE = "xtf.config.openshift.online";
	private static final String OPENSHIFT_VERSION = "xtf.config.openshift.version";
	private static final String TEST_ALERT = "test.alert";
	private static final String TEST_JENKINS_RERUN = "test.jenkins.rerun";
	private static final String TEMPLATE_REPO = "xtf.config.template.repo";
	private static final String TEMPLATE_BRANCH = "xtf.config.template.branch";

	private static final String OPENSTACK_URL = "xtf.config.openstack.url";
	private static final String OPENSTACK_TENANT = "xtf.config.openstack.tenant";
	private static final String OPENSTACK_OPEN_SECURITY_GROUP = "xtf.config.openstack.security.group.open";
	private static final String OPENSTACK_USERNAME = "xtf.config.openstack.username";
	private static final String OPENSTACK_PASSWORD = "xtf.config.openstack.password";

	public static final String EAP_LOCATION = "xtf.config.eap.location";
	public static final String IMAGE_EAP_PREFIX = "xtf.eap.";
	public static final String IMAGE_PREVIOUS_SUFFIX = ".previous";
	public static final String IMAGE_EAP_6 = IMAGE_EAP_PREFIX + "6";
	public static final String IMAGE_EAP_7 = IMAGE_EAP_PREFIX + "7";
	public static final String IMAGE_JDG = "xtf.jdg";
	public static final String IMAGE_JDG_CLIENT = "xtf.jdg.client";
	public static final String IMAGE_JDV = "xtf.jdv";
	public static final String IMAGE_JDV_CLIENT = "xtf.jdv.client";
	public static final String IMAGE_JDV_ODBC_TEST_IMAGE = "xtf.jdv.odbc.test";
	public static final String IMAGE_EWS_PREFIX = "org.apache.tomcat";
	public static final String IMAGE_TOMCAT7 = IMAGE_EWS_PREFIX + "7";
	public static final String IMAGE_TOMCAT8 = IMAGE_EWS_PREFIX + "8";
	public static final String IMAGE_AMQ = "xtf.amq";
	public static final String IMAGE_POSTGRES = "org.postgresql";
	public static final String IMAGE_DERBY = "org.apache.derby";
	public static final String IMAGE_MYSQL = "com.mysql";
	public static final String IMAGE_MONGO = "com.mongodb";
	public static final String IMAGE_NFS = "org.nfs";
	public static final String IMAGE_FUSE_JAVA_MAIN = "org.fuse.java_main";
	public static final String IMAGE_FUSE_KARAF = "org.fuse.karaf";
	public static final String IMAGE_BRMS = "xtf.brms";
	public static final String IMAGE_BPMS = "xtf.bpms";
	public static final String IMAGE_BPMS_LDAP_TEST_IMAGE = "xtf.bpms.ldap.test";
	public static final String IMAGE_SSO = "xtf.sso";
	public static final String IMAGE_PHANTOMJS = "xtf.phantomjs";
	public static final String IMAGE_H2 = "xtf.h2";
	public static final String IMAGE_MSA = "xtf.msa";
	public static final String IMAGE_ZIPKIN = "io.zipkin.java";
	public static final String IMAGE_RAD = "xtf.rad.spark";
	public static final String IMAGE_OSHINKO_REST = "xtf.rad.oshinko.rest";
	public static final String IMAGE_OSHINKO_WEBUI = "xtf.rad.oshinko.webui";
	public static final String IMAGE_OSHINKO_CLI = "xtf.rad.oshinko.cli";
	public static final String IMAGE_PY_SPARK = "xtf.rad.driver.python";
	public static final String IMAGE_JAVA_SPARK = "xtf.rad.driver.java";
	public static final String IMAGE_SCALA_SPARK = "xtf.rad.driver.scala";
	public static final String IMAGE_SQUID = "org.squid-cache";
	public static final String IMAGE_TCP_PROXY = "xtf.tcp-proxy";
	public static final String IMAGE_MM_SERVICE = "org.hawkular.service";
	public static final String IMAGE_MM_DATASTORE = "org.hawkular.datastore";

	public static final String VERSION_EAP = "xtf.version.eap";
	public static final String VERSION_JDV = "xtf.version.jdv";
	public static final String VERSION_JDG = "xtf.version.jdg";
	public static final String VERSION_EWS = "xtf.version.ews";
	public static final String VERSION_FUSE = "xtf.version.fuse";
	public static final String VERSION_KIE = "xtf.version.kie";
	public static final String VERSION_JDK = "xtf.version.jdk";
	public static final String VERSION_SSO = "xtf.version.sso";
	public static final String VERSION_AMQ = "xtf.version.amq";
	public static final String VERSION_MSA = "xtf.version.msa";

	public static final String CDK_DOMAIN = "cd.xtf.cz";
	public static final String CDK_IP = "10.1.2.2";
	public static final String CDK_INTERNAL_HOSTNAME = "localhost.localdomain";

	public static final String OC_BINARY_LOCATION = "oc.binary.location";

	public static final String CI_USERNAME = "ci.username";
	public static final String CI_PASSWORD = "ci.password";

	private static final Logger LOGGER = LoggerFactory.getLogger(TestConfiguration.class);
	private static final TestConfiguration INSTANCE = new TestConfiguration();

	private final Properties properties = new Properties();

	private TestConfiguration() {
		// first let's try product properties
		copyValues(fromPath("test.properties"), true);

		// then product properties
		copyValues(fromPath("../test.properties"));

		// then system variables
		copyValues(System.getProperties());

		// then environment variables
		copyValues(fromEnvironment());

		// Use global properties stored in repository - typically images - if not set before
		if (Paths.get("global-test.properties").toAbsolutePath().toFile().exists()) {
			copyValues(fromPath("global-test.properties"));
		}
		else if (Paths.get("../global-test.properties").toAbsolutePath().toFile().exists()) {
			copyValues(fromPath("../global-test.properties"));
		}

		// then defaults
		copyValues(defaultValues());
	}

	public static TestConfiguration get() {
		return INSTANCE;
	}

	public static String masterUrl() {
		String result = get().readValue(DOMAIN);
		if (StringUtils.isNotBlank(result)) {
			result = "https://" + (result.equals(CDK_DOMAIN) ? CDK_IP : ("api." + result)) + ":8443";
		} else {
			result = get().readValue(MASTER_URL);
		}

		return result;
	}

	public static String masterUsername() {
		return get().readValue(MASTER_USERNAME);
	}

	public static String masterPassword() {
		return get().readValue(MASTER_PASSWORD);
	}

	public static String getMasterToken() {
		return get().readValue(MASTER_TOKEN);
	}

	public static String adminUsername() {
		return get().readValue(ADMIN_USERNAME);
	}

	public static String adminPassword() {
		return get().readValue(ADMIN_PASSWORD);
	}

	public static String masterNamespace() {
		return get().readValue(MASTER_NAMESPACE);
	}

	public static String masterSshUsername() {
		return get().readValue(MASTER_SSH_USERNAME);
	}

	public static String masterSshKeyPath() {
		return get().readValue(MASTER_SSH_KEY_PATH);
	}

	public static String domain() {
		return get().readValue(DOMAIN);
	}

	public static String routeDomain() {
		String result = get().readValue(DOMAIN);
		if (StringUtils.isNotBlank(result)) {
			result = "apps." + result;
		} else {
			result = get().readValue(ROUTE_DOMAIN);
		}

		return result;
	}

	public static String proxyHost() {
		return get().readValue(PROXY_HOST);
	}

	public static String proxyHostUsername() {
		return get().readValue(PROXY_HOST_USERNAME);
	}

	public static String proxyDomain() {
		return get().readValue(PROXY_DOMAIN);
	}

	public static String nfsServer() {
		String result = get().readValue(DOMAIN);
		if (StringUtils.isNotBlank(result)) {
			result = "nfs." + result;
		} else {
			result = get().readValue(NFS_SERVER);
		}

		return result;
	}

	public static String nfsSshUsername() {
		return get().readValue(NFS_SSH_USERNAME);
	}

	public static String proxyHostsString() {
		return get().readValue(PROXY_HOSTS);
	}

	public static String openStackURL() {
		return get().readValue(OPENSTACK_URL);
	}

	public static String openStackTenant() {
		return get().readValue(OPENSTACK_TENANT);
	}

	public static String openStackOpenSecurityGroup() {
		return get().readValue(OPENSTACK_OPEN_SECURITY_GROUP);
	}

	public static String openStackUsername() {
		return get().readValue(OPENSTACK_USERNAME);
	}

	public static String openStackPassword() {
		return get().readValue(OPENSTACK_PASSWORD);
	}

	public static String ciUsername() {
		return get().readValue(CI_USERNAME);
	}

	public static String ciPassword() {
		return get().readValue(CI_PASSWORD);
	}

	public static String fabric8Version() { return get().readValue(FABRIC8_VERSION);	}

	public static int fabric8ServiceNameLimit() {
		return Integer.parseInt(get().readValue(FABRIC8_SERVICE_NAME_LIMIT));
	}

	public static String kieVersion() {
		return get().readValue(VERSION_KIE);
	}

	public static boolean fuseCachedImages() { return Boolean.parseBoolean(get().readValue(FUSE_CACHED_IMAGES));	}

	public static boolean fuseDisableJolokia() {
		return Boolean.parseBoolean(get().readValue(FUSE_DISABLE_JOLOKIA));
	}

	public static List<HttpHost> squidProxyHosts() {
		final List<String> hostStrings = Arrays.asList(proxyHostsString().split(","));
		final int port = squidProxyPort();

		return hostStrings.stream()
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(s -> new HttpHost(s, port))
				.collect(Collectors.toList());
	}

	public static List<HttpHost> vertxProxyHosts() {
		final List<String> hostStrings = Arrays.asList(proxyHostsString().split(","));
		final int port = vertxProxyPort();

		return hostStrings.stream()
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(s -> new HttpHost(s, port))
				.collect(Collectors.toList());
	}

	public static int squidProxyPort() {
		return Integer.parseInt(get().readValue(PROXY_SQUID_PORT));
	}

	public static int vertxProxyPort() {
		return Integer.parseInt(get().readValue(PROXY_VERTX_PORT));
	}

	public static int soakTestIterations() {
		return Integer.parseInt(get().readValue(SOAK_TEST_ITERATIONS));
	}

	public static boolean cleanNamespace() {
		return Boolean.parseBoolean(get().readValue(CLEAN_NAMESPACE));
	}

	public static boolean testAlert() {
		return Boolean.parseBoolean(get().readValue(TEST_ALERT));
	}

	public static boolean jacoco() {
		return Boolean.parseBoolean(get().readValue(JACOCO_ENABLED));
	}

	public static String jacocoPath() {
		return get().readValue(JACOCO_PATH);
	}

	/**
	 * Get maven proxy base URL.
	 * If no value is defined in environment new value is generated
	 * @return the proxy base URL in format 'http://url'
	 */
	public static String mavenBaseURL() {
		String result = get().readValue(MAVEN_BASE_URL);
		if (StringUtils.isBlank(result)) {
			result = "http://maven." + get().readValue(DOMAIN);
		} else {
			final String http = result.startsWith("http") ? "" : "http://";
			result = http + result;
		}
		return result;
	}

	/**
	 * Get maven proxy URL.
	 * If no value is defined in environment new value is generated (using hardcoded
	 * properties derived from Nexus API)
	 * @return the proxy URL in format 'http://url/endpoint'
	 */
	public static String mavenProxyURL() {
		String result = get().readValue(MAVEN_PROXY_URL);
		if(StringUtils.isBlank(result)) {
			result = mavenBaseURL() + "/nexus/content/groups/" + get().readValue(MAVEN_PROXY_GROUP) + "/";
		} else {
			final String http = result.startsWith("http") ? "" : "http://";
			result = http + result;
		}
		return result;
	}

	public static String centralMavenProxyURL() {
		return mavenBaseURL() + "/nexus/content/repositories/central/";
	}

	public static String secureMavenProxyURL() {
		return mavenProxyURL().replace("http://", "https://secure-");
	}

	/**
	 * Get maven deployment URL.
	 * If no value is defined in environment new value is generated (using hardcoded
	 * properties derived from Indy API)
	 * @return the deployment URL in format 'http://url/endpoint'
	 */
	public static String mavenDeploySnapshotURL() {
		String result = get().readValue(MAVEN_DEPLOY_SNAPSHOT_URL);
		if(StringUtils.isBlank(result)) {
			result = mavenBaseURL() + "/nexus/content/repositories/local-deployments/";
		} else {
			final String http = result.startsWith("http") ? "" : "http://";
			result = http + result;
		}
		return result;
	}

	public static String mavenDeployReleaseURL() {
		String result = get().readValue(MAVEN_DEPLOY_RELEASE_URL);
		if(StringUtils.isBlank(result)) {
			result = mavenBaseURL() + "/nexus/content/repositories/local-deployments-release/";
		} else {
			final String http = result.startsWith("http") ? "" : "http://";
			result = http + result;
		}
		return result;
	}

	public static String mavenProxyGroup() {
		return get().readValue(MAVEN_PROXY_GROUP);
	}

	public static boolean mavenProxyEnabled() {
		return Boolean.parseBoolean(get().readValue(MAVEN_PROXY_ENABLED));
	}

	public static String gitLabURL() {
		String result = get().readValue(DOMAIN);
		if (StringUtils.isNotBlank(result)) {
			result = "http://gitlab." + result;
		} else {
			result = get().readValue(GITLAB_URL);
		}
		return result;
	}

	@Deprecated
	public static String gitLabToken() {
		return get().readValue(GITLAB_TOKEN);
	}

	public static boolean dynamicGitLab() {
		return StringUtils.isNotBlank(get().readValue(DOMAIN))
				|| "disabled".equals(get().readValue(GITLAB_TOKEN));
	}

	public static String gitLabUsername() {
		return get().readValue(GITLAB_USERNAME);
	}

	public static String gitLabPassword() {
		return get().readValue(GITLAB_PASSWORD);
	}

	public static String gitLabGroup() {
		return get().readValue(GITLAB_GROUP);
	}

	public static String getFuseVersion() {
		return get().readValue(VERSION_FUSE);
	}

	public static String getMsaVersion() {
		return get().readValue(VERSION_MSA);
	}

	public static String buildNamespace() {
		return get().readValue(BUILD_NAMESPACE);
	}
	
	public static boolean forceRebuild() {
		return Boolean.parseBoolean(get().readValue(FORCE_REBUILD));
	}
	
	public static boolean binaryBuild() {
		return Boolean.parseBoolean(get().readValue(BINARY_BUILD));
	}

	public static boolean openshiftOnline() {
		return Boolean.parseBoolean(get().readValue(OPENSHIFT_ONLINE));
	}

	public static String openshiftVersion() {
		return get().readValue(OPENSHIFT_VERSION);
	}

	public static int maxHttpTries() {
		return Integer.parseInt(get().readValue(MAX_HTTP_TRIES));
	}

	public static int defaultWaitTimeout() {
		return Integer.parseInt(get().readValue(DEFAULT_WAIT_TIMEOUT));
	}

	public static String externalServicesHost() {
		return get().readValue(EXTERNAL_SERVICES_HOST);
	}

	public static String templateRepo() {
		return get().readValue(TEMPLATE_REPO);
	}

	public static String templateBranch() {
		return get().readValue(TEMPLATE_BRANCH);
	}

	public static String testJenkinsRerun() {
		return get().readValue(TEST_JENKINS_RERUN);
	}

	public static PingProtocol pingProtocol() {
		switch(get().readValue(PING_PROTOCOL)) {
			case "dnsping": return PingProtocol.DNSPING;
			case "kubeping": return PingProtocol.KUBEPING;
			default: throw new IllegalStateException(PING_PROTOCOL + " can only be 'dnsping' or 'kubeping'");
		}
	}

	public static String ocBinaryLocation() {
		return get().readValue(OC_BINARY_LOCATION);
	}

	public static String testInfrastructureProject() {
		return "test-infra";
	}

	public static String secureMavenProxyRoute() {
		return "nexus-secure";
	}

	public String readValue(final String key) {
		return readValue(key, null);
	}

	public String readValue(final String key, final String defaultValue) {
		return this.properties.getProperty(key, defaultValue);
	}

	private Properties fromPath(final String path) {
		final Properties props = new Properties();

		final Path propsPath = Paths.get(path)
				.toAbsolutePath();
		if (Files.isReadable(propsPath)) {
			try (InputStream is = Files.newInputStream(propsPath)) {
				props.load(is);
			} catch (final IOException ex) {
				LOGGER.warn("Unable to read properties from '{}'", propsPath);
				LOGGER.debug("Exception", ex);
			}
		}

		return props;
	}

	private Properties fromEnvironment() {
		final Properties props = new Properties();

		for (final Entry<String, String> entry : System.getenv()
				.entrySet()) {
			switch (entry.getKey()) {
				case "DOMAIN":
					props.setProperty(DOMAIN, entry.getValue());
					break;
				case "MASTER_URL":
					props.setProperty(MASTER_URL, entry.getValue());
					break;
				case "MASTER_USERNAME":
					props.setProperty(MASTER_USERNAME, entry.getValue());
					break;
				case "MASTER_PASSWORD":
					props.setProperty(MASTER_PASSWORD, entry.getValue());
					break;
				case "MASTER_NAMESPACE":
					props.setProperty(MASTER_NAMESPACE, entry.getValue());
					break;
				case "MASTER_TOKEN":
					props.setProperty(MASTER_TOKEN, entry.getValue());
					break;
				case "MASTER_SSH_USERNAME":
					props.setProperty(MASTER_SSH_USERNAME, entry.getValue());
					break;
				case "MASTER_SSH_KEY_PATH":
					props.setProperty(MASTER_SSH_KEY_PATH, entry.getValue());
					break;
				case "ROUTE_DOMAIN":
					props.setProperty(ROUTE_DOMAIN, entry.getValue());
					break;
				case "PROXY_DOMAIN":
					props.setProperty(PROXY_DOMAIN, entry.getValue());
					break;
				case "ADMIN_USERNAME":
					props.setProperty(ADMIN_USERNAME, entry.getValue());
					break;
				case "ADMIN_PASSWORD":
					props.setProperty(ADMIN_PASSWORD, entry.getValue());
					break;
				case "CLEAN_NAMESPACE":
					props.setProperty(CLEAN_NAMESPACE, entry.getValue());
					break;
				case "IMAGE_EAP_6":
					props.setProperty(IMAGE_EAP_6, entry.getValue());
					break;
				case "IMAGE_EAP_7":
					props.setProperty(IMAGE_EAP_7, entry.getValue());
					break;
				case "IMAGE_JDG":
					props.setProperty(IMAGE_JDG, entry.getValue());
					break;
				case "IMAGE_JDG_CLIENT":
					props.setProperty(IMAGE_JDG_CLIENT, entry.getValue());
					break;
				case "IMAGE_JDV":
					props.setProperty(IMAGE_JDV, entry.getValue());
					break;
				case "IMAGE_JDV_CLIENT":
					props.setProperty(IMAGE_JDV_CLIENT, entry.getValue());
					break;
				case "IMAGE_TOMCAT7":
					props.setProperty(IMAGE_TOMCAT7, entry.getValue());
					break;
				case "IMAGE_TOMCAT8":
					props.setProperty(IMAGE_TOMCAT8, entry.getValue());
					break;
				case "IMAGE_AMQ":
					props.setProperty(IMAGE_AMQ, entry.getValue());
					break;
				case "IMAGE_POSTGRES":
					props.setProperty(IMAGE_POSTGRES, entry.getValue());
					break;
				case "IMAGE_DERBY":
					props.setProperty(IMAGE_DERBY, entry.getValue());
					break;
				case "IMAGE_MYSQL":
					props.setProperty(IMAGE_MYSQL, entry.getValue());
					break;
				case "IMAGE_MONGO":
					props.setProperty(IMAGE_MONGO, entry.getValue());
					break;
				case "IMAGE_NFS":
					props.setProperty(IMAGE_NFS, entry.getValue());
					break;
				case "IMAGE_FUSE_JAVA_MAIN":
					props.setProperty(IMAGE_FUSE_JAVA_MAIN, entry.getValue());
					break;
				case "IMAGE_FUSE_KARAF":
					props.setProperty(IMAGE_FUSE_KARAF, entry.getValue());
					break;
				case "IMAGE_BRMS":
					props.setProperty(IMAGE_BRMS, entry.getValue());
					break;
				case "IMAGE_BPMS":
					props.setProperty(IMAGE_BPMS, entry.getValue());
					break;
				case "IMAGE_BPMS_PREVIOUS":
					props.setProperty(IMAGE_BPMS + IMAGE_PREVIOUS_SUFFIX, entry.getValue());
					break;
				case "IMAGE_BPMS_LDAP_TEST":
					props.setProperty(IMAGE_BPMS_LDAP_TEST_IMAGE, entry.getValue());
					break;
				case "IMAGE_SSO":
					props.setProperty(IMAGE_SSO, entry.getValue());
					break;
				case "IMAGE_RAD":
					props.setProperty(IMAGE_RAD, entry.getValue());
					break;
				case "IMAGE_MSA":
					props.setProperty(IMAGE_MSA, entry.getValue());
					break;
				case "IMAGE_OSHINKO_REST":
					props.setProperty(IMAGE_OSHINKO_REST, entry.getValue());
					break;
				case "IMAGE_OSHINKO_WEBUI":
					props.setProperty(IMAGE_OSHINKO_WEBUI, entry.getValue());
					break;
				case "IMAGE_OSHINKO_CLI":
					props.setProperty(IMAGE_OSHINKO_CLI, entry.getValue());
					break;
				case "IMAGE_PY_SPARK":
					props.setProperty(IMAGE_PY_SPARK, entry.getValue());
					break;
				case "IMAGE_JAVA_SPARK":
					props.setProperty(IMAGE_JAVA_SPARK, entry.getValue());
					break;
				case "IMAGE_SCALA_SPARK":
					props.setProperty(IMAGE_SCALA_SPARK, entry.getValue());
					break;
				case "IMAGE_SQUID":
					props.setProperty(IMAGE_SQUID, entry.getValue());
					break;
				case "IMAGE_TCP_PROXY":
					props.setProperty(IMAGE_TCP_PROXY, entry.getValue());
					break;
				case "IMAGE_PHANTOMJS":
					props.setProperty(IMAGE_PHANTOMJS, entry.getValue());
					break;
				case "IMAGE_MM_SERVICE":
					props.setProperty(IMAGE_MM_SERVICE, entry.getValue());
					break;
				case "IMAGE_MM_DATASTORE":
					props.setProperty(IMAGE_MM_DATASTORE, entry.getValue());
					break;
				case "JACOCO_ENABLED":
					props.setProperty(JACOCO_ENABLED, entry.getValue());
					break;
				case "JACOCO_PATH":
					props.setProperty(JACOCO_PATH, entry.getValue());
					break;
				case "NFS_SERVER":
					props.setProperty(NFS_SERVER, entry.getValue());
					break;
				case "NFS_SSH_USERNAME":
					props.setProperty(NFS_SSH_USERNAME, entry.getValue());
					break;
				case "PROXY_HOST":
					props.setProperty(PROXY_HOST, entry.getValue());
					break;
				case "PROXY_HOST_USERNAME":
					props.setProperty(PROXY_HOST_USERNAME, entry.getValue());
					break;
				case "PROXY_HOSTS":
					props.setProperty(PROXY_HOSTS, entry.getValue());
					break;
				case "PROXY_SQUID_PORT":
					props.setProperty(PROXY_SQUID_PORT, entry.getValue());
					break;
				case "PROXY_VERTX_PORT":
					props.setProperty(PROXY_VERTX_PORT, entry.getValue());
					break;
				case "SOAK_TEST_ITERATIONS":
					props.setProperty(SOAK_TEST_ITERATIONS, entry.getValue());
					break;
				case "IMAGE_REPO_BASE":
					props.setProperty(IMAGE_REPO_BASE, entry.getValue());
					break;
				case "MAVEN_BASE_URL":
					props.setProperty(MAVEN_BASE_URL, entry.getValue());
					break;
				case "MAVEN_PROXY_URL":
					props.setProperty(MAVEN_PROXY_URL, entry.getValue());
					break;
				case "MAVEN_PROXY_GROUP":
					props.setProperty(MAVEN_PROXY_GROUP, entry.getValue());
					break;
				case "MAVEN_PROXY_ENABLED":
					props.setProperty(MAVEN_PROXY_ENABLED, entry.getValue());
					break;
				case "MAVEN_DEPLOY_SNAPSHOT_URL":
					props.setProperty(MAVEN_DEPLOY_SNAPSHOT_URL, entry.getValue());
					break;
				case "MAVEN_DEPLOY_RELEASE_URL":
					props.setProperty(MAVEN_DEPLOY_RELEASE_URL, entry.getValue());
					break;
				case "GITLAB_URL":
					props.setProperty(GITLAB_URL, entry.getValue());
					break;
				case "GITLAB_TOKEN":
					props.setProperty(GITLAB_TOKEN, entry.getValue());
					break;
				case "GITLAB_USERNAME":
					props.setProperty(GITLAB_USERNAME, entry.getValue());
					break;
				case "GITLAB_PASSWORD":
					props.setProperty(GITLAB_PASSWORD, entry.getValue());
					break;
				case "GITLAB_GROUP":
					props.setProperty(GITLAB_GROUP, entry.getValue());
					break;
				case "PING_PROTOCOL":
					props.setProperty(PING_PROTOCOL, entry.getValue());
					break;
				case "FABRIC8_VERSION":
					props.setProperty(FABRIC8_VERSION, entry.getValue());
					break;
				case "FABRIC8_SERVICE_NAME_LIMIT":
					props.setProperty(FABRIC8_SERVICE_NAME_LIMIT, entry.getValue());
					break;
				case "FUSE_CACHED_IMAGES":
					props.setProperty(FUSE_CACHED_IMAGES, entry.getValue());
					break;
				case "FUSE_DISABLE_JOLOKIA":
					props.setProperty(FUSE_DISABLE_JOLOKIA, entry.getValue());
					break;
				case "OPENSTACK_URL":
					props.setProperty(OPENSTACK_URL, entry.getValue());
					break;
				case "OPENSTACK_TENANT":
					props.setProperty(OPENSTACK_TENANT, entry.getValue());
					break;
				case "OPENSTACK_USERNAME":
					props.setProperty(OPENSTACK_USERNAME, entry.getValue());
					break;
				case "OPENSTACK_PASSWORD":
					props.setProperty(OPENSTACK_PASSWORD, entry.getValue());
					break;
				case "OPENSTACK_OPEN_SECURITY_GROUP":
					props.setProperty(OPENSTACK_OPEN_SECURITY_GROUP, entry.getValue());
					break;
				case "VERSION_EAP":
					props.setProperty(VERSION_EAP, entry.getValue());
					break;
				case "VERSION_JDV":
					props.setProperty(VERSION_JDV, entry.getValue());
					break;
				case "VERSION_JDG":
					props.setProperty(VERSION_JDG, entry.getValue());
					break;
				case "VERSION_EWS":
					props.setProperty(VERSION_EWS, entry.getValue());
					break;
				case "VERSION_FUSE":
					props.setProperty(VERSION_FUSE, entry.getValue());
					break;
				case "VERSION_KIE":
					props.setProperty(VERSION_KIE, entry.getValue());
					break;
				case "VERSION_JDK":
					props.setProperty(VERSION_JDK, entry.getValue());
					break;
				case "VERSION_SSO":
					props.setProperty(VERSION_SSO, entry.getValue());
					break;
				case "VERSION_AMQ":
					props.setProperty(VERSION_AMQ, entry.getValue());
					break;
				case "BUILD_NAMESPACE":
					props.setProperty(BUILD_NAMESPACE, entry.getValue());
					break;
				case "FORCE_REBUILD":
					props.setProperty(FORCE_REBUILD, entry.getValue());
					break;
				case "BINARY_BUILD":
					props.setProperty(BINARY_BUILD, entry.getValue());
					break;
				case "MAX_HTTP_TRIES":
					props.setProperty(MAX_HTTP_TRIES, entry.getValue());
					break;
				case "DEFAULT_WAIT_TIMEOUT":
					props.setProperty(DEFAULT_WAIT_TIMEOUT, entry.getValue());
					break;
				case "EXTERNAL_SERVICES_HOST":
					props.setProperty(EXTERNAL_SERVICES_HOST, entry.getValue());
					break;
				case "OPENSHIFT_ONLINE":
					props.setProperty(OPENSHIFT_ONLINE, entry.getValue());
					break;
				case "OPENSHIFT_VERSION":
					props.setProperty(OPENSHIFT_VERSION, entry.getValue());
					break;
				case "TEST_ALERT":
					props.setProperty(TEST_ALERT, entry.getValue());
					break;
				case "TEST_JENKINS_RERUN":
					props.setProperty(TEST_JENKINS_RERUN, entry.getValue());
					break;
				case "TEMPLATE_REPO":
					props.setProperty(TEMPLATE_REPO, entry.getValue());
					break;
				case "TEMPLATE_BRANCH":
					props.setProperty(TEMPLATE_BRANCH, entry.getValue());
					break;
				case "CI_USERNAME":
					props.setProperty(CI_USERNAME, entry.getValue());
					break;
				case "CI_PASSWORD":
					props.setProperty(CI_PASSWORD, entry.getValue());
					break;
			}
		}

		return props;
	}

	private Properties defaultValues() {
		final Properties props = new Properties();

		props.setProperty(ROUTE_DOMAIN, "cloudapps.example.com");
		props.setProperty(PROXY_DOMAIN, "proxy.xtf");
		props.setProperty(CLEAN_NAMESPACE, "false");
		props.setProperty(JACOCO_ENABLED, "false");
		props.setProperty(MAVEN_PROXY_GROUP, "public");
		props.setProperty(PROXY_SQUID_PORT, "3128");
		props.setProperty(PROXY_VERTX_PORT, "8080");
		props.setProperty(SOAK_TEST_ITERATIONS, "42");
		props.setProperty(MAVEN_PROXY_ENABLED, "false");
		props.setProperty(GITLAB_GROUP, "ose3");
		props.setProperty(PING_PROTOCOL, "kubeping");
		props.setProperty(FABRIC8_VERSION, "2.2.0.redhat-079");
		props.setProperty(FABRIC8_SERVICE_NAME_LIMIT, "2147483647");
		props.setProperty(FUSE_CACHED_IMAGES, "true");
		props.setProperty(FUSE_DISABLE_JOLOKIA, "false");
		props.setProperty(VERSION_FUSE, "6.2.1");
		props.setProperty(BUILD_NAMESPACE, "xtf-builds");
		props.setProperty(FORCE_REBUILD, "false");
		props.setProperty(BINARY_BUILD, "false");
		props.setProperty(MAX_HTTP_TRIES, "90");
		props.setProperty(DEFAULT_WAIT_TIMEOUT, "180000"); // 3 minutes
		props.setProperty(EXTERNAL_SERVICES_HOST, "external.xtf");
		props.setProperty(OPENSHIFT_ONLINE, "false");
		props.setProperty(TEST_ALERT, "false");
		props.setProperty(OC_BINARY_LOCATION, "/usr/bin/oc");
		props.setProperty(TEMPLATE_REPO, "git://github.com/jboss-openshift/application-templates.git");
		props.setProperty(TEMPLATE_BRANCH, "master");
		return props;
	}

	private void copyValues(final Properties source) {
		copyValues(source, false);
	}

	private void copyValues(final Properties source, final boolean overwrite) {
		source.stringPropertyNames().stream()
				.filter(key -> overwrite || !this.properties.containsKey(key))
				.forEach(key -> this.properties.setProperty(key, source.getProperty(key)));
	}

	public void storeConfiguration() {
		for (final String key : this.properties.stringPropertyNames()) {
			UsageRecorder.storeProperty(key, this.properties.getProperty(key));
		}
		UsageRecorder.flush();
	}

	public void storeConfigurationInFile(final Writer writer) throws IOException {
		this.properties.store(writer, "Runtime test configuration");
	}

	public enum PingProtocol {
		DNSPING,
		KUBEPING
	}
}
