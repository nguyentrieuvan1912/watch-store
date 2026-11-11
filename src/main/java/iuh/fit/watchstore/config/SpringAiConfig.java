package iuh.fit.watchstore.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
@Slf4j
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:gemini-3.5-flash-lite}")
    private String model;

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-004}")
    private String embeddingModelName;

    @Bean
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .completionsPath("chat/completions")
                .embeddingsPath("embeddings")
                .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "dummy-key")
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel);
    }

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        OpenAiEmbeddingModel delegate = new OpenAiEmbeddingModel(
                openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(embeddingModelName)
                        .build()
        );

        return new AbstractEmbeddingModel() {
            private volatile boolean remoteAvailable = (apiKey != null && !apiKey.isBlank() && !apiKey.contains("your_gemini_api_key"));

            @Override
            public float[] embed(Document document) {
                return embed(document.getText() != null ? document.getText() : "");
            }

            @Override
            public float[] embed(String text) {
                EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null));
                if (response != null && response.getResult() != null) {
                    return response.getResult().getOutput();
                }
                return computeLocalSemanticVector(text);
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                if (remoteAvailable) {
                    try {
                        return delegate.call(request);
                    } catch (Exception e) {
                        log.warn("Gemini Embedding API fallback activated (using local semantic vector embedding): {}", e.getMessage());
                        remoteAvailable = false;
                    }
                }
                List<Embedding> embeddings = new ArrayList<>();
                List<String> instructions = request.getInstructions();
                for (int i = 0; i < instructions.size(); i++) {
                    float[] vec = computeLocalSemanticVector(instructions.get(i));
                    embeddings.add(new Embedding(vec, i));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public int dimensions() {
                return 768;
            }
        };
    }

    /**
     * Tạo vector ngữ nghĩa 768 chiều chuẩn hóa (L2-normalized) cho Cosine Similarity
     * khớp với cấu hình spring.ai.vectorstore.mariadb.dimensions=768.
     */
    private static float[] computeLocalSemanticVector(String rawText) {
        final int dims = 768;
        float[] vec = new float[dims];
        if (rawText == null || rawText.isBlank()) {
            vec[0] = 1.0f;
            return vec;
        }
        String text = rawText.toLowerCase(Locale.ROOT);
        String[] tokens = text.split("[^\\p{L}\\p{N}]+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            // Weight key watch domain attributes higher for sharper cosine similarity
            float weight = 1.0f;
            if (token.equals("casio") || token.equals("seiko") || token.equals("citizen")
                    || token.equals("orient") || token.equals("tissot") || token.equals("rolex")
                    || token.equals("omega")) {
                weight = 3.5f;
            } else if (token.equals("automatic") || token.equals("quartz") || token.equals("eco")
                    || token.equals("solar") || token.equals("cơ") || token.equals("pin")) {
                weight = 2.8f;
            } else if (token.equals("nam") || token.equals("nữ") || token.equals("unisex")) {
                weight = 2.5f;
            } else if (token.equals("da") || token.equals("thép") || token.equals("titanium")
                    || token.equals("cao") || token.equals("lặn") || token.equals("luxury")) {
                weight = 2.2f;
            }

            int h1 = Math.abs(token.hashCode()) % dims;
            int h2 = Math.abs((token + "_bi").hashCode()) % dims;
            vec[h1] += weight;
            vec[h2] += weight * 0.6f;

            // Character 3-grams for partial similarity
            if (token.length() >= 3) {
                for (int i = 0; i <= token.length() - 3; i++) {
                    String gram = token.substring(i, i + 3);
                    int gh = Math.abs(gram.getBytes(StandardCharsets.UTF_8).length * 31 + gram.hashCode()) % dims;
                    vec[gh] += 0.35f;
                }
            }
        }

        // Normalize to unit length for Cosine Similarity
        double norm = 0.0;
        for (float v : vec) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-9) {
            for (int i = 0; i < dims; i++) {
                vec[i] = (float) (vec[i] / norm);
            }
        } else {
            vec[0] = 1.0f;
        }
        return vec;
    }
}
