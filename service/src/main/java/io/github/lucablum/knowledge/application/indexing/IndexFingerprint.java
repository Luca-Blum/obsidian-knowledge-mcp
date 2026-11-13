package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Everything that decides what the index holds besides the Vault: the embedding model, the dimension of its vectors
 * and the chunking rules. An index built with another fingerprint cannot be updated Note by Note, only re-indexed.
 */
@ValueObject
public record IndexFingerprint(String embeddingModel, int dimension, ChunkingRules chunking) {}
