package uk.gov.pay.connector.gateway.templates;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import uk.gov.pay.connector.gateway.OrderRequestBuilder.TemplateData;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import static freemarker.template.Configuration.VERSION_2_3_20;

public class TemplateBuilder {
    private Template template;

    public TemplateBuilder(String templatePath) {
        templateSetup("/templates", templatePath);
    }

    public String buildWith(Object templateData) {
        Writer responseWriter = new StringWriter();
        try {
            template.process(templateData, responseWriter);
        } catch (TemplateException | IOException e) {
            throw new RuntimeException("Could not render template " + template.getName(), e);
        }
        return responseWriter.toString();
    }

    private void templateSetup(String templateDir, String templateName) {
        Configuration cfg = new Configuration(VERSION_2_3_20);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(Locale.ENGLISH);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setClassForTemplateLoading(TemplateBuilder.class, templateDir);

        try {
            template = cfg.getTemplate(templateName);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template " + templateName + " in dir " + templateDir, e);
        }
    }

}
