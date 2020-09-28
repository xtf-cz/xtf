package cz.xtf.core.waiting;

public class WaiterException extends RuntimeException {

    public WaiterException() {
        super();
    }

    public WaiterException(String message) {
        super(message);
    }
}
