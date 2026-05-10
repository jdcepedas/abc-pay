package co.abcpay.ledger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "ABC Pay - Immutable Ledger",
        version = "0.1.0",
        description = "Implements the Maintain Audit Trail tactic. Append-only, hash-chained ledger anchored by an HMAC-signed head row to detect any retroactive tampering.",
        contact = @Contact(name = "ABC Pay")))
public class LedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
