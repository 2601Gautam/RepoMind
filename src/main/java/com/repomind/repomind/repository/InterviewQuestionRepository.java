package com.repomind.repomind.repository;

import com.repomind.repomind.model.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {
    List<InterviewQuestion> findBySessionIdOrderByQuestionOrderAsc(UUID sessionId);
}
