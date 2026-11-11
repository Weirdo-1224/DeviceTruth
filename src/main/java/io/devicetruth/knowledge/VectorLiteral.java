package io.devicetruth.knowledge;

import java.util.StringJoiner;

/** pgvector 文本字面量；不引入额外 JDBC 扩展，维度由 profile 与数据库列共同约束。 */
final class VectorLiteral {
    private VectorLiteral() {}

    static String of(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float component : vector) joiner.add(Float.toString(component));
        return joiner.toString();
    }
}
