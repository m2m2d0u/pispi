package ci.sycapay.pispi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PispiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PispiApplication.class, args);
	}

}
