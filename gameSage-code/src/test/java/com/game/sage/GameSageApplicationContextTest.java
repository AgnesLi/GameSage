package com.game.sage;

import com.game.sage.advisor.GameSageRiskAdvisor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
@ActiveProfiles("test")
class GameSageApplicationContextTest {

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ChatClient gameGuideClient;

    @Autowired
    private GameSageRiskAdvisor gameSageRiskAdvisor;

    @Test
    @DisplayName("应用上下文可加载，关键 Bean 均已装配")
    void contextLoads() {
        assertNotNull(applicationContext);
        assertNotNull(gameGuideClient);
        assertNotNull(gameSageRiskAdvisor);
        assertSame(vectorStore, applicationContext.getBean(VectorStore.class));
    }
}
