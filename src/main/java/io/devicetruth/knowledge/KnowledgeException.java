package io.devicetruth.knowledge;

/** 知识域的可公开失败：code 稳定、message 固定，不携带正文、SQL、路径或凭据。 */
public final class KnowledgeException extends RuntimeException {
    private final String code;
    private final int status;

    public KnowledgeException(String code, int status) { this(code, status, code); }

    public KnowledgeException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }

    public static KnowledgeException invalid(String message) {
        return new KnowledgeException("INVALID_ARGUMENTS", 400, message);
    }

    public static KnowledgeException notFoundOrDenied() {
        return new KnowledgeException("NOT_FOUND_OR_DENIED", 404);
    }

    /** 资料本身存在且当前身份可读，但没有在本 run 取回过：引用必须命中已取集合。 */
    public static KnowledgeException invalidCitation() {
        return new KnowledgeException("INVALID_CITATION", 409);
    }

    /** embedding 适配失败转成稳定的知识域错误；不保留供应商响应或网络细节。 */
    public static KnowledgeException fromEmbedding(EmbeddingException failure) {
        return switch (failure.code()) {
            case "EMBEDDING_DIMENSION_MISMATCH" -> new KnowledgeException("EMBEDDING_DIMENSION_MISMATCH", 409);
            case "EMBEDDING_INPUT_EMPTY", "EMBEDDING_RESPONSE_INVALID" ->
                    new KnowledgeException("EMBEDDING_RESPONSE_INVALID", 502);
            case "EMBEDDING_CONFIGURATION_MISSING" -> new KnowledgeException("EMBEDDING_CONFIGURATION_MISSING", 503);
            default -> new KnowledgeException("EMBEDDING_UNAVAILABLE", 503);
        };
    }
}
