package pl.poznan.put.api;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {
  public static void main(String[] args) {
    SpringApplicationBuilder builder = new SpringApplicationBuilder(Application.class);

    String appMode = System.getenv("APP_MODE");
    if ("cli".equalsIgnoreCase(appMode)) {
      // Run in CLI mode
      builder.web(WebApplicationType.NONE);
    }

    builder.run(args);
  }
}
