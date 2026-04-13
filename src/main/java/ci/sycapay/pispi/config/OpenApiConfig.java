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
                        new Tag().name("Transfers").description("Initiate and manage credit transfers (PACS.008 / PACS.002 / PACS.028)"),
                        new Tag().name("Identity Verification").description("Request and respond to account identity verifications (ACMT.023 / ACMT.024)"),
                        new Tag().name("Request to Pay").description("Create and manage Request-to-Pay operations (PAIN.013 / PAIN.014)"),
                        new Tag().name("Aliases").description("Manage account aliases in the RAC (creation, modification, deletion, search)"),
                        new Tag().name("Revendications").description("Manage alias ownership claim operations"),
                        new Tag().name("Return Funds").description("Initiate, accept or reject return-of-funds requests (CAMT.056 / CAMT.029 / PACS.004)"),
                        new Tag().name("Participants").description("List and synchronise SPI participant directory (CAMT.013 / CAMT.014)"),
                        new Tag().name("Notifications").description("Connectivity test (ADMI.004) and notification history"),
                        new Tag().name("Reports").description("Request compensation, transaction and invoice reports (CAMT.060)"),
                        new Tag().name("Transfer Callbacks").description("AIP callbacks for inbound transfers and transfer results"),
                        new Tag().name("Verification Callbacks").description("AIP callbacks for inbound identity verification requests"),
                        new Tag().name("RTP Callbacks").description("AIP callbacks for inbound Request-to-Pay messages"),
                        new Tag().name("Return Funds Callbacks").description("AIP callbacks for inbound return-of-funds requests"),
                        new Tag().name("Report Callbacks").description("AIP callbacks delivering compensation and guarantee report data"),
                        new Tag().name("Notification Callbacks").description("AIP callbacks for system notifications and connectivity events")));
    }

    @Bean
    public GroupedOpenApi outboundApi() {
        return GroupedOpenApi.builder()
                .group("outbound")
                .displayName("Outbound — PI to AIP")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi callbackApi() {
        return GroupedOpenApi.builder()
                .group("callbacks")
                .displayName("Callbacks — AIP to PI")
                .pathsToMatch("/api/pi/**")
                .build();
    }
}
