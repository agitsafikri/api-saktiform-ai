package com.saktiform.api.service.blast.placeholder.resolver;

import com.saktiform.api.service.blast.placeholder.BlastMessageContext;
import com.saktiform.api.service.blast.placeholder.PlaceholderResolver;
import org.springframework.stereotype.Component;

@Component
public class PhoneResolver implements PlaceholderResolver {
    @Override
    public String key() {
        return "phone";
    }

    @Override
    public String resolve(BlastMessageContext ctx) {
        return ctx.getRecipientPhone();
    }
}
