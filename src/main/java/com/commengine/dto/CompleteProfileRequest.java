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

    @Size(max = 30)
    private String language;

    /** All languages the user speaks. Preferred over `language` when sent. */
    @Size(max = 20)
    private java.util.List<@Size(max = 30) String> languages;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public java.util.List<String> getLanguages() { return languages; }
    public void setLanguages(java.util.List<String> languages) { this.languages = languages; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
