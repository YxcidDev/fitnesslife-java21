package com.fitnesslife.gym.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeDocumentParser {

    public record Section(String heading, String text) {
    }

    public record ParsedDocument(String title, List<Section> sections) {
    }

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,4})\\s+(.+?)\\s*#*\\s*$");
    private static final int MAX_PDF_PAGES = 300;

        public ParsedDocument parse(String fileName, byte[] content) {
        String fallbackTitle = humanize(fileName);
        return switch (extensionOf(fileName)) {
            case "md" -> parseMarkdown(text(content), fallbackTitle);
            case "txt" -> new ParsedDocument(fallbackTitle, List.of(new Section(null, normalize(text(content)))));
            case "html" -> parseHtml(text(content), fallbackTitle);
            case "pdf" -> parsePdf(content, fallbackTitle);
            default -> throw new IllegalArgumentException("Formato no soportado");
        };
    }

    private String text(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private ParsedDocument parseMarkdown(String raw, String fallbackTitle) {
        String title = null;
        String heading = null;
        StringBuilder body = new StringBuilder();
        List<Section> sections = new ArrayList<>();

        for (String line : normalize(raw).split("\n")) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line);
            if (matcher.matches()) {
                flush(sections, heading, body);
                String text = matcher.group(2).strip();
                if (title == null && matcher.group(1).length() == 1) {
                    title = text;
                    heading = null;
                } else {
                    heading = text;
                }
            } else {
                body.append(line).append('\n');
            }
        }
        flush(sections, heading, body);
        return new ParsedDocument(title != null ? title : fallbackTitle, sections);
    }

    private ParsedDocument parseHtml(String html, String fallbackTitle) {
        org.jsoup.nodes.Document document = Jsoup.parse(html);
        Element root = document.selectFirst(".legal-container");
        if (root == null) {
            root = document.body();
        }

        String title = null;
        String heading = null;
        StringBuilder body = new StringBuilder();
        List<Section> sections = new ArrayList<>();

        for (Element element : root.select("h1, h2, h3, p, li, tr, .legal-contact")) {
            switch (element.normalName()) {
                case "h1" -> {
                    if (title == null) {
                        title = element.text();
                    }
                }
                case "h2", "h3" -> {
                    flush(sections, heading, body);
                    heading = element.text();
                }
                case "tr" -> body.append(String.join(" | ", element.select("th, td").eachText())).append('\n');
                case "li" -> body.append("- ").append(element.text()).append('\n');
                default -> body.append(element.text()).append('\n');
            }
        }
        flush(sections, heading, body);

        String documentTitle = title != null ? title
                : (StringUtils.hasText(document.title()) ? document.title() : fallbackTitle);
        return new ParsedDocument(documentTitle, sections);
    }

        private ParsedDocument parsePdf(byte[] content, String fallbackTitle) {
        Path temp = null;
        List<Document> pages;
        try {
            temp = Files.createTempFile("kb-", ".pdf");
            Files.write(temp, content);
            pages = new PagePdfDocumentReader(new FileSystemResource(temp)).get();
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("No se pudo leer el PDF");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < Math.min(pages.size(), MAX_PDF_PAGES); i++) {
            String text = normalize(pages.get(i).getText() == null ? "" : pages.get(i).getText());
            if (!text.isBlank()) {
                Object page = pages.get(i).getMetadata().getOrDefault("page_number", i + 1);
                sections.add(new Section("Página " + page, text));
            }
        }
        return new ParsedDocument(fallbackTitle, sections);
    }

    private void flush(List<Section> sections, String heading, StringBuilder body) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            sections.add(new Section(heading, text));
        }
        body.setLength(0);
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" ?\n ?", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private String humanize(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = (dot < 0 ? fileName : fileName.substring(0, dot)).replace('-', ' ').replace('_', ' ').strip();
        return base.isEmpty() ? fileName : Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }
}