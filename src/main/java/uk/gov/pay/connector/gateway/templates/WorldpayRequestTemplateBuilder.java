package uk.gov.pay.connector.gateway.templates;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayRequest;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import static freemarker.template.Configuration.VERSION_2_3_34;

public class WorldpayRequestTemplateBuilder {
    private final Configuration cfg;

    public WorldpayRequestTemplateBuilder() {
        cfg = new Configuration(VERSION_2_3_34);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.ENGLISH);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setClassForTemplateLoading(WorldpayRequestTemplateBuilder.class, "/templates");
    }

    public String buildWith(String templatePath, WorldpayRequest templateRecord) {
        Template template;
        try {
            template = cfg.getTemplate(templatePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template " + templatePath + " in dir /templates", e);
        }
        Writer responseWriter = new StringWriter();
        try {
            template.process(templateRecord, responseWriter);
        } catch (TemplateException | IOException e) {
            throw new RuntimeException("Could not render template " + template.getName(), e);
        }
        return responseWriter.toString();
    }
}
