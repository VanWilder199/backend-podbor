package by.marketplace.notification.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


@Service
public class TemplateEngineImpl {
    private final TemplateEngine templateEngine;

    public TemplateEngineImpl(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }


    public String renderOtpHtml(String code) {
        Context ctx = new Context();
        ctx.setVariable("code", code);
        return templateEngine.process("email/otp", ctx);
    }

    public String renderReportPublishedHtml(String viewUrl) {
        Context ctx = new Context();
        ctx.setVariable("viewUrl", viewUrl);
        return templateEngine.process("email/report_published", ctx);
    }

    public String renderNotificationHtml(String message) {
        Context ctx = new Context();
        ctx.setVariable("message", message);
        return templateEngine.process("email/notification", ctx);
    }

}
