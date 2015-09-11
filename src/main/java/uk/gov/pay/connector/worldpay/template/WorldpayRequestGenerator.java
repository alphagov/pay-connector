package uk.gov.pay.connector.worldpay.template;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;

import static freemarker.template.Configuration.VERSION_2_3_20;

public class WorldpayRequestGenerator {

    private Template template;

    protected WorldpayRequestGenerator(String templateName) {
        templateSetup("/templates/worldpay", templateName);
    }

    protected String buildWith(Map<String, Object> input) {
        Writer responseWriter = new StringWriter();
        try {
            template.process(input, responseWriter);
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
        cfg.setClassForTemplateLoading(WorldpayRequestGenerator.class, templateDir);

        try {
            template = cfg.getTemplate(templateName);
        } catch (IOException e) {
            throw new RuntimeException("Could not load template " + templateName + " in dir " + templateDir, e);
        }
    }

    public static WorldpayOrderSubmitRequestGenerator anOrderSubmitRequest() {
        return new WorldpayOrderSubmitRequestGenerator();
    }
}
