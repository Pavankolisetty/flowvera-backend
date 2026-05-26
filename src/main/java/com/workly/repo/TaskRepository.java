package com.workly.repo;


import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.workly.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByCreatedBy(String createdBy);
}
