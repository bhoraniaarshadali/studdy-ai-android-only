package com.arshad.studdy_app_android_only.ui.teacher.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.arshad.studdy_app_android_only.R;
import com.arshad.studdy_app_android_only.ui.teacher.create.CreateExamActivity;
import com.arshad.studdy_app_android_only.ui.teacher.create.PaperGeneratorActivity;
import com.arshad.studdy_app_android_only.ui.teacher.papers.PaperListActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CreateNewBottomSheet extends BottomSheetDialogFragment {

    public static CreateNewBottomSheet newInstance() {
        return new CreateNewBottomSheet();
    }

    @Override
    public int getTheme() {
        return R.style.Studdy_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_create_new, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_create_mcq).setOnClickListener(v -> {
            dismiss();
            startActivity(new Intent(requireContext(), CreateExamActivity.class));
        });

        view.findViewById(R.id.btn_generate_paper).setOnClickListener(v -> {
            dismiss();
            startActivity(new Intent(requireContext(), PaperGeneratorActivity.class));
        });
    }
}
