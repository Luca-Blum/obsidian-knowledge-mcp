package io.github.lucablum.knowledge.adapter.http;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * Lets a request reach the MCP endpoint or {@code /admin} only with the bearer token of a configured client. Health
 * stays open, as Docker checks it without a token and it shows only whether the instance is up.
 */
@ApplicationScoped
@PrimaryAdapter
public class BearerTokenFilter {

    private static final Logger LOG = Logger.getLogger(BearerTokenFilter.class);

    /** Runs before every route, the MCP server's included. */
    private static final int PRIORITY = 100;

    private static final String BEARER = "Bearer ";

    private final Map<String, String> tokens;

    public BearerTokenFilter(CollectionConfig collection) {
        this.tokens = configuredTokens(collection.auth().tokens());
        if (tokens.isEmpty()) {
            throw new IllegalStateException("No client token is configured for Collection %s, so no client could "
                    + "reach it: set COLLECTION_AUTH_TOKENS_<CLIENT>".formatted(collection.name()));
        }
    }

    /** Leaves out every client whose token was removed. */
    private static Map<String, String> configuredTokens(Map<String, Optional<String>> tokens) {
        return tokens.entrySet().stream()
                .filter(client -> client.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, client -> client.getValue().get()));
    }

    void register(@Observes Filters filters) {
        filters.register(this::filter, PRIORITY);
    }

    private void filter(RoutingContext request) {
        if (!isProtected(request.normalizedPath())) {
            request.next();
            return;
        }
        Optional<String> client = clientOf(request.request().getHeader(HttpHeaders.AUTHORIZATION));
        if (client.isEmpty()) {
            LOG.infof("Rejected %s %s without a known client token", request.request().method(), request.normalizedPath());
            request.response()
                    .setStatusCode(401)
                    .putHeader("WWW-Authenticate", "Bearer")
                    .end();
            return;
        }
        LOG.debugf("Client %s: %s %s", client.get(), request.request().method(), request.normalizedPath());
        request.next();
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/mcp") || path.startsWith("/admin");
    }

    /** The name of the client whose token the header carries. */
    private Optional<String> clientOf(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return Optional.empty();
        }
        byte[] presented = authorization.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8);
        return tokens.entrySet().stream()
                .filter(client -> matches(presented, client.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Compares in constant time, so the response time does not give away how much of a token was right. */
    private static boolean matches(byte[] presented, String token) {
        return MessageDigest.isEqual(presented, token.getBytes(StandardCharsets.UTF_8));
    }
}
