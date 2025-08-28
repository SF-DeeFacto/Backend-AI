package com.deefacto.ai_service.common.service;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfGeneratorService {

    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public PdfGeneratorService() {
        // Flexmark 설정
        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Bedrock 응답을 PDF로 변환
     */
    public byte[] generatePdfFromBedrockResponse(String content, String title, String employeeId) {
        try {
            log.info("PDF 생성 시작 - 제목: {}, 사용자: {}", title, employeeId);

            // Markdown을 HTML로 변환
            String htmlContent = convertMarkdownToHtml(content, title, employeeId);

            // HTML을 PDF로 변환
            return convertHtmlToPdf(htmlContent);

        } catch (Exception e) {
            log.error("PDF 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("PDF 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * PDF 파일로 저장
     */
    public String savePdfToFile(byte[] pdfBytes, String fileName) {
        try {
            String filePath = "reports/" + fileName;
            
            // 디렉토리 생성
            java.io.File directory = new java.io.File("reports");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // PDF 파일 저장
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfBytes);
            }

            log.info("PDF 파일 저장 완료: {}", filePath);
            return filePath;

        } catch (IOException e) {
            log.error("PDF 파일 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("PDF 파일 저장 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Markdown을 HTML로 변환
     */
    private String convertMarkdownToHtml(String markdownContent, String title, String employeeId) {
        try {
            // Markdown 파싱
            Node document = markdownParser.parse(markdownContent);
            String bodyHtml = htmlRenderer.render(document);

            // HTML 템플릿 생성
            String htmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>%s</title>
                    <style>
                        body {
                            font-family: 'Malgun Gothic', Arial, sans-serif;
                            margin: 40px;
                            line-height: 1.6;
                            color: #333;
                        }
                        .header {
                            text-align: center;
                            border-bottom: 2px solid #333;
                            padding-bottom: 20px;
                            margin-bottom: 30px;
                        }
                        .header h1 {
                            color: #2c5282;
                            margin-bottom: 10px;
                        }
                        .meta-info {
                            background-color: #f8f9fa;
                            padding: 15px;
                            border-radius: 5px;
                            margin-bottom: 30px;
                        }
                        .content {
                            text-align: justify;
                        }
                        .content h2 {
                            color: #2d3748;
                            border-bottom: 1px solid #e2e8f0;
                            padding-bottom: 5px;
                        }
                        .content h3 {
                            color: #4a5568;
                        }
                        .content table {
                            width: 100%%;
                            border-collapse: collapse;
                            margin: 20px 0;
                        }
                        .content table th,
                        .content table td {
                            border: 1px solid #e2e8f0;
                            padding: 8px 12px;
                            text-align: left;
                        }
                        .content table th {
                            background-color: #f7fafc;
                            font-weight: bold;
                        }
                        .footer {
                            margin-top: 50px;
                            text-align: center;
                            font-size: 12px;
                            color: #718096;
                            border-top: 1px solid #e2e8f0;
                            padding-top: 20px;
                        }
                        img {
                            max-width: 100%%;
                            height: auto;
                            display: block;
                            margin: 20px auto;
                        }
                        pre {
                            background-color: #f7fafc;
                            padding: 15px;
                            border-radius: 5px;
                            overflow-x: auto;
                        }
                        code {
                            background-color: #edf2f7;
                            padding: 2px 4px;
                            border-radius: 3px;
                            font-family: 'Courier New', monospace;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>%s</h1>
                        <p>AI 생성 리포트</p>
                    </div>
                    
                    <div class="meta-info">
                        <p><strong>생성일시:</strong> %s</p>
                        <p><strong>요청자:</strong> %s</p>
                        <p><strong>생성시스템:</strong> DeeFacto AI Service</p>
                    </div>
                    
                    <div class="content">
                        %s
                    </div>
                    
                    <div class="footer">
                        <p>본 문서는 AI에 의해 자동 생성되었습니다.</p>
                        <p>© DeeFacto AI Service - %s</p>
                    </div>
                </body>
                </html>
                """;

            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            return String.format(htmlTemplate, 
                title, title, currentTime, employeeId, bodyHtml, 
                LocalDateTime.now().getYear());

        } catch (Exception e) {
            log.error("Markdown to HTML 변환 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Markdown to HTML 변환 실패", e);
        }
    }

    /**
     * HTML을 PDF로 변환
     */
    private byte[] convertHtmlToPdf(String htmlContent) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // PDF 변환 설정
            ConverterProperties converterProperties = new ConverterProperties();
            
            // HTML을 PDF로 변환
            HtmlConverter.convertToPdf(htmlContent, outputStream, converterProperties);

            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("HTML to PDF 변환 실패: {}", e.getMessage(), e);
            throw new RuntimeException("HTML to PDF 변환 실패", e);
        }
    }

    /**
     * 간단한 텍스트를 PDF로 변환 (Markdown이 아닌 경우)
     */
    public byte[] generateSimplePdf(String content, String title, String employeeId) {
        try {
            log.info("간단한 PDF 생성 시작 - 제목: {}, 사용자: {}", title, employeeId);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // 폰트 설정
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            // 제목 추가
            Paragraph titleParagraph = new Paragraph(title)
                    .setFont(boldFont)
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(titleParagraph);

            // 메타 정보 추가
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Paragraph metaParagraph = new Paragraph("생성일시: " + currentTime + " | 요청자: " + employeeId)
                    .setFont(font)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(30);
            document.add(metaParagraph);

            // 내용 추가
            Paragraph contentParagraph = new Paragraph(content)
                    .setFont(font)
                    .setFontSize(12);
            document.add(contentParagraph);

            document.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("간단한 PDF 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("간단한 PDF 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * PDF 파일명 생성
     */
    public String generatePdfFileName(String reportType, String employeeId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("report_%s_%s_%s.pdf", reportType, employeeId, timestamp);
    }
}
