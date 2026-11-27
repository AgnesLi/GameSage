package com.game.sage.config;

import com.game.sage.advisor.GameSageRiskAdvisor;
import com.game.sage.prompt.SystemPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameSageConfigTest {

    @Mock
    private ChatClient.Builder builder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private SessionService sessionService;

    @Mock
    private GameSageRiskAdvisor riskAdvisor;

    @Test
    @DisplayName("构建 ChatClient：使用攻略系统提示词，并按 Risk → 日志 → Session 顺序装配 Advisor")
    void gameGuideClient_shouldConfigureSystemPromptAndAdvisors() {
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        ChatClient result = new GameSageConfig()
                .gameGuideClient(builder, sessionService, riskAdvisor);

        assertSame(chatClient, result);
        verify(builder).defaultSystem(SystemPrompt.GAME_GUIDE_PROMPT);

        ArgumentCaptor<Advisor[]> captor = ArgumentCaptor.forClass(Advisor[].class);
        verify(builder).defaultAdvisors(captor.capture());
        Advisor[] advisors = captor.getValue();

        assertTrue(advisors.length >= 3, "至少应包含风控、日志、Session 三类 Advisor");
        assertSame(riskAdvisor, advisors[0], "风控 Advisor 必须最先执行");
        assertInstanceOf(SimpleLoggerAdvisor.class, advisors[1]);
        for (int i = 2; i < advisors.length; i++) {
            assertNotNull(advisors[i], "Session Advisor 不应为 null");
        }
    }

    @Test
    @DisplayName("默认会话仓库使用内存实现，适合本地开发")
    void sessionRepository_shouldUseInMemoryImplementation() {
        SessionRepository repository = new GameSageConfig().sessionRepository();

        assertInstanceOf(InMemorySessionRepository.class, repository);
    }
}
