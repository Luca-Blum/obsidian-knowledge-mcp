package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.access.AuthorizeClients;
import io.github.lucablum.knowledge.application.access.ClientRegistration;
import io.github.lucablum.knowledge.application.access.Registration;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.util.List;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * {@code POST /register}: dynamic client registration (RFC 7591), which claude.ai's "Register automatically" uses.
 * Anyone may register; only the passphrase on the consent page lets a Client in.
 */
@ApplicationScoped
@PrimaryAdapter
public class ClientRegistrationRoute {

    private static final Logger LOG = Logger.getLogger(ClientRegistrationRoute.class);

    /** A public Client authenticates with PKCE alone; the others with the secret they get here. */
    static final List<String> AUTH_METHODS = List.of("none", "client_secret_post", "client_secret_basic");

    /** RFC 7591 makes a Client that names no method one with a secret, sent with HTTP Basic. */
    private static final String DEFAULT_AUTH_METHOD = "client_secret_basic";

    private static final int MAXIMUM_NAME_LENGTH = 100;

    private static final int BODY_LIMIT = 16 * 1024;

    private final AuthorizeClients authorize;
    private final CollectionConfig collection;

    public ClientRegistrationRoute(AuthorizeClients authorize, CollectionConfig collection) {
        this.authorize = authorize;
        this.collection = collection;
    }

    void register(@Observes Router router) {
        if (OAuthEndpoints.of(collection).isPresent()) {
            router.post(OAuthEndpoints.REGISTER)
                    .handler(BodyHandler.create().setBodyLimit(BODY_LIMIT))
                    .blockingHandler(this::registerClient, false);
        }
    }

    private void registerClient(RoutingContext request) {
        JsonObject metadata;
        try {
            metadata = request.body().asJsonObject();
        } catch (DecodeException e) {
            metadata = null;
        }
        if (metadata == null) {
            Responses.error(request, 400, "invalid_client_metadata", "The body must be a JSON object");
            return;
        }
        String authMethod = metadata.getValue("token_endpoint_auth_method", DEFAULT_AUTH_METHOD).toString();
        if (!AUTH_METHODS.contains(authMethod)) {
            Responses.error(request, 400, "invalid_client_metadata", "Supported auth methods: " + AUTH_METHODS);
            return;
        }
        Registration registration;
        try {
            registration = authorize.register(
                    new ClientRegistration(name(metadata), redirectUris(metadata), !"none".equals(authMethod)));
        } catch (IllegalArgumentException e) {
            Responses.error(request, 400, "invalid_redirect_uri", e.getMessage());
            return;
        }
        LOG.infof("Registered OAuth client %s (%s)", registration.client().name(), registration.client().clientId());
        Responses.json(request, 201, registered(registration, authMethod));
    }

    private static String name(JsonObject metadata) {
        String name = metadata.getValue("client_name", "Unnamed client").toString().strip();
        return name.length() > MAXIMUM_NAME_LENGTH ? name.substring(0, MAXIMUM_NAME_LENGTH) : name;
    }

    /** The redirect URIs as the Client sent them; anything that is not a list of URIs is rejected as one. */
    private static List<URI> redirectUris(JsonObject metadata) {
        if (!(metadata.getValue("redirect_uris") instanceof JsonArray uris) || uris.isEmpty()) {
            throw new IllegalArgumentException("redirect_uris must list at least one URI");
        }
        return uris.stream().map(uri -> URI.create(String.valueOf(uri))).toList();
    }

    private static JsonObject registered(Registration registration, String authMethod) {
        RegisteredClient client = registration.client();
        JsonObject answer = new JsonObject()
                .put("client_id", client.clientId())
                .put("client_id_issued_at", System.currentTimeMillis() / 1000)
                .put("client_name", client.name())
                .put("redirect_uris", new JsonArray(client.redirectUris().stream().map(URI::toString).toList()))
                .put("grant_types", new JsonArray().add("authorization_code").add("refresh_token"))
                .put("response_types", new JsonArray().add("code"))
                .put("token_endpoint_auth_method", authMethod);
        registration.secret().ifPresent(secret -> answer.put("client_secret", secret).put("client_secret_expires_at", 0));
        return answer;
    }
}
