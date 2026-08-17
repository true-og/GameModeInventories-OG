package me.eccentric_nz.gamemodeinventories.JSON;

// Thrown by the JSON.org classes when things are amiss.
public class JSONException extends RuntimeException {

    private static final long serialVersionUID = 0;
    private Throwable cause;

    public JSONException(String message) {

        super(message);

    }

    public JSONException(Throwable cause) {

        super(cause.getMessage());
        this.cause = cause;

    }

    // Returns the cause of this exception, or null if the cause is nonexistent or
    // unknown.
    @Override
    public Throwable getCause() {

        return this.cause;

    }

}
