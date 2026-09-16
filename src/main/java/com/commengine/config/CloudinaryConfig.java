package com.commengine.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(AppProperties props) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", props.getCloudinary().getCloudName(),
                "api_key", props.getCloudinary().getApiKey(),
                "api_secret", props.getCloudinary().getApiSecret(),
                "secure", true
        ));
    }
}
