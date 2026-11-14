package io.github.lucablum.knowledge.application.access;

import io.github.lucablum.knowledge.domain.access.AuthorizationCode;
import io.github.lucablum.knowledge.domain.access.Grant;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.github.lucablum.knowledge.domain.access.SecretHash;
import java.time.Instant;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * What OAuth has to remember across restarts: the registered Clients, the codes waiting to be redeemed, the Grants, and
 * which passphrase they were given under. Secrets are only ever stored as their hashes.
 */
@SecondaryPort
public interface Grants {

    void register(RegisteredClient client);

    Optional<RegisteredClient> client(String clientId);

    void remember(AuthorizationCode code);

    /** Removes the code and returns it, so no code is redeemed twice, not even by two requests at once. */
    Optional<AuthorizationCode> takeCode(SecretHash code);

    void save(Grant grant);

    /** Removes the Grant of this refresh token and returns it, so no refresh token is used twice. */
    Optional<Grant> takeGrant(SecretHash refreshToken);

    /** Whether a Grant has this access token and it has not expired by {@code now}. */
    boolean admits(SecretHash accessToken, Instant now);

    /** The hash of the passphrase the current Grants were given under; empty before the first start-up. */
    Optional<SecretHash> passphrase();

    /** Forgets every code and Grant and remembers the new passphrase; registered Clients stay. */
    void forgetAll(SecretHash newPassphrase);
}
