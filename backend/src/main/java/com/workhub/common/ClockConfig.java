package com.workhub.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** The zone in which "today" and sprint days are counted; timestamps themselves stay in UTC. */
	@Bean
	ZoneId appZone(@Value("${workhub.time-zone}") String zone) {
		return ZoneId.of(zone);
	}

}
