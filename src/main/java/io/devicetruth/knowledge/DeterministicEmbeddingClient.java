package io.devicetruth.knowledge;

import java.util.Locale;

/**
 * 确定性字符 n-gram 向量化：只用于离线合同测试和本地无密钥演示。
 * 它没有任何语义模型能力，不能用来宣称检索质量、召回效果或真实 embedding 验收；
 * 真实供应商→PG→召回由 02-03 必跑并单独记录。
 */
public final class DeterministicEmbeddingClient implements EmbeddingClient {
    private final EmbeddingProfile profile;

    public DeterministicEmbeddingClient(EmbeddingProfile profile) { this.profile = profile; }

    @Override public EmbeddingProfile profile() { return profile; }

    @Override public float[] embed(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        float[] vector = new float[profile.dimension()];
        int[] codePoints = value.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];
            if (ignored(codePoint)) continue;
            add(vector, "u:" + Character.toString(codePoint), 0.5f);
            if (i + 1 < codePoints.length && !ignored(codePoints[i + 1]))
                add(vector, "b:" + new String(new int[] {codePoint, codePoints[i + 1]}, 0, 2), 1f);
        }
        for (String token : value.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank()) add(vector, "w:" + token, 1f);
        }
        float norm = 0f;
        for (float component : vector) norm += component * component;
        if (norm == 0f) throw new EmbeddingException("EMBEDDING_INPUT_EMPTY");
        float length = (float) Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) vector[i] /= length;
        return vector;
    }

    private static boolean ignored(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private void add(float[] vector, String token, float weight) {
        vector[Math.floorMod(token.hashCode(), vector.length)] += weight;
    }
}
