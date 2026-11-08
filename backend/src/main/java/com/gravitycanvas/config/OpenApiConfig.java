package com.gravitycanvas.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 3.0 Configuration for Priora.
 * Defines API metadata, global security scheme (HTTP Basic Auth), and server definitions.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Priora Task Prioritization & Workload Management API",
        version = "1.0.0",
        description = "Intelligent Task Prioritization & Workload Balancing Platform.\n\n" +
            "Key Engineering Features:\n" +
            "- **Dynamic Gravity Engine**: Two-phase Sigmoid deadline proximity, logarithmic blast-radius, and stagnation scoring.\n" +
            "- **DAG Cycle Prevention**: In-memory BFS reachability validation preventing circular task dependencies.\n" +
            "- **Role-Based Access Control**: Strict multi-role guards (Project Manager, Team Lead, Team Member, Tester).\n" +
            "- **Optimistic Locking**: JPA @Version concurrency control against lost updates.\n" +
            "- **Hibernate EntityGraph Optimization**: Single-query prefetching eliminating N+1 query bottlenecks.",
        contact = @Contact(
            name = "Priora Engineering Team",
            url = "https://github.com/priora"
        )
    ),
    security = {
        @SecurityRequirement(name = "basicAuth")
    },
    servers = {
        @Server(url = "/", description = "Default Server URL")
    }
)
@SecurityScheme(
    name = "basicAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "basic",
    description = "HTTP Basic Authentication using registered username and password credentials."
)
public class OpenApiConfig {
}
