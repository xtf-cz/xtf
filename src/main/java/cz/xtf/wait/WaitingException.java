package cz.xtf.wait;

public class WaitingException extends RuntimeException {

	private static final long serialVersionUID = 8903345011804056298L;

	public WaitingException() {
	}

	public WaitingException(String message) {
		super(message);
	}

	public WaitingException(Throwable cause) {
		super(cause);
	}

	public WaitingException(String message, Throwable cause) {
		super(message, cause);
	}

	public WaitingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
