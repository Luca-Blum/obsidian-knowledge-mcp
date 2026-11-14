package io.github.lucablum.knowledge.application.access;

import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/** Who a Client says it is at the token endpoint, and its secret if it was registered with one. */
@ValueObject
public record ClientCredentials(String clientId, Optional<String> secret) {

    @Override
    public String toString() {
        return "ClientCredentials[client=%s]".formatted(clientId);
    }
}
