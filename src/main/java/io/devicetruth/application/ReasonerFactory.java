package io.devicetruth.application;

import io.devicetruth.core.reasoning.Reasoner;
import java.util.function.Supplier;

/**
 * 每个诊断运行独立创建推理器：StructuredReasoner 持有可变的 attempts 与延迟初始化的连接，
 * 共享同一个实例会让并发运行的尝试记录互相污染，因此这里只保存创建方式，不保存实例。
 */
public final class ReasonerFactory {
    private final String name;
    private final Supplier<Reasoner> creator;

    private ReasonerFactory(String name, Supplier<Reasoner> creator) {
        this.name = name;
        this.creator = creator;
    }

    public static ReasonerFactory of(String name, Supplier<Reasoner> creator) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("reasoner name is required");
        if (creator == null) throw new IllegalArgumentException("reasoner creator is required");
        return new ReasonerFactory(name, creator);
    }

    /** 每次调用都返回一个新实例。 */
    public Reasoner create() { return creator.get(); }

    public String name() { return name; }
}