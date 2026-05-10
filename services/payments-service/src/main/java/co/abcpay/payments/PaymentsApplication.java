package co.abcpay.payments;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "ABC Pay - Payments Service",
        version = "0.1.0",
        description = "Persists payment transactions and emits an immutable audit event in the ledger. Trusts the integrity verdict from the gateway/validator pipeline.",
        contact = @Contact(name = "ABC Pay")))
public class PaymentsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
