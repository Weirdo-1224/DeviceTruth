package io.devicetruth.application;

/** 应用层稳定异常：只携带固定原因，不携带用户原文、模型原文或绝对路径。 */
public final class ApplicationExceptions {
    private ApplicationExceptions() {}

    public static final class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException() { super("Session not found"); }
    }

    public static final class SessionCapacityExceededException extends RuntimeException {
        public SessionCapacityExceededException() { super("Session capacity exceeded"); }
    }

    public static final class MessageCapacityExceededException extends RuntimeException {
        public MessageCapacityExceededException() { super("Message capacity exceeded"); }
    }

    public static final class NotImplementedException extends RuntimeException {
        private final Route route;
        public NotImplementedException(Route route) {
            super("Route not implemented in this milestone: " + route);
            this.route = route;
        }
        public Route route() { return route; }
    }

    public static final class RouterUnavailableException extends RuntimeException {
        public RouterUnavailableException() { super("ROUTER_UNAVAILABLE"); }
    }
}