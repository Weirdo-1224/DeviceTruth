package io.devicetruth.knowledge;

/** Shared invariant for provider responses, persistent caches and query vectors. */
public final class EmbeddingVectors {
    private EmbeddingVectors() {}
    public static void validate(float[] vector, int dimension) {
        if (vector == null || vector.length != dimension) throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
        double norm = 0;
        for (float x : vector) {
            if (!Float.isFinite(x)) throw new EmbeddingException("EMBEDDING_RESPONSE_INVALID");
            norm += (double)x*x;
        }
        if (norm == 0) throw new EmbeddingException("EMBEDDING_ZERO_VECTOR");
    }
}
