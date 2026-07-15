package com.arshad.studdy_app_android_only.data.local.entity;

/**
 * Synchronization status for local exam attempts.
 */
public enum SyncStatus {
    /** Attempt answers are fully synchronized with the server. */
    SYNCED,
    
    /** Attempt has pending answer updates that need synchronization. */
    PENDING,
    
    /** Exam submission has been initiated locally, pending final server response. */
    SUBMIT_PENDING,
    
    /** Submission confirmed and finalized by the server. */
    SUBMITTED_CONFIRMED,
    
    /** Submission rejected by the server because the exam is closed or expired. */
    CLOSED_REJECTED
}
