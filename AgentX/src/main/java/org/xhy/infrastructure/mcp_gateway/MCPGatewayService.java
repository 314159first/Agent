package org.xhy.infrastructure.mcp_gateway;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.domain.tool.model.config.ToolDefinition;
import org.xhy.domain.tool.model.config.ToolSpecificationConverter;
import org.xhy.infrastructure.config.MCPGatewayProperties;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.utils.JsonUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MCPGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(MCPGatewayService.class);

    private final MCPGatewayProperties properties;

    public MCPGatewayService(MCPGatewayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().trim().isEmpty()) {
            logger.warn("MCP Gateway base-url is not configured (mcp.gateway.base-url)");
        }

        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            logger.warn("MCP Gateway api-key is not configured (mcp.gateway.api-key)");
        }

        logger.info("MCP Gateway service initialized, baseUrl={}", properties.getBaseUrl());
    }

    public String buildUserContainerUrl(String mcpServerName, String containerIp, Integer containerPort) {
        String containerBaseUrl = "http://" + containerIp + ":" + containerPort;
        return containerBaseUrl + "/" + mcpServerName + "/sse?api_key=" + properties.getApiKey();
    }

    public String buildGlobalSSEUrl(String mcpServerName) {
        return properties.getBaseUrl() + "/" + mcpServerName + "/sse?api_key=" + properties.getApiKey();
    }

    public boolean deployTool(String installCommand) {
        String url = properties.getBaseUrl() + "/deploy";
        return deployToolToUrl(installCommand, url);
    }

    public boolean deployTool(String installCommand, String containerIp, Integer containerPort) {
        String url = "http://" + containerIp + ":" + containerPort + "/deploy";
        return deployToolToUrl(installCommand, url);
    }

    public void waitForGatewayReady(String containerIp, Integer containerPort, long timeoutMs) {
        waitForHttpEndpointReady("http://" + containerIp + ":" + containerPort + "/", timeoutMs, false);
    }

    public void waitForToolSseReady(String sseUrl, long timeoutMs) {
        waitForHttpEndpointReady(sseUrl, timeoutMs, true);
    }

    private boolean deployToolToUrl(String installCommand, String url) {
        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + properties.getApiKey());
            httpPost.setEntity(new StringEntity(installCommand, "UTF-8"));

            logger.info("Sending MCP deploy request to: {}", url);
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                String responseBody = entity != null ? EntityUtils.toString(entity) : null;

                if (statusCode >= 200 && statusCode < 300 && responseBody != null) {
                    Map result = JsonUtils.parseObject(responseBody, Map.class);
                    logger.info("MCP deploy response: {}", result);
                    Object success = result.get("success");
                    if (Boolean.TRUE.equals(success) || "true".equalsIgnoreCase(String.valueOf(success))) {
                        return true;
                    }

                    String errorMsg = String.format("MCP deploy failed, response: %s", responseBody);
                    logger.error(errorMsg);
                    throw new BusinessException(errorMsg);
                }

                String errorMsg = String.format("MCP deploy failed, status: %d, response: %s", statusCode,
                        responseBody);
                logger.error(errorMsg);
                throw new BusinessException(errorMsg);
            }
        } catch (IOException e) {
            throw new BusinessException("Failed to call MCP deploy API: " + e.getMessage(), e);
        }
    }

    public List<ToolDefinition> listTools(String toolName) throws Exception {
        String url = properties.getBaseUrl() + "/" + toolName + "/sse/sse?api_key=" + properties.getApiKey();
        HttpMcpTransport transport = new HttpMcpTransport.Builder().sseUrl(url).timeout(Duration.ofSeconds(10))
                .logRequests(false).logResponses(true).build();
        McpClient client = new DefaultMcpClient.Builder().transport(transport).build();
        try {
            List<ToolSpecification> toolSpecifications = client.listTools();
            return ToolSpecificationConverter.convert(toolSpecifications);
        } catch (Exception e) {
            logger.error("Failed to call MCP Gateway API", e);
            throw new BusinessException("Failed to call MCP Gateway API: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    public List<ToolDefinition> listToolsFromReviewContainer(String toolName, String containerIp, Integer containerPort)
            throws Exception {
        String url = "http://" + containerIp + ":" + containerPort + "/" + toolName + "/sse/sse?api_key="
                + properties.getApiKey();

        logger.info("Listing tools from review container: {}", url);

        HttpMcpTransport transport = new HttpMcpTransport.Builder().sseUrl(url).timeout(Duration.ofSeconds(10))
                .logRequests(false).logResponses(true).build();
        McpClient client = new DefaultMcpClient.Builder().transport(transport).build();
        try {
            List<ToolSpecification> toolSpecifications = client.listTools();
            List<ToolDefinition> result = ToolSpecificationConverter.convert(toolSpecifications);

            logger.info("Listed {} tool definitions from review container", result != null ? result.size() : 0);
            return result;
        } catch (Exception e) {
            logger.error("Failed to call MCP Gateway API from review container: {}:{}", containerIp, containerPort, e);
            throw new BusinessException("Failed to call MCP Gateway API from review container: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    private CloseableHttpClient createHttpClient() {
        RequestConfig config = RequestConfig.custom().setConnectTimeout(properties.getConnectTimeout())
                .setSocketTimeout(properties.getReadTimeout()).build();

        return HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    private void waitForHttpEndpointReady(String url, long timeoutMs, boolean sseRequest) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        Exception lastException = null;

        while (System.nanoTime() < deadline) {
            try (CloseableHttpClient httpClient = createHttpClient()) {
                HttpGet httpGet = new HttpGet(url);
                if (sseRequest) {
                    httpGet.setHeader("Accept", "text/event-stream");
                }

                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (isReadyStatus(statusCode, sseRequest)) {
                        logger.info("MCP endpoint ready: {}", url);
                        return;
                    }

                    HttpEntity entity = response.getEntity();
                    String responseBody = entity != null ? EntityUtils.toString(entity) : null;
                    lastException = new BusinessException(
                            String.format("Endpoint returned non-2xx status: %d, body=%s", statusCode, responseBody));
                }
            } catch (Exception e) {
                lastException = e;
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("Interrupted while waiting for MCP endpoint readiness", e);
            }
        }

        throw new BusinessException("MCP endpoint startup timeout: " + url, lastException);
    }

    private boolean isReadyStatus(int statusCode, boolean sseRequest) {
        if (sseRequest) {
            return statusCode >= 200 && statusCode < 300;
        }
        return statusCode >= 200 && statusCode < 500;
    }
}
