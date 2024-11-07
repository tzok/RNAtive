package pl.poznan.put.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.poznan.put.api.model.Task;

public interface TaskRepository extends JpaRepository<Task, String> {
}
