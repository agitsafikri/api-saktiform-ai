package com.saktiform.api.service.blast.placeholder.resolver;

import com.saktiform.api.service.blast.placeholder.BlastMessageContext;
import com.saktiform.api.service.blast.placeholder.PlaceholderResolver;
import org.springframework.stereotype.Component;

@Component
public class NameResolver implements PlaceholderResolver {
    @Override
    public String key() {
        return "name";
    }

    @Override
    public String resolve(BlastMessageContext ctx) {
        return ctx.getRecipientName();
    }
}
