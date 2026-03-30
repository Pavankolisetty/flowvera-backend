package com.workly.repo;

import com.workly.entity.TaskProgressHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskProgressHistoryRepository extends JpaRepository<TaskProgressHistory, Long> {
    List<TaskProgressHistory> findByTaskAssignmentIdOrderByRecordedAtAsc(Long taskAssignmentId);
}
