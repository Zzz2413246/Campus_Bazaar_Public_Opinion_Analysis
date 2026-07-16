package com.nankai.yuqing.controller;

import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisTaskExtension;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 分析任务扩展入口；任务二标准到位后保持 URL 和请求结构不变。 */
@RestController
@RequestMapping("/api/analysis/extensions")
public class AnalysisExtensionController {

    private final PostRepository postRepository;
    private final Map<String, AnalysisTaskExtension> extensions;

    public AnalysisExtensionController(PostRepository postRepository,
                                       List<AnalysisTaskExtension> extensions) {
        this.postRepository = postRepository;
        this.extensions = extensions.stream().collect(Collectors.toMap(
            AnalysisTaskExtension::code, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return extensions.values().stream().map(AnalysisTaskExtension::status).toList();
    }

    @GetMapping("/{code}")
    public Map<String, Object> status(@PathVariable String code) {
        AnalysisTaskExtension extension = extensions.get(code);
        return extension == null ? Map.of("error", "未知分析任务: " + code) : extension.status();
    }

    @PostMapping("/{code}/run")
    public Map<String, Object> run(@PathVariable String code,
                                   @RequestBody(required = false) Map<String, Object> request) {
        AnalysisTaskExtension extension = extensions.get(code);
        if (extension == null) return Map.of("error", "未知分析任务: " + code);
        return extension.execute(postRepository.findAll(), request == null ? Map.of() : request);
    }
}
