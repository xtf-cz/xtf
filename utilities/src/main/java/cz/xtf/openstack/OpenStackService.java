package cz.xtf.openstack;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Server;
import org.openstack4j.openstack.OSFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.docker.OpenShiftNode;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.tuple.Tuple;

public class OpenStackService {
	private final OSClient openStack;

	public OpenStackService() {
		openStack = OSFactory
				.builderV2()
				.endpoint(TestConfiguration.openStackURL())
				.credentials(TestConfiguration.openStackUsername(),
						TestConfiguration.openStackPassword())
				.tenantName(TestConfiguration.openStackTenant()).authenticate();
	}

	public List<? extends Server> getOpenStackNodes() {
		return openStack.compute().servers().list();
	}

	public List<OpenShiftNodeOnOpenStack> getOpenShiftNodes(
			List<OpenShiftNode> openShiftNodes) {
		final List<? extends Server> osNodes = getOpenStackNodes();
		final Map<String, String> hostToIP = openShiftNodes
				.stream()
				.map(openShiftNode -> openShiftNode.getHostname())
				.map(x -> {
					try {
						return Arrays.asList(InetAddress.getAllByName(x))
								.stream();
					} catch (Exception e) {
						return (Stream<InetAddress>) null;
					}
				})
				.flatMap(x -> x)
				.collect(
						Collectors.toMap(x -> x.getHostName(),
								x -> x.getHostAddress()));
		final Map<String, Server> ipToOpenStackNode = osNodes
				.stream()
				.<Tuple.Pair<String, Server>> flatMap(
						node -> node
								.getAddresses()
								.getAddresses()
								.values()
								.stream()
								.flatMap(addresses -> addresses.stream())
								.map(x -> Tuple.pair(x.getAddr(), (Server) node)))
				.collect(
						Collectors.toMap(x -> x.getFirst(), x -> x.getSecond()));
		return openShiftNodes
				.stream()
				.filter(x -> hostToIP.containsKey(x.getHostname()))
				.filter(x -> ipToOpenStackNode.containsKey(hostToIP.get(x
						.getHostname())))
				.map(x -> new OpenShiftNodeOnOpenStack(x,
						ipToOpenStackNode.get(hostToIP.get(x.getHostname())), openStack))
				.collect(Collectors.toList());
	}

	public List<OpenShiftNodeOnOpenStack> getOpenShiftNodes() {
		return getOpenShiftNodes(OpenshiftUtil.getInstance().getNodes());
	}
	
	@SafeVarargs
	public final List<OpenShiftNodeOnOpenStack> getOpenShiftNodes(Tuple.Pair<String, String>... labels) {
		return getOpenShiftNodes(OpenshiftUtil.getInstance().getNodes(labels));
	}
	
	public void connectAllNodesToNetwork() {
		getOpenShiftNodes().forEach(x -> x.networkUp());
	}
}
