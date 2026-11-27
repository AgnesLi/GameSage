package com.game.sage.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.game.sage.utils.CommonUtils.SEARCH_FAILED_MESSAGE;
import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
@RequiredArgsConstructor
public class IdentityVCrypticNotesTool {

    private final VectorStore vectorStore;

    @Tool(description = """
            在加页手记本地攻略知识库中检索相关片段。
            适用于：职业推荐、怪物机制、辞章价值等已有攻略内容。
            如果返回“本地知识库未找到相关攻略”，说明本地库没有覆盖该问题。
            如果返回“本地知识库检索失败”，说明数据库暂时不可用，直接把该提示告知用户，不要重试。
            """)
    public String searchLocalGuide(
            @ToolParam(description = "搜索查询，应包含具体游戏术语，如'贪婪的盗匪 绿锤 应对'") String query,
            @ToolParam(description = "视角过滤：survivor（求生者）、regulator（监管者），不确定时传 both") String mode) {

        try {
            // 1. 构建带 metadata 过滤的检索请求
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(query)
                    .topK(4)
                    .similarityThreshold(0.65);

            // 2. 视角过滤
            if (!"both".equalsIgnoreCase(mode)) {
                requestBuilder.filterExpression("mode == '" + mode + "' or mode == 'both'");
            }

            List<Document> docs = vectorStore.similaritySearch(requestBuilder.build());

            // 3. 无结果时返回明确信号
            if (docs.isEmpty()) {
                return "本地知识库未找到相关攻略。";
            }

            // 4. 拼接片段
            return SpliceSegments(docs).toString().trim();

        } catch (Exception e) {
            // 数据库/向量库访问失败：直接返回固定文案，不重试
            log.error("{}, query={}, mode={}", SEARCH_FAILED_MESSAGE, query, mode);
            log.error("", e);
            return SEARCH_FAILED_MESSAGE;
        }
    }

    private StringBuilder SpliceSegments(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String heading = (String) doc.getMetadata().getOrDefault("heading", "未知章节");
            String category = (String) doc.getMetadata().getOrDefault("category", "general");
            sb.append("【片段").append(i + 1).append("｜").append(heading)
                    .append("｜").append(category).append("】\n");
            sb.append(doc.getText()).append("\n\n");
        }
        return sb;
    }

}
