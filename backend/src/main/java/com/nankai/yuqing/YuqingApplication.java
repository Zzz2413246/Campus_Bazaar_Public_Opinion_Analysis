package com.nankai.yuqing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 舆情分析系统启动类
 */
@SpringBootApplication
@EnableScheduling
public class YuqingApplication {
    public static void main(String[] args) {
        SpringApplication.run(YuqingApplication.class, args);
    }
}
