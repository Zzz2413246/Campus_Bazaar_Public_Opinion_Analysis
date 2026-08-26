package com.nankai.yuqing.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 从第二位同学末代代码中筛选并纳入部署包的四份正式提示词。 */
@Component
public class Task2PromptCatalog {
    public String screenPost() { return read("task2/prompts/screen_post.txt"); }
    public String screenComments() { return read("task2/prompts/screen_comments.txt"); }
    public String classify() { return read("task2/prompts/classify.txt"); }
    public String assessRisk() { return read("task2/prompts/assess_risk.txt"); }

    private String read(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取任务二提示词：" + path, ex);
        }
    }
}
