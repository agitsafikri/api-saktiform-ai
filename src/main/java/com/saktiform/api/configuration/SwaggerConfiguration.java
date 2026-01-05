package com.saktiform.api.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {
    @Bean
    public OpenAPI openAPI(){
        String schemeName = "bearerAuth";

        var info = new Info().title("Saktiform API Documentation")
                .description("API untuk menggunakan data pada aplikasi Saktiform")
                .version("v 1.0.0");

//        var externalDocumentation = new ExternalDocumentation()
//                .description("Saktiform MVC UI").url("/");

        var requirement = new SecurityRequirement().addList(schemeName);

        var scheme = new SecurityScheme()
                .name(schemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("Bearer")
                .bearerFormat("JWT");

        var components = new Components().addSecuritySchemes(schemeName, scheme);

        var openApi = new OpenAPI()
                .info(info)
//                .externalDocs(externalDocumentation)
                .addSecurityItem(requirement)
                .components(components);

        return openApi;
    }
}
