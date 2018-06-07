package cz.xtf.ssh;

public class SshPrivateKeyNotFoundException extends RuntimeException {

	private static final long serialVersionUID = -1110984173100523047L;

	public SshPrivateKeyNotFoundException() {
	}

	public SshPrivateKeyNotFoundException(String message) {
		super(message);
	}
}
