package co.abcpay.validator;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "ABC Pay - Signature Validator",
        version = "0.1.0",
        description = "Implements the Verify Message Integrity tactic. Recomputes the HMAC over the canonical request and rejects on mismatch or replay.",
        contact = @Contact(name = "ABC Pay")))
public class ValidatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValidatorApplication.class, args);
    }
}
