package com.hoang.worknest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hoang.worknest.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTaskId(Long taskId);
    Optional<Attachment> findByIdAndTaskId(Long id, Long taskId);
    Optional<Attachment> findByObjectKey(String objectKey);
}
