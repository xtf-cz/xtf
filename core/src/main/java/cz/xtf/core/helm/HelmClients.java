package cz.xtf.core.helm;

public class HelmClients {

    public static HelmBinary adminBinary() {
        return HelmBinaryManagerFactory.INSTANCE.getHelmBinaryManager().adminBinary();
    }

    public static HelmBinary masterBinary() {
        return HelmBinaryManagerFactory.INSTANCE.getHelmBinaryManager().masterBinary();
    }
}
