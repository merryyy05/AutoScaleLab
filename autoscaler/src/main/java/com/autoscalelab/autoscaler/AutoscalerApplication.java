package com.autoscalelab.autoscaler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutoscalerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutoscalerApplication.class, args);
	}

}
