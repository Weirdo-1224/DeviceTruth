package io.devicetruth.application;

/** 应用层执行状态：与 Core 的 RunStatus 刻意不同名，避免把"线程结束"误当成"诊断成功"。 */
public enum ExecutionStatus { QUEUED, RUNNING, COMPLETED, FAILED }