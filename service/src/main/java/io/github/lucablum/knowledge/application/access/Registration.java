package io.github.lucablum.knowledge.application.access;

import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/** A registered Client, with its secret in plain text if it asked for one: the only time the secret is seen. */
@ValueObject
public record Registration(RegisteredClient client, Optional<String> secret) {

    @Override
    public String toString() {
        return "Registration[client=%s]".formatted(client.clientId());
    }
}
