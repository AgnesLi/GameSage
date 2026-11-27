package com.game.sage.config;

import com.game.sage.advisor.GameSageRiskAdvisor;
import com.game.sage.prompt.SystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class GameSageConfig {

    @Bean
    public ChatClient gameGuideClient(ChatClient.Builder builder,
                                      SessionService sessionService,
                                      GameSageRiskAdvisor gameSageRiskAdvisor) {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(gameSageRiskAdvisor);
        advisors.add(new SimpleLoggerAdvisor(
                request -> {
                    return "Custom request: " + (request != null ? request.prompt().getUserMessage() : null);
                },
                response -> {
                    return "Custom response: " + (response != null ? response.getResult() : null);
                },
                0
        ));
        advisors.addAll(
                SessionAdvisors.builder(sessionService)
                        .compaction(
                                new TurnCountTrigger(5),  // 触发条件：达到5个turn时压缩
                                WindowCompactionStrategy.builder().lastTurns(5).build()  // 策略：保留最近5个turn
                        )
                        .build()
        );

        return builder
                .defaultSystem(SystemPrompt.GAME_GUIDE_PROMPT)
                .defaultAdvisors(advisors.toArray(new Advisor[0]))
                .build();
    }

    @Bean
    public SessionRepository sessionRepository() {
        return InMemorySessionRepository.builder().build();
    }
}
