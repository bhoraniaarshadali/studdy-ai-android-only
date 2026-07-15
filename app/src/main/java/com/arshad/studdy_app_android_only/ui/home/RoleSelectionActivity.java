package com.arshad.studdy_app_android_only.ui.home;

import android.content.Intent;
import android.os.Bundle;

import com.arshad.studdy_app_android_only.databinding.ActivityRoleSelectionBinding;
import com.arshad.studdy_app_android_only.ui.BaseActivity;
import com.arshad.studdy_app_android_only.ui.auth.StudentAuthActivity;
import com.arshad.studdy_app_android_only.ui.auth.TeacherAuthActivity;

/**
 * Role selection / home screen.
 *
 * Displays two cards — Teacher and Student — matching the spec's initial screen.
 * This activity is only reached when there is no saved session (handled by MainActivity).
 */
public class RoleSelectionActivity extends BaseActivity {

    private ActivityRoleSelectionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoleSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.cardTeacher.setOnClickListener(v ->
                startActivity(new Intent(this, TeacherAuthActivity.class))
        );

        binding.cardStudent.setOnClickListener(v ->
                startActivity(new Intent(this, StudentAuthActivity.class))
        );
    }
}
