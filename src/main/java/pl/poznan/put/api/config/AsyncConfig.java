package pl.poznan.put.api.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    int coreCount = Runtime.getRuntime().availableProcessors();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(coreCount);
    executor.setMaxPoolSize(coreCount * 2);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("ComputeService-");
    executor.initialize();
    return executor;
  }
}
