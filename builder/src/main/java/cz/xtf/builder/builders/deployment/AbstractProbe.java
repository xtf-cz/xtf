package cz.xtf.builder.builders.deployment;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.kubernetes.api.model.HTTPHeader;
import io.fabric8.kubernetes.api.model.HTTPHeaderBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;

public abstract class AbstractProbe {

    // valid probe types: { httpGet, tcpSocket, exec }
    protected String probeType;
    protected String path;
    protected String port;
    protected String host;
    protected Map<String, String> httpHeaders;
    protected String[] commandArgs;

    public Probe build() {
        ProbeBuilder builder = new ProbeBuilder();
        if (probeType.equals("httpGet")) {
            final List<HTTPHeader> headers = httpHeaders.entrySet().stream().map(
                    x -> (new HTTPHeaderBuilder()).withName(x.getKey()).withValue(x.getValue()).build())
                    .collect(Collectors.toList());
            builder.withNewHttpGet()
                    .withPath(path)
                    .withPort(getPort())
                    .withHost(host)
                    .withHttpHeaders(headers)
                    .endHttpGet();
        }
        if (probeType.equals("tcpSocket")) {
            builder.withNewTcpSocket()
                    .withPort(getPort())
                    .endTcpSocket();
        }
        if (probeType.equals("exec")) {
            builder.withNewExec()
                    .withCommand(commandArgs)
                    .endExec();
        }

        build(builder);

        return builder.build();
    }

    protected abstract void build(ProbeBuilder builder);

    //sets port as integer without quotes if it's numeric
    private IntOrString getPort() {
        IntOrStringBuilder builder = new IntOrStringBuilder();
        if (StringUtils.isNumeric(port)) {
            builder.withIntVal(Integer.valueOf(port));
        } else {
            builder.withStrVal(port);
        }
        return builder.build();
    }

    public AbstractProbe createHttpProbe(final String host, final String path, final String port,
            final Map<String, String> httpHeaders) {
        this.probeType = "httpGet";
        this.path = path;
        this.port = port;
        this.host = host;
        this.httpHeaders = httpHeaders;
        return this;
    }

    public AbstractProbe createHttpProbe(final String host, final String path, final String port) {
        return createHttpProbe(host, path, port, new HashMap<String, String>());
    }

    public AbstractProbe createHttpProbe(final String path, final String port) {
        return createHttpProbe(null, path, port);
    }

    public AbstractProbe createHttpProbe(final String path, final String port, final String username, final String password) {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
        return createHttpProbe(null, path, port, headers);
    }

    public AbstractProbe createTcpProbe(String port) {
        this.probeType = "tcpSocket";
        this.port = port;
        return this;
    }

    public AbstractProbe createExecProbe(String... commandArgs) {
        this.probeType = "exec";
        this.commandArgs = commandArgs;
        return null;
    }

}
