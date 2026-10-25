package iuh.fit.fashionwebsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class FashionWebsiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(FashionWebsiteApplication.class, args);
    }

}
