package pl.poznan.put.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {
  public static void main(String[] args) {
    if (args.length > 0) {
      // Run in CLI mode if arguments are provided
      new SpringApplicationBuilder(Application.class)
          .web(WebApplicationType.NONE)
          .run(args);
    } else {
      // Run in web mode if no arguments
      SpringApplication.run(Application.class, args);
    }
  }
}
