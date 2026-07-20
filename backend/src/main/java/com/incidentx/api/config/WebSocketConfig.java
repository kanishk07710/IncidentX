package com.incidentx.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.Arrays;

// Live submission grading: the sandbox run happens off the request thread (see
// SubmissionService#runGradingAsync) and the result is pushed to subscribers of
// /topic/submissions/{id} instead of the client having to poll. Reuses the same origin
// allowlist as the REST CORS config so local dev and the deployed Vercel origin both work.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // No SockJS fallback: plain STOMP-over-WebSocket, which Render's standard web service
        // (a persistent container, not serverless) supports directly.
        registry.addEndpoint("/ws").setAllowedOrigins(origins);
    }
}
