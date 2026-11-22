package io.devicetruth.knowledge;

/** Stable redacted codes only: supplier text, document contents and secrets never enter messages. */
public final class RerankerException extends RuntimeException {
    public RerankerException(String code){super(code);}
    public String code(){return getMessage();}
}
