package io.github.lucablum.knowledge.adapter.http.access;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.access.AuthorizeClients;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * At start-up, with OAuth on: checks the passphrase, so one that is too short stops the instance at once, and locks
 * out every Client that consented under a previous one. Changing the passphrase and restarting is how the User locks
 * out claude.ai.
 */
@ApplicationScoped
@PrimaryAdapter
public class PassphraseCheck {

    private static final Logger LOG = Logger.getLogger(PassphraseCheck.class);

    private final AuthorizeClients authorize;
    private final CollectionConfig collection;

    public PassphraseCheck(AuthorizeClients authorize, CollectionConfig collection) {
        this.authorize = authorize;
        this.collection = collection;
    }

    void check(@Observes StartupEvent event) {
        OAuthEndpoints.of(collection).ifPresentOrElse(endpoints -> {
            authorize.forgetGrantsIfPassphraseChanged();
            LOG.infof("OAuth is on for Collection %s at %s", collection.name(), endpoints.issuer());
        }, () -> LOG.infof("OAuth is off for Collection %s: no COLLECTION_OAUTH_PASSPHRASE", collection.name()));
    }
}
