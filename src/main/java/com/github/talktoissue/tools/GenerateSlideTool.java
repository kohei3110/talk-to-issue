package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.poi.sl.usermodel.ShapeType;

public class GenerateSlideTool {

    private static final Color BOX_FILL = new Color(0, 120, 212);       // Microsoft blue
    private static final Color BOX_FILL_ALT = new Color(0, 153, 188);   // Teal accent
    private static final Color BOX_TEXT = Color.WHITE;
    private static final Color ARROW_COLOR = new Color(80, 80, 80);
    private static final Color DETAIL_TEXT = new Color(60, 60, 60);

    private final File outputDir;
    private final boolean dryRun;
    private String generatedFilePath;

    public GenerateSlideTool(File outputDir, boolean dryRun) {
        this.outputDir = outputDir;
        this.dryRun = dryRun;
    }

    public String getGeneratedFilePath() {
        return generatedFilePath;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "generate_slide",
            """
            Generate a PowerPoint slide deck. Each slide can use one of three layouts:
            - "bullets" (default): title + bullet points
            - "flow": title + a horizontal flow diagram with boxes connected by arrows (great for pipelines, workflows, sequences)
            - "grid": title + a grid of boxes (great for showing components, features, tool categories)
            Provide structured content and this tool will populate the template PPTX.""",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "slides", Map.of(
                        "type", "array",
                        "description", "Array of slide objects.",
                        "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "title", Map.of("type", "string", "description", "Slide title"),
                                "layout", Map.of("type", "string", "enum", List.of("bullets", "flow", "grid"),
                                    "description", "Slide layout: 'bullets' for text, 'flow' for horizontal flow diagram, 'grid' for grid of boxes. Default: bullets"),
                                "bullets", Map.of("type", "array", "items", Map.of("type", "string"),
                                    "description", "Bullet points (used when layout is 'bullets' or omitted)"),
                                "diagram_items", Map.of("type", "array",
                                    "description", "Items for flow/grid diagrams. Each has a label and optional detail.",
                                    "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                            "label", Map.of("type", "string", "description", "Box label (short, 2-4 words)"),
                                            "detail", Map.of("type", "string", "description", "Detail text shown below the box (optional, 1 line)")
                                        ),
                                        "required", List.of("label")
                                    ))
                            ),
                            "required", List.of("title")
                        )
                    ),
                    "output_filename", Map.of("type", "string", "description", "Output filename (e.g., talk-to-issue-overview.pptx)")
                ),
                "required", List.of("slides", "output_filename")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> slides = (List<Map<String, Object>>) args.get("slides");
                    String outputFilename = (String) args.get("output_filename");

                    if (dryRun) {
                        System.out.println("[DRY-RUN] Would generate " + slides.size() + " slides to " + outputFilename);
                        for (int i = 0; i < slides.size(); i++) {
                            var s = slides.get(i);
                            System.out.println("  Slide " + (i + 1) + " [" + s.getOrDefault("layout", "bullets") + "]: " + s.get("title"));
                        }
                        return Map.of("status", "dry-run", "slides_count", slides.size(), "filename", outputFilename);
                    }

                    Path baseDir = outputDir.toPath().toAbsolutePath().normalize();
                    String safeFilename = Path.of(outputFilename).getFileName().toString();
                    Path outputPath = baseDir.resolve(safeFilename).normalize();
                    if (!outputPath.startsWith(baseDir)) {
                        throw new SecurityException("Output path is outside the output directory");
                    }
                    java.nio.file.Files.createDirectories(baseDir);

                    try (InputStream templateStream = getClass().getClassLoader().getResourceAsStream("template.pptx")) {
                        if (templateStream == null) {
                            throw new IllegalStateException("Template file 'template.pptx' not found in resources");
                        }

                        try (XMLSlideShow ppt = new XMLSlideShow(templateStream)) {
                            var layouts = ppt.getSlideMasters().get(0).getSlideLayouts();
                            var contentLayout = layouts.length > 1 ? layouts[1] : layouts[0];

                            for (Map<String, Object> slideData : slides) {
                                String title = (String) slideData.get("title");
                                String layout = (String) slideData.getOrDefault("layout", "bullets");

                                var slide = ppt.createSlide(contentLayout);

                                // Set title on the title placeholder
                                for (XSLFTextShape textShape : slide.getPlaceholders()) {
                                    String phName = textShape.getShapeName().toLowerCase();
                                    if (phName.contains("title")) {
                                        textShape.setText(title);
                                    } else if (phName.contains("content")) {
                                        // Clear the content placeholder — we'll either fill it or draw shapes
                                        textShape.clearText();
                                        if ("bullets".equals(layout)) {
                                            fillBullets(textShape, slideData);
                                        } else {
                                            // Hide content placeholder text for diagram slides
                                            textShape.setText("");
                                        }
                                    }
                                }

                                if ("flow".equals(layout)) {
                                    drawFlowDiagram(slide, slideData);
                                } else if ("grid".equals(layout)) {
                                    drawGridDiagram(slide, slideData);
                                }
                            }

                            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                                ppt.write(out);
                            }
                        }
                    }

                    generatedFilePath = outputPath.toString();
                    System.out.println("Generated slide: " + outputPath);
                    return Map.of(
                        "status", "success",
                        "slides_count", slides.size(),
                        "output_path", outputPath.toString()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }

    @SuppressWarnings("unchecked")
    private void fillBullets(XSLFTextShape textShape, Map<String, Object> slideData) {
        List<String> bullets = (List<String>) slideData.getOrDefault("bullets", List.of());
        for (int i = 0; i < bullets.size(); i++) {
            var paragraph = (i == 0 && !textShape.getTextParagraphs().isEmpty())
                ? textShape.getTextParagraphs().get(0)
                : textShape.addNewTextParagraph();
            paragraph.addNewTextRun().setText(bullets.get(i));
        }
    }

    @SuppressWarnings("unchecked")
    private void drawFlowDiagram(XSLFSlide slide, Map<String, Object> slideData) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) slideData.getOrDefault("diagram_items", List.of());
        if (items.isEmpty()) return;

        int count = items.size();
        // Slide content area: x=50..670 (EMU points scaled), y=150..480
        double slideWidth = 670;
        double contentStartX = 50;
        double contentStartY = 180;

        double boxWidth = Math.min(120, (slideWidth - (count - 1) * 30) / count);
        double boxHeight = 55;
        double arrowGap = 20;
        double totalWidth = count * boxWidth + (count - 1) * arrowGap;
        double startX = contentStartX + (slideWidth - totalWidth) / 2;
        double boxY = contentStartY + 30;

        for (int i = 0; i < count; i++) {
            var item = items.get(i);
            String label = (String) item.get("label");
            String detail = (String) item.getOrDefault("detail", null);

            double x = startX + i * (boxWidth + arrowGap);

            // Draw rounded rectangle
            XSLFAutoShape box = slide.createAutoShape();
            box.setShapeType(ShapeType.ROUND_RECT);
            box.setAnchor(new Rectangle2D.Double(
                ptToEmu(x), ptToEmu(boxY), ptToEmu(boxWidth), ptToEmu(boxHeight)));
            box.setFillColor(i % 2 == 0 ? BOX_FILL : BOX_FILL_ALT);
            box.setLineWidth(0);

            // Label text inside box
            box.clearText();
            var p = box.addNewTextParagraph();
            p.setTextAlign(TextParagraph.TextAlign.CENTER);
            var run = p.addNewTextRun();
            run.setText(label);
            run.setFontColor(BOX_TEXT);
            run.setFontSize(11.0);
            run.setBold(true);

            // Detail text below box
            if (detail != null && !detail.isEmpty()) {
                XSLFAutoShape detailBox = slide.createAutoShape();
                detailBox.setShapeType(ShapeType.RECT);
                detailBox.setAnchor(new Rectangle2D.Double(
                    ptToEmu(x - 5), ptToEmu(boxY + boxHeight + 5),
                    ptToEmu(boxWidth + 10), ptToEmu(30)));
                detailBox.setFillColor(null);
                detailBox.setLineWidth(0);

                detailBox.clearText();
                var dp = detailBox.addNewTextParagraph();
                dp.setTextAlign(TextParagraph.TextAlign.CENTER);
                var dr = dp.addNewTextRun();
                dr.setText(detail);
                dr.setFontColor(DETAIL_TEXT);
                dr.setFontSize(8.0);
            }

            // Arrow to next box (line + triangle arrowhead)
            if (i < count - 1) {
                double arrowStartX = x + boxWidth + 2;
                double arrowEndX = startX + (i + 1) * (boxWidth + arrowGap) - 2;
                double arrowY = boxY + boxHeight / 2;

                // Line body
                XSLFAutoShape line = slide.createAutoShape();
                line.setShapeType(ShapeType.RECT);
                line.setAnchor(new Rectangle2D.Double(
                    ptToEmu(arrowStartX), ptToEmu(arrowY - 1),
                    ptToEmu(arrowEndX - arrowStartX - 6), ptToEmu(2)));
                line.setFillColor(ARROW_COLOR);
                line.setLineWidth(0);

                // Triangle arrowhead
                XSLFAutoShape arrowHead = slide.createAutoShape();
                arrowHead.setShapeType(ShapeType.TRIANGLE);
                arrowHead.setAnchor(new Rectangle2D.Double(
                    ptToEmu(arrowEndX - 8), ptToEmu(arrowY - 5),
                    ptToEmu(8), ptToEmu(10)));
                arrowHead.setFillColor(ARROW_COLOR);
                arrowHead.setLineWidth(0);
                arrowHead.setRotation(90);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void drawGridDiagram(XSLFSlide slide, Map<String, Object> slideData) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) slideData.getOrDefault("diagram_items", List.of());
        if (items.isEmpty()) return;

        int count = items.size();
        int cols = count <= 4 ? 2 : 3;
        int rows = (int) Math.ceil((double) count / cols);

        double contentStartX = 60;
        double contentStartY = 170;
        double gridWidth = 600;
        double gridHeight = 300;
        double gapX = 15;
        double gapY = 15;

        double boxWidth = (gridWidth - (cols - 1) * gapX) / cols;
        double boxHeight = (gridHeight - (rows - 1) * gapY) / rows;
        boxHeight = Math.min(boxHeight, 80);

        for (int i = 0; i < count; i++) {
            var item = items.get(i);
            String label = (String) item.get("label");
            String detail = (String) item.getOrDefault("detail", null);

            int col = i % cols;
            int row = i / cols;

            double x = contentStartX + col * (boxWidth + gapX);
            double y = contentStartY + row * (boxHeight + gapY);

            // Draw rounded rectangle
            XSLFAutoShape box = slide.createAutoShape();
            box.setShapeType(ShapeType.ROUND_RECT);
            box.setAnchor(new Rectangle2D.Double(
                ptToEmu(x), ptToEmu(y), ptToEmu(boxWidth), ptToEmu(boxHeight)));
            box.setFillColor(i % 2 == 0 ? BOX_FILL : BOX_FILL_ALT);
            box.setLineWidth(0);

            // Label
            box.clearText();
            var p = box.addNewTextParagraph();
            p.setTextAlign(TextParagraph.TextAlign.CENTER);
            var run = p.addNewTextRun();
            run.setText(label);
            run.setFontColor(BOX_TEXT);
            run.setFontSize(12.0);
            run.setBold(true);

            // Detail inside same box (second line)
            if (detail != null && !detail.isEmpty()) {
                var dp = box.addNewTextParagraph();
                dp.setTextAlign(TextParagraph.TextAlign.CENTER);
                var dr = dp.addNewTextRun();
                dr.setText(detail);
                dr.setFontColor(new Color(220, 220, 240));
                dr.setFontSize(9.0);
            }
        }
    }

    /** Convert points to EMU (English Metric Units). 1 pt = 12700 EMU. */
    private static double ptToEmu(double pt) {
        return pt * 12700;
    }
}
