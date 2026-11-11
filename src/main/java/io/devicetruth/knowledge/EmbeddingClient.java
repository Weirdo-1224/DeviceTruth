package io.devicetruth.knowledge;

/** 服务端 embedding 适配。只接受固定 profile；返回向量长度必须等于 profile 维度。 */
public interface EmbeddingClient {
    EmbeddingProfile profile();

    /** 单段文本向量化；失败抛 {@link EmbeddingException}，不返回部分结果。 */
    float[] embed(String text);
}
