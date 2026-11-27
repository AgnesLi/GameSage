package com.game.sage.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuideDataLoader implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final List<IGuideData> guideDataList;

    @Value("${datasource.update}")
    private boolean isDatasourceUpdate;

    @Override
    public void run(String... args) {

        if (isDatasourceUpdate) {
            List<Document> allChunks = new ArrayList<>();

            if (guideDataList != null) {
                guideDataList
                        .forEach(loader -> {
                            try {
                                log.info("执行加载器: {}", loader.getName());
                                allChunks.addAll(loader.loadGuideData());
                            } catch (Exception e) {
                                log.error("加载器 {} 执行失败", loader.getName(), e);
                            }
                        });

                // 批量写入 VectorStore（内部自动调用 EmbeddingModel 生成向量）
                // 写入失败时不让整个应用启动失败，便于在 embedding 配置/额度异常时仍能启动排查
                try {
                    //检查 ID 有无重复
                    Map<String, Long> idCounts = allChunks.stream()
                            .map(Document::getId)
                            .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

                    List<String> duplicates = idCounts.entrySet().stream()
                            .filter(e -> e.getValue() > 1)
                            .map(Map.Entry::getKey)
                            .toList();

                    if (!duplicates.isEmpty()) {
                        log.warn("检测到 {} 个重复 ID: {}", duplicates.size(), duplicates);
                        // 打印每个重复 ID 对应的内容，定位是哪几个片段撞了
//                        allChunks.stream()
//                                .filter(d -> duplicates.contains(d.getId()))
//                                .forEach(d -> log.warn("重复ID={}, 内容前50字={}", d.getId(),
//                                        d.getText().length() > 50 ? d.getText().substring(0, 50) : d.getText()));
                    } else {
                        log.info("共 {} 个片段，ID 无重复", allChunks.size());
                    }

                    vectorStore.add(allChunks);
                    log.info("加页手记知识库加载完成，共写入 {} 个片段", allChunks.size());
                } catch (Exception e) {
                    log.error("向量库写入失败，本次启动将跳过知识库加载。请检查 embedding 的 base-url / api-key / model 及账户余额。原因: {}",
                            e.getMessage());
                }
            }
        } else {
            log.info("无需更新数据库数据");
        }
    }
}
