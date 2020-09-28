package cz.xtf.builder.builders.pod;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class NFSVolume extends Volume {
    private final String server;
    private final String serverPath;

    public NFSVolume(String name, String server, String serverPath) {
        super(name);
        this.server = server;
        this.serverPath = serverPath;
    }

    public String getServer() {
        return server;
    }

    public String getServerPath() {
        return serverPath;
    }

    @Override
    protected void addVolumeParameters(VolumeBuilder builder) {
        builder.withNewNfs()
                .withServer(getServer())
                .withPath(getServerPath())
                .endNfs();
    }
}
