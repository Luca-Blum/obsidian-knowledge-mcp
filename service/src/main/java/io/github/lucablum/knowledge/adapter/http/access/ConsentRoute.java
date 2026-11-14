package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.access.AuthorizationRequest;
import io.github.lucablum.knowledge.application.access.AuthorizeClients;
import io.github.lucablum.knowledge.domain.access.CodeChallenge;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * {@code /authorize}: a {@code GET} from the Client's redirect shows the consent page, and the page's {@code POST}
 * with the right passphrase sends the User back to the Client with a code. A request that names an unknown Client or
 * a redirect it did not register is never redirected, so the page cannot send codes or errors anywhere else.
 */
@ApplicationScoped
@PrimaryAdapter
public class ConsentRoute {

    private static final Logger LOG = Logger.getLogger(ConsentRoute.class);

    private final AuthorizeClients authorize;
    private final CollectionConfig collection;

    public ConsentRoute(AuthorizeClients authorize, CollectionConfig collection) {
        this.authorize = authorize;
        this.collection = collection;
    }

    void register(@Observes Router router) {
        if (OAuthEndpoints.of(collection).isPresent()) {
            router.get(OAuthEndpoints.AUTHORIZE).blockingHandler(this::showConsentPage, false);
            router.post(OAuthEndpoints.AUTHORIZE)
                    .handler(BodyHandler.create().setBodyLimit(OAuthEndpoints.BODY_LIMIT))
                    .blockingHandler(this::consent, false);
        }
    }

    private void showConsentPage(RoutingContext request) {
        MultiMap query = request.queryParams();
        Optional<RegisteredClient> client = clientWithRedirect(query);
        if (client.isEmpty()) {
            ConsentPage.showError(request, 400, "The client is unknown or did not register this redirect URI.");
            return;
        }
        URI redirectUri = URI.create(query.get("redirect_uri"));
        if (!"code".equals(query.get("response_type"))) {
            redirect(request, redirectUri, error("unsupported_response_type", query.get("state")));
        } else if (isBlank(query.get("code_challenge")) || !"S256".equals(query.get("code_challenge_method"))) {
            redirect(request, redirectUri, error("invalid_request", query.get("state")));
        } else {
            ConsentPage.show(request, 200, collection.name(), client.get().name(), formFields(query), null);
        }
    }

    private void consent(RoutingContext request) {
        MultiMap form = request.request().formAttributes();
        Optional<RegisteredClient> client = clientWithRedirect(form);
        if (client.isEmpty()) {
            ConsentPage.showError(request, 400, "The client is unknown or did not register this redirect URI.");
            return;
        }
        URI redirectUri = URI.create(form.get("redirect_uri"));
        if (isBlank(form.get("code_challenge"))) {
            redirect(request, redirectUri, error("invalid_request", form.get("state")));
            return;
        }
        AuthorizationRequest authorization = new AuthorizationRequest(
                client.get().clientId(), redirectUri, new CodeChallenge(form.get("code_challenge")));
        Optional<String> code = authorize.consent(authorization, form.get("passphrase"));
        if (code.isEmpty()) {
            LOG.infof("Wrong passphrase for OAuth client %s", client.get().name());
            ConsentPage.show(request, 401, collection.name(), client.get().name(), formFields(form),
                    "That is not the passphrase.");
            return;
        }
        LOG.infof("The User let OAuth client %s in", client.get().name());
        Map<String, String> answer = new LinkedHashMap<>();
        answer.put("code", code.get());
        Optional.ofNullable(form.get("state")).ifPresent(state -> answer.put("state", state));
        redirect(request, redirectUri, answer);
    }

    /** The Client the request names, if it registered the request's redirect URI. */
    private Optional<RegisteredClient> clientWithRedirect(MultiMap parameters) {
        String clientId = parameters.get("client_id");
        String redirectUri = parameters.get("redirect_uri");
        if (isBlank(clientId) || isBlank(redirectUri)) {
            return Optional.empty();
        }
        try {
            URI redirect = URI.create(redirectUri);
            return authorize.client(clientId).filter(client -> client.allowsRedirectTo(redirect));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** What the consent page posts back, so the {@code POST} sees the request the {@code GET} checked. */
    private static Map<String, String> formFields(MultiMap parameters) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String name : new String[] {"client_id", "redirect_uri", "code_challenge", "state"}) {
            Optional.ofNullable(parameters.get(name)).ifPresent(value -> fields.put(name, value));
        }
        return fields;
    }

    private static Map<String, String> error(String error, String state) {
        Map<String, String> answer = new LinkedHashMap<>();
        answer.put("error", error);
        Optional.ofNullable(state).ifPresent(value -> answer.put("state", value));
        return answer;
    }

    private static void redirect(RoutingContext request, URI redirectUri, Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(parameter -> parameter.getKey() + "=" + URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String separator = redirectUri.getRawQuery() == null ? "?" : "&";
        request.response()
                .setStatusCode(302)
                .putHeader("Location", redirectUri + separator + query)
                .putHeader("Cache-Control", "no-store")
                .end();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
