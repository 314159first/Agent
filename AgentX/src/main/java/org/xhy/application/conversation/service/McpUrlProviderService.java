package org.xhy.application.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.application.container.dto.ContainerDTO;
import org.xhy.application.container.service.ContainerAppService;
import org.xhy.domain.container.constant.ContainerStatus;
import org.xhy.domain.tool.model.ToolEntity;
import org.xhy.domain.tool.service.ToolDomainService;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.mcp_gateway.MCPGatewayService;
import org.xhy.infrastructure.utils.JsonUtils;

import java.util.Map;

@Service
public class McpUrlProviderService {

    private static final Logger logger = LoggerFactory.getLogger(McpUrlProviderService.class);
    private static final long GATEWAY_READY_TIMEOUT_MS = 30000L;
    private static final long TOOL_SSE_READY_TIMEOUT_MS = 60000L;

    private final MCPGatewayService mcpGatewayService;
    private final ContainerAppService containerAppService;
    private final ToolDomainService toolDomainService;

    public McpUrlProviderService(MCPGatewayService mcpGatewayService, ContainerAppService containerAppService,
            ToolDomainService toolDomainService) {
        this.mcpGatewayService = mcpGatewayService;
        this.containerAppService = containerAppService;
        this.toolDomainService = toolDomainService;
    }

    public String getSSEUrl(String mcpServerName, String userId) {
        boolean isGlobalTool = isGlobalTool(mcpServerName, userId);

        if (isGlobalTool) {
            return buildReviewContainerSSEUrl(mcpServerName);
        }

        return buildUserContainerSSEUrl(mcpServerName, userId);
    }

    public String getMcpToolUrl(String mcpServerName, String userId) {
        try {
            return getSSEUrl(mcpServerName, userId);
        } catch (Exception e) {
            logger.error("Failed to get MCP tool URL: userId={}, tool={}", userId, mcpServerName, e);
            throw new BusinessException("Unable to connect tool: " + mcpServerName + " - " + e.getMessage(), e);
        }
    }

    private boolean isGlobalTool(String mcpServerName, String userId) {
        try {
            ToolEntity tool = toolDomainService.getToolByServerNameForUsage(mcpServerName, userId);
            return tool != null && tool.isGlobal();
        } catch (Exception e) {
            logger.warn("Unable to determine tool type, defaulting to user tool: {}", mcpServerName, e);
            return false;
        }
    }

    private String buildUserContainerSSEUrl(String mcpServerName, String userId) {
        try {
            logger.info("Preparing user container tool connection: userId={}, tool={}", userId, mcpServerName);

            ContainerDTO containerInfo = ensureUserContainerReady(userId);
            mcpGatewayService.waitForGatewayReady(containerInfo.getIpAddress(), containerInfo.getExternalPort(),
                    GATEWAY_READY_TIMEOUT_MS);

            String sseUrl = mcpGatewayService.buildUserContainerUrl(mcpServerName, containerInfo.getIpAddress(),
                    containerInfo.getExternalPort());

            deployTool(containerInfo, mcpServerName, userId);
            mcpGatewayService.waitForToolSseReady(sseUrl, TOOL_SSE_READY_TIMEOUT_MS);

            logger.info("User container tool connection ready: userId={}, url={}", userId, maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Failed to build user container SSE URL: userId={}, tool={}", userId, mcpServerName, e);
            throw new BusinessException("Unable to connect user tool: " + e.getMessage(), e);
        }
    }

    private String buildReviewContainerSSEUrl(String mcpServerName) {
        try {
            logger.info("Preparing review container tool connection: tool={}", mcpServerName);

            ContainerDTO containerInfo = ensureReviewContainerReady();
            mcpGatewayService.waitForGatewayReady(containerInfo.getIpAddress(), containerInfo.getExternalPort(),
                    GATEWAY_READY_TIMEOUT_MS);

            String sseUrl = mcpGatewayService.buildUserContainerUrl(mcpServerName, containerInfo.getIpAddress(),
                    containerInfo.getExternalPort());
            mcpGatewayService.waitForToolSseReady(sseUrl, TOOL_SSE_READY_TIMEOUT_MS);

            logger.info("Review container tool connection ready: tool={}, url={}", mcpServerName,
                    maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Failed to build review container SSE URL: tool={}", mcpServerName, e);
            throw new BusinessException("Unable to connect global tool: " + e.getMessage(), e);
        }
    }

    private ContainerDTO ensureUserContainerReady(String userId) {
        try {
            ContainerDTO userContainer = containerAppService.getUserContainer(userId);
            if (!isContainerHealthy(userContainer)) {
                throw new BusinessException("User container is not ready, status: " + userContainer.getStatus());
            }
            return userContainer;
        } catch (Exception e) {
            logger.error("Failed to prepare user container: userId={}", userId, e);
            throw new BusinessException("User container preparation failed: " + e.getMessage(), e);
        }
    }

    private boolean isContainerHealthy(ContainerDTO container) {
        if (container == null) {
            return false;
        }

        boolean isRunning = ContainerStatus.RUNNING.equals(container.getStatus());
        boolean hasNetworkInfo = container.getIpAddress() != null && container.getExternalPort() != null;
        boolean hasDockerContainerId = container.getDockerContainerId() != null;
        boolean basicHealthy = isRunning && hasNetworkInfo && hasDockerContainerId;

        if (!basicHealthy) {
            logger.warn("Container basic health check failed: containerId={}, running={}, networkInfo={}, dockerId={}",
                    container.getId(), isRunning, hasNetworkInfo, hasDockerContainerId);
        }

        return basicHealthy;
    }

    private void deployTool(ContainerDTO container, String toolName, String userId) {
        try {
            ToolEntity tool = toolDomainService.getToolByServerNameForUsage(toolName, userId);
            if (tool == null) {
                throw new BusinessException("Tool definition not found: " + toolName);
            }

            String installCommandJson = convertInstallCommand(tool.getInstallCommand());
            mcpGatewayService.deployTool(installCommandJson, container.getIpAddress(), container.getExternalPort());
            logger.debug("Tool deploy request sent to user container: tool={}", toolName);
        } catch (Exception e) {
            logger.error("Failed to deploy tool into user container: tool={}, error={}", toolName, e.getMessage(), e);
            throw new BusinessException("Failed to deploy tool into user container: " + e.getMessage(), e);
        }
    }

    private String convertInstallCommand(Map<String, Object> installCommand) {
        try {
            return JsonUtils.toJsonString(installCommand);
        } catch (Exception e) {
            throw new BusinessException("Failed to convert install command: " + e.getMessage(), e);
        }
    }

    private ContainerDTO ensureReviewContainerReady() {
        try {
            ContainerDTO reviewContainer = containerAppService.getOrCreateReviewContainer();
            if (!isContainerHealthy(reviewContainer)) {
                throw new BusinessException("Review container is not ready, status: " + reviewContainer.getStatus());
            }
            return reviewContainer;
        } catch (Exception e) {
            logger.error("Failed to prepare review container", e);
            throw new BusinessException("Review container preparation failed: " + e.getMessage(), e);
        }
    }

    private String maskSensitiveInfo(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("api_key=[^&]*", "api_key=***");
    }
}
