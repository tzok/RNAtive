package pl.poznan.put.api.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import pl.poznan.put.api.model.Task;

public interface TaskRepository extends JpaRepository<Task, String> {
  @Transactional
  @Modifying
  @Query("DELETE FROM Task t WHERE t.createdAt < :cutoff")
  void deleteTasksOlderThan(Instant cutoff);
}
