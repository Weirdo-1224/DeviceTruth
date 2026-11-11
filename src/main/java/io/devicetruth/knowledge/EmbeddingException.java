package io.devicetruth.knowledge;

/** embedding 适配失败：code 是稳定错误码，message 不含密钥、正文或供应商原始响应。 */
public final class EmbeddingException extends RuntimeException {
    private final String code;

    public EmbeddingException(String code) { this(code, code); }

    public EmbeddingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
