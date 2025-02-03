package ch.admin.bit.jeap.messagecontract.web.api;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class TracingErrorAttributes extends DefaultErrorAttributes {

    private final Tracer tracer;

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> errorAttributes = super.getErrorAttributes(webRequest, options);
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            errorAttributes.put("traceId", currentSpan.context().traceId());
        }
        return errorAttributes;
    }
}
