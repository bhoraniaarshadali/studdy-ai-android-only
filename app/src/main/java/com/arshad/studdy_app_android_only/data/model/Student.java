package com.arshad.studdy_app_android_only.data.model;

import com.google.gson.annotations.SerializedName;

/** Maps to a row in {@code public.students}. */
public class Student {

    @SerializedName("id")
    public String id;

    @SerializedName("enrollment_number")
    public String enrollmentNumber;

    @SerializedName("name")
    public String name;

    @SerializedName("created_at")
    public String createdAt;

    public Student() {}

    public Student(String enrollmentNumber, String name) {
        this.enrollmentNumber = enrollmentNumber;
        this.name = name;
    }
}
