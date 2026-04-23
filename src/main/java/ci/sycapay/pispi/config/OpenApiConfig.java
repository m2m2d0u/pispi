package ci.sycapay.pispi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pispiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PI-SPI Connector API")
                        .description("""
                                Connector between Sycapay Participant Institution (PI) and the BCEAO Automated Interbank Platform (AIP/SPI).

                                ## Overview
                                This API exposes two categories of endpoints:
                                - **Outbound** – Operations initiated by the PI toward the AIP (transfers, alias management, RTP, identity verification, return funds, reports, participants, notifications).
                                - **Callbacks** – Endpoints called by the AIP to push inbound messages to the PI (transfer results, RTP requests, return fund requests, reports, notifications).

                                ## Message Flow
                                All outbound operations are logged, saved to the local database, and forwarded to the AIP using ISO 20022 messages over mTLS.
                                Callback endpoints receive AIP pushes, log them, update local state, and forward events to the Sycapay backend via webhook.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sycapay")
                                .email("tech@sycapay.ci")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://pi-spi.sycapay.ci").description("Production")))
                .tags(List.of(
                        new Tag().name("Transactions (mobile)").description("Unified mobile-facing transfer endpoints per BCEAO remote spec — send_now (PACS.008), receive_now (PAIN.013), send_schedule (Programme / Abonnement)"),
                        new Tag().name("Request to Pay").description("Create and manage Request-to-Pay operations (PAIN.013 / PAIN.014)"),
                        new Tag().name("Aliases").description("Manage account aliases in the RAC (creation, modification, deletion, search)"),
                        new Tag().name("Revendications").description("Manage alias ownership claim operations"),
                        new Tag().name("Participants").description("List and synchronise SPI participant directory (CAMT.013 / CAMT.014)"),
                        new Tag().name("Notifications").description("Connectivity test (ADMI.004) and notification history"),
                        new Tag().name("Reports").description("Request compensation, transaction and invoice reports (CAMT.060)"),
                        new Tag().name("Transfer Callbacks").description("AIP callbacks for inbound transfers and transfer results"),
                        new Tag().name("RTP Callbacks").description("AIP callbacks for inbound Request-to-Pay messages"),
                        new Tag().name("Report Callbacks").description("AIP callbacks delivering compensation and guarantee report data"),
                        new Tag().name("Notification Callbacks").description("AIP callbacks for system notifications and connectivity events")));
    }

    @Bean
    public GroupedOpenApi outboundApi() {
        return GroupedOpenApi.builder()
                .group("outbound")
                .displayName("Outbound — PI to AIP")
                .packagesToScan("ci.sycapay.pispi.controller.outbound")
                .build();
    }

    @Bean
    public GroupedOpenApi callbackApi() {
        // Callback controllers live under a single package but use the BCEAO-defined
        // root paths (/transferts, /demandes-paiements, /verifications-identites,
        // /retour-fonds, /rapports/*, /notifications/*, /revendications/*, etc.) —
        // a single pathsToMatch pattern can't capture them all, so group by package.
        return GroupedOpenApi.builder()
                .group("callbacks")
                .displayName("Callbacks — AIP to PI")
                .packagesToScan("ci.sycapay.pispi.controller.callback")
                .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        // Catch-all group so the default Swagger UI view ("all") shows every endpoint
        // and every referenced DTO in a single page — useful when hunting for a
        // specific schema name.
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All endpoints")
                .packagesToScan("ci.sycapay.pispi.controller")
                .build();
    }
}
