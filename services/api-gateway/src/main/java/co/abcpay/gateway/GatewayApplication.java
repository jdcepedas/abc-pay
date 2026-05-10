package co.abcpay.gateway;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "ABC Pay - API Gateway",
        version = "0.1.0",
        description = "Edge service for ASR-SEG-02. Forwards verified, integrity-checked traffic to downstream payments.",
        contact = @Contact(name = "ABC Pay")))
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
