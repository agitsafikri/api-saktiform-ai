package com.saktiform.api.service.blast.placeholder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Engine placeholder {{key}} berbasis registry resolver (OQ-6). Unknown token dibiarkan apa adanya + log.
 */
@Component
public class PlaceholderEngine {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderEngine.class);
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private final Map<String, PlaceholderResolver> resolvers;

    public PlaceholderEngine(List<PlaceholderResolver> resolverList) {
        this.resolvers = resolverList.stream()
                .collect(Collectors.toMap(PlaceholderResolver::key, r -> r));
    }

    public String render(String template, BlastMessageContext ctx) {
        if (template == null) return null;
        Matcher m = TOKEN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            PlaceholderResolver resolver = resolvers.get(key);
            if (resolver == null) {
                log.warn("Unknown placeholder {{{}}} dibiarkan apa adanya", key);
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        Objects.toString(resolver.resolve(ctx), "")));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
