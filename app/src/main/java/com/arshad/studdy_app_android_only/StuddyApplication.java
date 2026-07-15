package com.arshad.studdy_app_android_only;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.arshad.studdy_app_android_only.work.SyncExamAttemptWorker;

/**
 * Custom application class. Registers global network callback to kick off background sync workers.
 */
public class StuddyApplication extends Application {

    private static final String TAG = "StuddyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application: onCreate: Initializing StuddyApplication");
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            try {
                connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        Log.d(TAG, "NetworkCallback: Internet became available. Enqueuing sync worker.");
                        triggerSyncWorker();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to register network callback", e);
            }
        }
    }

    private void triggerSyncWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SyncExamAttemptWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(
                "SyncExamAttemptWork",
                ExistingWorkPolicy.REPLACE,
                workRequest
        );
    }
}
