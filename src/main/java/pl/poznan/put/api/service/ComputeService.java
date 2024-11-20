package pl.poznan.put.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.poznan.put.ConsensusMode;
import pl.poznan.put.api.dto.ComputeRequest;
import pl.poznan.put.api.dto.ComputeResponse;
import pl.poznan.put.api.model.Task;
import pl.poznan.put.api.model.TaskStatus;
import pl.poznan.put.api.repository.TaskRepository;

@Service
public class ComputeService {
    private static final Logger logger = LoggerFactory.getLogger(ComputeService.class);
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final TaskProcessorService taskProcessorService;

    @Autowired
    public ComputeService(
            TaskRepository taskRepository,
            ObjectMapper objectMapper,
            TaskProcessorService taskProcessorService) {
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.taskProcessorService = taskProcessorService;
    }

    public ComputeResponse submitComputation(ComputeRequest request) throws Exception {
        logger.info("Submitting new computation task with {} files", request.files().size());
        var task = new Task();
        task.setRequest(objectMapper.writeValueAsString(request));
        task.setStatus(TaskStatus.PENDING); // Explicitly set initial status
        task = taskRepository.save(task);
        var taskId = task.getId();

        // Schedule async processing without waiting
        taskProcessorService.processTaskAsync(taskId);

        return new ComputeResponse(taskId);
    }

    // Inne metody pozostają bez zmian
}
