package io.devicetruth.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 独立 Web 入口：持续提供 HTTP，不调用 CLI 的单次执行、报告落盘或退出逻辑。 */
@SpringBootApplication
public class DeviceTruthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceTruthServerApplication.class, args);
    }
}