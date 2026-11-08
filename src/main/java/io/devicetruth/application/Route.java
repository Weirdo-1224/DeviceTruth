package io.devicetruth.application;

/** S1 允许的四类路由。模型只能建议其中一种，最终由程序复核。 */
public enum Route { QUERY, DIAGNOSE, CLARIFY, UNSUPPORTED }