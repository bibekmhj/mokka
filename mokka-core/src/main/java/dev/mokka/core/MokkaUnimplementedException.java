package dev.mokka.core;

/**
 * Thrown when your test calls an AWS SDK operation that mokka does not yet fake.
 *
 * <p>Every mokka fake starts with 100% API coverage (every method compiles) but only
 * a subset of operations have real behavior. Unimplemented operations throw this
 * exception with a link to open a PR adding support.
 *
 * <p>To add support for the missing operation, see the roadmap and contributing guide:
 * <a href="https://github.com/bibekmhj/mokka/blob/main/ROADMAP.md">ROADMAP.md</a>.
 */
public class MokkaUnimplementedException extends UnsupportedOperationException {

    private static final long serialVersionUID = 1L;

    private final String service;
    private final String operation;

    public MokkaUnimplementedException(String service, String operation) {
        super(buildMessage(service, operation));
        this.service = service;
        this.operation = operation;
    }

    public String service() {
        return service;
    }

    public String operation() {
        return operation;
    }

    private static String buildMessage(String service, String operation) {
        return String.format(
            "mokka: fake for %s.%s is not implemented yet.%n"
                + "  This method compiles against mokka but has no fake behavior.%n"
                + "  Track or contribute at: https://github.com/bibekmhj/mokka/blob/main/ROADMAP.md#%s%n"
                + "  Or open an issue: https://github.com/bibekmhj/mokka/issues/new?labels=service-request&title=%%5B%s%%5D+%s",
            service, operation, service.toLowerCase(), service, operation);
    }
}
