package com.commengine.dto;

import com.commengine.entity.Gender;
import jakarta.validation.constraints.*;

public class CompleteProfileRequest {
    @NotBlank @Size(min = 2, max = 50)
    private String name;

    @NotNull @Min(value = 18, message = "You must be 18 or older to use this app") @Max(100)
    private Integer age;

    private Gender gender;

    @Size(max = 300)
    private String bio;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
