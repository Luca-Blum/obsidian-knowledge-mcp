package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import java.util.Optional;
import java.util.Set;

/**
 * Where this instance's OAuth endpoints are. They sit at the origin root, because claude.ai's OAuth client sends
 * {@code /authorize} and {@code /token} there whatever path the issuer has (ADR 0005).
 */
record OAuthEndpoints(String issuer) {

    static final String AUTHORIZATION_SERVER_METADATA = "/.well-known/oauth-authorization-server";

    static final String RESOURCE_METADATA = "/.well-known/oauth-protected-resource";

    /** Where quarkus-mcp-server serves MCP. */
    private static final String MCP = "/mcp";

    /** RFC 9728 lets a Client also ask for the resource metadata with the resource's path appended. */
    static final String RESOURCE_METADATA_FOR_MCP = RESOURCE_METADATA + MCP;

    static final String REGISTER = "/register";

    static final String AUTHORIZE = "/authorize";

    static final String TOKEN = "/token";

    /** The largest form or JSON body an OAuth endpoint accepts; theirs are a few hundred bytes. */
    static final int BODY_LIMIT = 16 * 1024;

    private static final Set<String> PUBLIC_PATHS = Set.of(AUTHORIZATION_SERVER_METADATA, RESOURCE_METADATA,
            RESOURCE_METADATA_FOR_MCP, REGISTER, AUTHORIZE, TOKEN);

    /** OAuth is on only with a passphrase, and then it needs to know the origin the internet reaches it at. */
    static Optional<OAuthEndpoints> of(CollectionConfig collection) {
        if (collection.oauth().passphrase().isEmpty()) {
            return Optional.empty();
        }
        String publicUrl = collection.publicUrl().orElseThrow(() -> new IllegalStateException(
                "OAuth is on for Collection %s but COLLECTION_PUBLIC_URL is not set".formatted(collection.name())));
        return Optional.of(new OAuthEndpoints(publicUrl.replaceAll("/+$", "")));
    }

    boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path);
    }

    String url(String path) {
        return issuer + path;
    }

    String resource() {
        return url(MCP);
    }

    String resourceMetadata() {
        return url(RESOURCE_METADATA);
    }
}
