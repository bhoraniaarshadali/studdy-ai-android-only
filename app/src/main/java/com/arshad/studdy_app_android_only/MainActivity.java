package com.arshad.studdy_app_android_only;

import android.content.Intent;
import android.os.Bundle;

import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.auth.TeacherAuthActivity;
import com.arshad.studdy_app_android_only.ui.home.RoleSelectionActivity;
import com.arshad.studdy_app_android_only.ui.student.dashboard.StudentDashboardActivity;
import com.arshad.studdy_app_android_only.ui.teacher.dashboard.TeacherDashboardActivity;
import com.arshad.studdy_app_android_only.util.SessionManager;

/**
 * Session router. This Activity has no layout — it immediately inspects
 * the persisted sessions and forwards to the correct screen:
 *
 *   1. Teacher JWT present  → TeacherDashboardActivity
 *   2. Student enrollment present → StudentDashboardActivity
 *   3. Neither             → RoleSelectionActivity
 *
 * Per spec: "Startup bypasses this screen when a teacher Auth session
 * exists; teacher is checked first."
 */
public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = SessionManager.getInstance(this);

        Intent intent;
        if (session.hasTeacherSession()) {
            intent = new Intent(this, TeacherDashboardActivity.class);
        } else if (session.hasStudentSession()) {
            intent = new Intent(this, StudentDashboardActivity.class);
        } else {
            intent = new Intent(this, RoleSelectionActivity.class);
        }

        startActivity(intent);
        finish(); // remove MainActivity from the back stack
    }
}