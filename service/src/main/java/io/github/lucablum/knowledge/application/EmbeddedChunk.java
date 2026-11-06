package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.Chunk;

/** A Chunk together with its vector. */
public record EmbeddedChunk(Chunk chunk, float[] embedding) {}
