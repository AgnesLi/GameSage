package com.game.sage.utils;

import com.game.sage.model.DocumentsMetaData;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.game.sage.utils.CommonUtils.*;
import static reactor.netty.http.HttpConnectionLiveness.log;

public class MarkdownFilesUtils {

    public static String currentDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    public static List<Document> getDocuments(String[] guideFiles,
                                              ResourceLoader resourceLoader,
                                              DocumentsMetaData documentsMetaData) throws IOException {
        List<Document> allChunks = new ArrayList<>();

        for (String filePath : guideFiles) {
            Resource resource = resourceLoader.getResource(filePath);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Map<String, Object> metadata = getMetadata(documentsMetaData, resource);

            List<Document> titleChunks = splitByMarkdownHeadings(content, metadata);

            // 2. 对超长块做 Token 二次切分，并合并过短块
            List<Document> finalChunks = refineChunks(titleChunks);

            allChunks.addAll(finalChunks);
        }

        return allChunks;
    }

    private static @NonNull Map<String, Object> getMetadata(DocumentsMetaData documentsMetaData, Resource resource) {
        String fileName = resource.getFilename();

        // 1. 按 Markdown 标题切分

        // 推断 metadata：根据标题内容判断类别和模式
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", documentsMetaData.getSource());
        metadata.put("file", fileName);
        metadata.put("category", documentsMetaData.getInferCategory());
        metadata.put("mode", documentsMetaData.getInferMode());
        metadata.put("game", documentsMetaData.getInferGame());
        metadata.put("updateTime", currentDate);
        return metadata;
    }

    public static List<Document> splitByMarkdownHeadings(String markdown, Map<String, Object> metadata) {
        List<Document> chunks = new ArrayList<>();
        // 匹配 ## 或 ### 标题行（加页手记攻略建议用 ## 作为主切分点）
        Pattern headingPattern = Pattern.compile("^(#{2,3})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(markdown);

        List<int[]> headingPositions = new ArrayList<>();
        List<String> headingTitles = new ArrayList<>();
        List<Integer> headingLevels = new ArrayList<>();
        while (matcher.find()) {
            headingPositions.add(new int[]{matcher.start(), matcher.end()});
            headingTitles.add(matcher.group(2).trim());
            headingLevels.add(matcher.group(1).length());
        }

        String sourceFile = metadataValue(metadata, "file");
        String currentH2 = null;
        for (int i = 0; i < headingPositions.size(); i++) {
            int contentStart = headingPositions.get(i)[1];
            int contentEnd = (i + 1 < headingPositions.size())
                    ? headingPositions.get(i + 1)[0]
                    : markdown.length();

            // 三级标题拼接所属二级标题，例如 "贪婪的盗匪::技能解析"
            String heading;
            if (headingLevels.get(i) == 2) {
                currentH2 = headingTitles.get(i);
                heading = currentH2;
            } else {
                heading = currentH2 == null
                        ? headingTitles.get(i)
                        : currentH2 + "::" + headingTitles.get(i);
            }

            String sectionContent = markdown.substring(contentStart, contentEnd).trim();
            if (sectionContent.isEmpty()) continue;

            // 每个章节使用独立的 metadata 副本，避免上一章节的推断结果污染后续章节
            Map<String, Object> sectionMetadata = new HashMap<>(metadata);
            sectionMetadata.put("heading", heading);
            sectionMetadata.computeIfAbsent("category", k -> inferCategory(heading));
            sectionMetadata.computeIfAbsent("mode", k -> inferMode(heading));
            sectionMetadata.computeIfAbsent("game", k -> inferGame(heading));

            // 把标题注入正文开头，检索命中时模型能知道这段属于哪个章节
            String textWithContext = "【" + heading + "】\n" + sectionContent;
            // 使用确定性 ID，保证同一份内容每次构建出的 ID 相同，PgVectorStore 的 upsert 才能命中同一条记录
            String docId = deterministicId(sourceFile, heading, 0);
            chunks.add(new Document(docId, textWithContext, sectionMetadata));
        }
        return chunks;
    }

    public static List<Document> refineChunks(List<Document> rawChunks) {
        // Spring AI 的 TokenTextSplitter，中文场景下 chunkSize 设 400 左右
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(400)                 // 目标 token 数
                .withMinChunkSizeChars(100)         // 小于 100 字符的块会被合并
                .withMinChunkLengthToEmbed(10)      // 小于 10 字符不生成向量
                .withKeepSeparator(true)
                .build();

        List<Document> refined = new ArrayList<>();
        for (Document chunk : rawChunks) {
            // 上游切分/转换可能产生 null 文档或空文本，直接跳过，避免 NPE
            if (chunk == null || chunk.getText() == null || chunk.getText().isBlank()) {
                continue;
            }

            String text = chunk.getText();
            // 粗略估算：中文约 1 字符 ≈ 1 token，400 token 对应约 400 汉字
            if (text.length() <= 450) {
                // 短块直接保留（如果太短，可以跳过或合并，这里简单处理）
                if (text.length() >= 30) {
                    refined.add(chunk);
                }
            } else {
                // 超长块用 TokenTextSplitter 二次切分
                List<Document> subChunks = splitter.apply(List.of(chunk));
                List<Document> fixedChunks = reassignIdsAfterSplit(subChunks);
                refined.addAll(fixedChunks);
            }
        }
        return refined;
    }

    public static List<Document> reassignIdsAfterSplit(List<Document> subChunks) {
        List<Document> fixed = new ArrayList<>();
        for (int i = 0; i < subChunks.size(); i++) {
            Document raw = subChunks.get(i);

            // 用 Builder 重建，保留原始文本和 metadata，同时额外追加分片chunkIndex、totalChunks
            Map<String, Object> newMeta = new HashMap<>(raw.getMetadata());  // 保留从父块继承的 metadata
            newMeta.put("chunk_index", i);
            newMeta.put("total_chunks", subChunks.size());

            // 分片 ID 同样由 文件 + 标题 + 分片序号 计算，保证重复加载时稳定
            String docId = deterministicId(
                    metadataValue(newMeta, "file"),
                    metadataValue(newMeta, "heading"),
                    i);

            Document rebuilt = Document.builder()
                    .id(docId)
                    .text(raw.getText())
                    .metadata(newMeta)
                    .build();

            fixed.add(rebuilt);
        }
        return fixed;
    }

    private static String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }

    /**
     * 根据 文件名 + 标题 + 分片序号 计算确定性 UUID。
     * <p>
     * PgVectorStore 默认的 id 列类型是 uuid，并会在写入时执行 {@code UUID.fromString(id)}，
     * 因此这里返回标准 UUID 字符串，而不是裸的 SHA-256 十六进制摘要。
     */
    private static String deterministicId(String sourceFile, String heading, int chunkIndex) {
        String key = nullToEmpty(sourceFile) + '\u0000'
                + nullToEmpty(heading) + '\u0000'
                + chunkIndex;
        String id = sha256Uuid(key).toString();
        log.debug("{}:{}", key, id);
        return id;
    }

    private static UUID sha256Uuid(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            // 标记为 version 5、IETF variant，生成标准格式的 UUID
            digest[6] = (byte) ((digest[6] & 0x0F) | 0x50);
            digest[8] = (byte) ((digest[8] & 0x3F) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(digest);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String inferGame(String heading) {
        if (heading.contains("第五人格")) return INFER_GAME_IdentityV;
        return INFER_MODE_BOTH;
    }

    private static String inferCategory(String heading) {
        if (heading.contains("角色")) return INFER_CATEGORY_CHARACTER;
        if (heading.contains("辞章")) return INFER_CATEGORY_CLASSICS;
        if (heading.contains("异像")) return INFER_CATEGORY_MONSTER;
        return INFER_MODE_BOTH;
    }

    private static String inferMode(String heading) {
        if (heading.contains("求生者") || heading.contains("智者") || heading.contains("主角") ||
                heading.contains("信使") || heading.contains("英雄")) return INFER_MODE_SURVIVOR;
        if (heading.contains("监管者")) return INFER_MODE_REGULATOR;
        return INFER_MODE_BOTH;
    }

}
