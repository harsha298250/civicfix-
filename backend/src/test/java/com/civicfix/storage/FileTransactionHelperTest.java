package com.civicfix.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileTransactionHelperTest {

    private StorageService storageService;
    private FileTransactionHelper helper;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        helper = new FileTransactionHelper(storageService);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Rollback synchronization automatically deletes physical file from storage when transaction rolls back")
    void testRollbackDeletesStagedFile() {
        helper.registerRollbackCleanup("photo_uuid_123.jpg", "complaints/test-id");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size(), "A synchronization callback should be registered");

        TransactionSynchronization sync = synchronizations.get(0);
        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storageService).deleteFile("photo_uuid_123.jpg", "complaints/test-id");
    }

    @Test
    @DisplayName("Committed transaction retains physical file without deletion")
    void testCommitRetainsStagedFile() {
        helper.registerRollbackCleanup("photo_uuid_123.jpg", "complaints/test-id");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        TransactionSynchronization sync = synchronizations.get(0);
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(storageService, never()).deleteFile(anyString(), anyString());
    }

    @Test
    @DisplayName("Multiple files registered for rollback are all cleaned up on rollback")
    void testMultipleFilesRollbackCleanup() {
        helper.registerRollbackCleanup(List.of("f1.jpg", "f2.jpg", "f3.jpg"), "complaints/batch-test");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(3, synchronizations.size());

        for (TransactionSynchronization sync : synchronizations) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(storageService).deleteFile("f1.jpg", "complaints/batch-test");
        verify(storageService).deleteFile("f2.jpg", "complaints/batch-test");
        verify(storageService).deleteFile("f3.jpg", "complaints/batch-test");
    }
}
