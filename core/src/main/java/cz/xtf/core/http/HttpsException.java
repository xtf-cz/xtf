package cz.xtf.core.http;

public class HttpsException extends RuntimeException {

    public HttpsException() {
        super();
    }

    public HttpsException(String message) {
        super(message);
    }

    public HttpsException(Throwable e) {
        super(e);
    }

    public HttpsException(String message, Throwable e) {
        super(message, e);
    }
}
