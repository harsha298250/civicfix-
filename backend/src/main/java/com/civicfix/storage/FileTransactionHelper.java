package com.civicfix.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * FileTransactionHelper
 *
 * Coordinates two-phase commit consistency between the local filesystem and PostgreSQL database transactions.
 * Registers Spring TransactionSynchronization callbacks to ensure that physical files written to disk
 * during an uncommitted database transaction are automatically deleted if the transaction rolls back.
 */
@Component
public class FileTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(FileTransactionHelper.class);

    private final StorageService storageService;

    @Autowired
    public FileTransactionHelper(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Registers a single staged physical file for automatic deletion if the surrounding database transaction rolls back.
     */
    public void registerRollbackCleanup(String filename, String subDirectory) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("[FILE_TX] No active transaction. Rollback synchronization skipped for {}/{}", subDirectory, filename);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        log.info("[FILE_TX] Transaction rolled back. Deleting orphaned staged file: {}/{}", subDirectory, filename);
                        storageService.deleteFile(filename, subDirectory);
                    } catch (Exception ex) {
                        log.warn("[FILE_TX] Failed to delete orphaned file {}/{} after rollback: {}",
                                subDirectory, filename, ex.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Registers multiple staged physical files for automatic deletion if the surrounding database transaction rolls back.
     */
    public void registerRollbackCleanup(List<String> filenames, String subDirectory) {
        if (filenames == null || filenames.isEmpty()) {
            return;
        }
        for (String filename : filenames) {
            registerRollbackCleanup(filename, subDirectory);
        }
    }
}
