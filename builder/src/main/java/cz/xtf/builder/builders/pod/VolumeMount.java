package cz.xtf.builder.builders.pod;

public class VolumeMount {
    private String mountPath;
    private String name;
    private boolean readOnly;
    private String subPath;

    public VolumeMount(String name, String mountPath, boolean readOnly) {
        this.mountPath = mountPath;
        this.name = name;
        this.readOnly = readOnly;
    }

    public VolumeMount(String name, String mountPath, boolean readOnly, String subPath) {
        this.mountPath = mountPath;
        this.name = name;
        this.readOnly = readOnly;
        this.subPath = subPath;
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getName() {
        return name;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public String getSubPath() {
        return subPath;
    }
}
