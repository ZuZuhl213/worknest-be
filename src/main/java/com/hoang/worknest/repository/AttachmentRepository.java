package com.hoang.worknest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hoang.worknest.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    interface StorageObject {
        String getBucketName();
        String getObjectKey();
    }

    List<Attachment> findByTaskId(Long taskId);
    Optional<Attachment> findByIdAndTaskId(Long id, Long taskId);
    Optional<Attachment> findByObjectKey(String objectKey);

    @Query("select a.bucketName as bucketName, a.objectKey as objectKey from Attachment a where a.task.id = :taskId")
    List<StorageObject> findStorageObjectsByTaskId(@Param("taskId") Long taskId);

    @Query("select a.bucketName as bucketName, a.objectKey as objectKey from Attachment a where a.task.project.id = :projectId")
    List<StorageObject> findStorageObjectsByProjectId(@Param("projectId") Long projectId);

    @Query("select a.bucketName as bucketName, a.objectKey as objectKey from Attachment a where a.task.project.workspace.id = :workspaceId")
    List<StorageObject> findStorageObjectsByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
