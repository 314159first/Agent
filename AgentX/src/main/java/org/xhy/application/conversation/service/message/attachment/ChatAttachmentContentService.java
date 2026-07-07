package org.xhy.application.conversation.service.message.attachment;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatAttachmentContentService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAttachmentContentService.class);

    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARS = 60_000;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final Set<String> WORD_EXTENSIONS = Set.of("doc", "docx");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "csv", "json", "html", "xml");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md", "csv", "json",
            "html", "xml");

    public boolean isImage(String fileUrl) {
        return IMAGE_EXTENSIONS.contains(extensionOf(fileUrl));
    }

    public boolean isSupportedDocument(String fileUrl) {
        return DOCUMENT_EXTENSIONS.contains(extensionOf(fileUrl));
    }

    public Optional<String> buildDocumentMessage(String fileUrl) {
        if (!isSupportedDocument(fileUrl)) {
            return Optional.empty();
        }

        String fileName = fileNameOf(fileUrl);
        try {
            byte[] bytes = download(fileUrl);
            String text = extractText(bytes, extensionOf(fileUrl));
            if (StringUtils.isBlank(text)) {
                return Optional.of("[Attached document]\nFile: " + fileName
                        + "\nThe document was uploaded successfully, but no readable text was extracted.");
            }

            String normalizedText = normalize(text);
            boolean truncated = normalizedText.length() > MAX_EXTRACTED_CHARS;
            if (truncated) {
                normalizedText = normalizedText.substring(0, MAX_EXTRACTED_CHARS);
            }

            StringBuilder message = new StringBuilder();
            message.append("[Attached document]\n");
            message.append("File: ").append(fileName).append("\n");
            message.append("Use the following extracted document text as context for the user's request.");
            if (truncated) {
                message.append(" The content was truncated because it is too long.");
            }
            message.append("\n\n");
            message.append(normalizedText);
            return Optional.of(message.toString());
        } catch (Throwable e) {
            logger.warn("Failed to extract chat attachment text, fileUrl={}, error={}", fileUrl, e.getMessage(), e);
            return Optional.of("[Attached document]\nFile: " + fileName
                    + "\nThe document was uploaded successfully, but the system failed to extract its text.");
        }
    }

    private String extractText(byte[] bytes, String extension) throws Exception {
        if ("pdf".equals(extension)) {
            return extractPdfText(bytes);
        }
        if (WORD_EXTENSIONS.contains(extension)) {
            return extractWordText(bytes);
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return "";
    }

    private String extractWordText(byte[] bytes) {
        ApachePoiDocumentParser parser = new ApachePoiDocumentParser();
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            Document document = parser.parse(inputStream);
            return document == null ? "" : document.text();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Word document", e);
        }
    }

    private String extractPdfText(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PDF document", e);
        }
    }

    private byte[] download(String fileUrl) throws Exception {
        URL url = URI.create(fileUrl).toURL();
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);

        if (connection instanceof HttpURLConnection httpConnection) {
            int status = httpConnection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download failed with HTTP status " + status);
            }
        }

        try (InputStream inputStream = connection.getInputStream();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOCUMENT_BYTES) {
                    throw new IllegalStateException("Document is larger than " + MAX_DOCUMENT_BYTES + " bytes");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private String normalize(String text) {
        return text.replace('\u0000', ' ').replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String extensionOf(String fileUrl) {
        String fileName = fileNameOf(fileUrl);
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String fileNameOf(String fileUrl) {
        if (StringUtils.isBlank(fileUrl)) {
            return "unknown";
        }
        try {
            String path = URI.create(fileUrl).getPath();
            int slash = path.lastIndexOf('/');
            String fileName = slash >= 0 ? path.substring(slash + 1) : path;
            return StringUtils.defaultIfBlank(URLDecoder.decode(fileName, StandardCharsets.UTF_8), "unknown");
        } catch (Exception e) {
            int slash = fileUrl.lastIndexOf('/');
            String fileName = slash >= 0 ? fileUrl.substring(slash + 1) : fileUrl;
            int query = fileName.indexOf('?');
            if (query >= 0) {
                fileName = fileName.substring(0, query);
            }
            return StringUtils.defaultIfBlank(fileName, "unknown");
        }
    }
}
