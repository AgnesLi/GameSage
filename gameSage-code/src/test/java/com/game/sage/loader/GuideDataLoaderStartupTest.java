package com.game.sage.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "datasource.update=true")
@ActiveProfiles("test")
class GuideDataLoaderStartupTest {

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("datasource.update=true 时启动会执行加载器并写入向量库")
    void contextShouldRunLoaderAndWriteWhenUpdateEnabled() {
        assertNotNull(applicationContext);
        verify(vectorStore).add(anyList());
    }
}
