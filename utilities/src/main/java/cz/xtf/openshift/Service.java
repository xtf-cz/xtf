package cz.xtf.openshift;

/**
 * @author Radek Koubsky
 */
public interface Service extends AutoCloseable {
	public void start() throws Exception;

	public boolean isStarted() throws Exception;

	public String getHostName();

}
