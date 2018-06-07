package cz.xtf.openstack;

import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Action;
import org.openstack4j.model.compute.RebootType;
import org.openstack4j.model.compute.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.docker.OpenShiftNode;

public class OpenShiftNodeOnOpenStack extends OpenShiftNode {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenShiftNode.class);
	private final Server openStackServer;
	private final OSClient openStackClient;
	
	public OpenShiftNodeOnOpenStack(final String hostname, final Server openStackServer, final OSClient openStackClient) {
		super(hostname);
		this.openStackServer = openStackServer;
		this.openStackClient = openStackClient;
	}
	
	public OpenShiftNodeOnOpenStack(final OpenShiftNode node, final Server openStackServer, final OSClient openStackClient) {
		super(node.getHostname(), node.getStatus(), node.getLabels());
		this.openStackServer = openStackServer;
		this.openStackClient = openStackClient;
	}
	
	public void hardRestart() {
		LOGGER.info("Hard-restart of node {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().reboot(openStackServer.getId(), RebootType.HARD);
	}
	
	public void powerOff() {
		LOGGER.info("Powering off node {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().action(openStackServer.getId(), Action.STOP);
	}
	
	public void boot() {
		LOGGER.info("Booting up node {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().action(openStackServer.getId(), Action.START);
	}
	
	public void pause() {
		LOGGER.info("Pausing node {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().action(openStackServer.getId(), Action.PAUSE);
	}
	
	public void resume() {
		LOGGER.info("Resuming node {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().action(openStackServer.getId(), Action.RESUME);
	}
	
	public void networkDown() {
		LOGGER.info("Disabling network for {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().removeSecurityGroup(openStackServer.getId(), TestConfiguration.openStackOpenSecurityGroup());
	}
	
	public void networkUp() {
		LOGGER.info("Enabling network for {}, {}", getHostname(), openStackServer.getName());
		openStackClient.compute().servers().addSecurityGroup(openStackServer.getId(), TestConfiguration.openStackOpenSecurityGroup());
	}
	public String toString() {
		return super.toString() + ", on OpenStack node " + openStackServer.getName();
	}
}
