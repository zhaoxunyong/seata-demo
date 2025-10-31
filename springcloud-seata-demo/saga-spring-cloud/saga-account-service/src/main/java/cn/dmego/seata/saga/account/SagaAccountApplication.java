package cn.dmego.seata.saga.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.oas.annotations.EnableOpenApi;

/**
 * SagaAccountApplication
 *
 * @author dmego
 * @date 2021/3/31 10:45
 */
@SpringBootApplication
@EnableOpenApi
public class SagaAccountApplication {
    public static void main(String[] args) {
        SpringApplication.run(SagaAccountApplication.class, args);
    }
}
