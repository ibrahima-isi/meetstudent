package com.bowe.meetstudent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class RoleConverter implements Converter<String, Role> {

    private static final Logger logger = LoggerFactory.getLogger(RoleConverter.class);

    @Override
    public Role convert(String source) {
        logger.info("Attempting to convert role: {}", source);
        if (source == null) {
            logger.warn("Received null source for role conversion");
            return null;
        }
        try {
            Role convertedRole = Role.valueOf(source.toUpperCase());
            logger.info("Successfully converted to role: {}", convertedRole);
            return convertedRole;
        } catch (IllegalArgumentException e) {
            logger.error("Failed to convert role: {}", source, e);
            return null; // or throw an exception if you prefer
        }
    }
}