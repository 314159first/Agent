package org.xhy.infrastructure.llm.factory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.xhy.infrastructure.llm.config.ProviderConfig;
import org.xhy.infrastructure.llm.protocol.enums.ProviderProtocol;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public class LLMProviderFactory {

    /** 获取对应的服务商 不使用工厂模式，因为 OpenAiChatModel 没有无参构造器，并且其他类型的模型不能适配
     * @param protocol 协议
     * @param providerConfig 服务商信息 */
    public static ChatModel getLLMProvider(ProviderProtocol protocol, ProviderConfig providerConfig) {
        ChatModel model = null;
        if (protocol == ProviderProtocol.OPENAI) {
            OpenAiChatModel.OpenAiChatModelBuilder openAiChatModelBuilder = new OpenAiChatModel.OpenAiChatModelBuilder();
            openAiChatModelBuilder.apiKey(providerConfig.getApiKey());
            openAiChatModelBuilder.baseUrl(providerConfig.getBaseUrl());
            openAiChatModelBuilder.customHeaders(providerConfig.getCustomHeaders());
            openAiChatModelBuilder.modelName(providerConfig.getModel());
            openAiChatModelBuilder.timeout(Duration.ofHours(1));
            model = new OpenAiChatModel(openAiChatModelBuilder);
        } else if (protocol == ProviderProtocol.ANTHROPIC) {
            model = AnthropicChatModel.builder().apiKey(providerConfig.getApiKey()).baseUrl(providerConfig.getBaseUrl())
                    .modelName(providerConfig.getModel()).version("2023-06-01").timeout(Duration.ofHours(1)).build();
        }
        return model;
    }

    public static StreamingChatModel getLLMProviderByStream(ProviderProtocol protocol, ProviderConfig providerConfig) {
        StreamingChatModel model = null;
        if (protocol == ProviderProtocol.OPENAI) {
            model = new OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder().apiKey(providerConfig.getApiKey())
                    .baseUrl(providerConfig.getBaseUrl()).customHeaders(providerConfig.getCustomHeaders())
                    .modelName(providerConfig.getModel()).timeout(Duration.ofHours(1)).build();
        } else if (protocol == ProviderProtocol.ANTHROPIC) {
            model = AnthropicStreamingChatModel.builder().apiKey(providerConfig.getApiKey())
                    .baseUrl(providerConfig.getBaseUrl()).version("2023-06-01").modelName(providerConfig.getModel())
                    .timeout(Duration.ofHours(1)).build();
        }

        return model == null ? null : new NullSafeStreamingChatModel(model, providerConfig.getModel());
    }

    private static class NullSafeStreamingChatModel implements StreamingChatModel {

        private final StreamingChatModel delegate;
        private final String modelName;

        private NullSafeStreamingChatModel(StreamingChatModel delegate, String modelName) {
            this.delegate = delegate;
            this.modelName = modelName;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            delegate.doChat(chatRequest, new NullSafeStreamingChatResponseHandler(handler, modelName));
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }

        @Override
        public ModelProvider provider() {
            return delegate.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return delegate.supportedCapabilities();
        }
    }

    private static class NullSafeStreamingChatResponseHandler implements StreamingChatResponseHandler {

        private final StreamingChatResponseHandler delegate;
        private final String modelName;
        private final StringBuilder streamedResponse = new StringBuilder();

        private NullSafeStreamingChatResponseHandler(StreamingChatResponseHandler delegate, String modelName) {
            this.delegate = delegate;
            this.modelName = modelName;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            if (partialResponse != null) {
                streamedResponse.append(partialResponse);
            }
            delegate.onPartialResponse(partialResponse);
        }

        @Override
        public void onPartialReasoning(String partialReasoning) {
            delegate.onPartialReasoning(partialReasoning);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            if (completeResponse == null || completeResponse.aiMessage() == null) {
                delegate.onCompleteResponse(buildFallbackResponse(completeResponse));
            } else {
                delegate.onCompleteResponse(completeResponse);
            }
        }

        private ChatResponse buildFallbackResponse(ChatResponse completeResponse) {
            ChatResponse.Builder builder = ChatResponse.builder().aiMessage(AiMessage.from(streamedResponse.toString()));
            if (completeResponse != null && completeResponse.metadata() != null) {
                return builder.metadata(completeResponse.metadata()).build();
            }
            return builder.modelName(modelName).build();
        }

        @Override
        public void onCompleteReasoning(String completeReasoning) {
            delegate.onCompleteReasoning(completeReasoning);
        }

        @Override
        public void onRawData(Object rawData) {
            delegate.onRawData(rawData);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }
    }
}
