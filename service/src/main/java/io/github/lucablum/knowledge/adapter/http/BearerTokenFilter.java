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

/**
 * Lets a request through only with the bearer token of a configured Client, so the MCP endpoint and {@code /admin}
 * stay closed wherever they are mounted. Health stays open, as Docker checks it without a token and it shows only
 * whether the instance is up. It calls no port, so it is a package-private helper rather than an adapter.
 */
@ApplicationScoped
class BearerTokenFilter {

    private static final Logger LOG = Logger.getLogger(BearerTokenFilter.class);

    /** Runs before every route, the MCP server's included. */
    private static final int PRIORITY = 100;

    private static final String HEALTH = "/q/health";

    private static final String BEARER = "Bearer ";

    private final Map<String, String> tokens;

    BearerTokenFilter(CollectionConfig collection) {
        this.tokens = configuredTokens(collection.auth().tokens());
        if (tokens.isEmpty()) {
            throw new IllegalStateException("No client token is configured for Collection %s, so no Client could "
                    + "reach it: set COLLECTION_AUTH_TOKENS_<CLIENT>".formatted(collection.name()));
        }
    }

    /** Leaves out every Client whose token was removed. */
    private static Map<String, String> configuredTokens(Map<String, Optional<String>> tokens) {
        return tokens.entrySet().stream()
                .filter(client -> client.getValue().isPresent())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, client -> client.getValue().get()));
    }

    void register(@Observes Filters filters) {
        filters.register(this::filter, PRIORITY);
    }

    private void filter(RoutingContext request) {
        if (request.normalizedPath().startsWith(HEALTH)) {
            request.next();
            return;
        }
        Optional<String> client = clientOf(request.request().getHeader(HttpHeaders.AUTHORIZATION));
        if (client.isEmpty()) {
            rejectAsUnauthorized(request);
            return;
        }
        LOG.debugf("Client %s: %s %s", client.get(), request.request().method(), request.normalizedPath());
        request.next();
    }

    private static void rejectAsUnauthorized(RoutingContext request) {
        LOG.infof("Rejected %s %s without a known client token", request.request().method(), request.normalizedPath());
        request.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Bearer")
                .end();
    }

    /** The name of the Client whose token the header carries; the scheme {@code Bearer} may be in any case. */
    private Optional<String> clientOf(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
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
