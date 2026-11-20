package xdm.plugin.api.events;

/**
 * Structured event payload representing host application state changes (startup, shutdown, safe mode).
 */
public class AppStateEvent {

    public enum State {
        STARTUP,
        SHUTDOWN,
        SAFE_MODE,
        STATE_CHANGED
    }

    private final State state;
    private final boolean safeMode;
    private final String details;
    private final long timestamp;

    public AppStateEvent(State state, boolean safeMode) {
        this(state, safeMode, "");
    }

    public AppStateEvent(State state, boolean safeMode, String details) {
        this.state = state != null ? state : State.STATE_CHANGED;
        this.safeMode = safeMode;
        this.details = details != null ? details : "";
        this.timestamp = System.currentTimeMillis();
    }

    public State getState() {
        return state;
    }

    public boolean isSafeMode() {
        return safeMode;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "AppStateEvent{" +
                "state=" + state +
                ", safeMode=" + safeMode +
                ", details='" + details + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
