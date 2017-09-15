package cz.xtf.jolokia;

import io.fabric8.kubernetes.api.model.Pod;

/**
 * "%s/api/v1/namespaces/%s/pods/https:%s:8778/proxy/
 * jolokia/exec/org.apache.activemq:type=Broker,brokerName=%3$s,destinationType=Queue,destinationName=%s/browse%28%29",
 *
 * @author David Simansky | dsimansk@redhat.com
 */
public class JolokiaRequestBuilder {

	private String masterUrl;
	private String namespace;
	private Pod pod;
	private String beanName;
	private AmqJolokiaRequest amqReq;

	public static JolokiaRequestBuilder newRequest() {
		return new JolokiaRequestBuilder();
	}

	public JolokiaRequestBuilder withMasterUrl(String masterUrl) {
		this.masterUrl = masterUrl;
		return this;
	}

	public JolokiaRequestBuilder withNamespace(String namespace) {
		this.namespace = namespace;
		return this;
	}

	public JolokiaRequestBuilder withPod(Pod pod) {
		this.pod = pod;
		return this;
	}

	public AmqJolokiaRequest withAmq() {
		this.beanName = "org.apache.activemq:type=Broker";
		this.amqReq = new AmqJolokiaRequest(this);
		return this.amqReq;
	}

	public JolokiaRequestBuilder endRequest() {
		return this;
	}

	public String build() {
		StringBuilder sb = new StringBuilder(masterUrl)
				.append("/api/v1/namespaces/").append(namespace).append("/pods")
				.append("/https:").append(pod.getMetadata().getName()).append(":8778/proxy")
				.append("/jolokia")
				.append(this.amqReq.execMethod != null ? "/exec/" : "/read/")
				.append(beanName);
		if (amqReq != null) {
			sb.append(",brokerName=").append(pod.getMetadata().getName())
					.append(",destinationType=").append(this.amqReq.destinationType)
					.append(",destinationName=").append(this.amqReq.destinationName)
					.append("/");
					if (this.amqReq.execMethod != null) {
						sb.append(this.amqReq.execMethod).append("%28%29");
					} else {
						sb.append(this.amqReq.attribute);
					}
		}
		return sb.toString();
	}

	public class AmqJolokiaRequest {
		String destinationName;
		String destinationType;
		String execMethod;
		String attribute;

		JolokiaRequestBuilder parent;

		AmqJolokiaRequest(JolokiaRequestBuilder parent) {
			this.parent = parent;
		}

		public AmqJolokiaRequest withQueue(String name) {
			destinationType = "Queue";
			destinationName = name;
			return this;
		}

		public AmqJolokiaRequest withExecMethod(String execMethod) {
			this.execMethod = execMethod;
			return this;
		}

		public AmqJolokiaRequest withAttribute(String attribute){
			this.attribute = attribute;
			return this;
		}

		public JolokiaRequestBuilder endAmq() {
			return parent;
		}
	}
}
