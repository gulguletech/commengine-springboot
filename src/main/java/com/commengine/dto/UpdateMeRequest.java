package com.commengine.dto;

import com.commengine.entity.Gender;
import jakarta.validation.constraints.Size;

public class UpdateMeRequest {
    @Size(min = 2, max = 50)
    private String name;

    @Size(max = 300)
    private String bio;

    private Gender gender;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
}
