package com.watcha.music;

import java.sql.SQLException;

import org.h2.tools.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableJpaAuditing
@EntityScan(basePackageClasses = { Jsr310JpaConverters.class }, // basePackageClasses에 지정
		basePackages = { "com.watcha.music.domain" }) // basePackages도 추가로 반드시 지정해줘야 한다
@SpringBootApplication
public class BoundaryDetector {

	public static void main(String[] args) {
		SpringApplication.run(BoundaryDetector.class, args);
	}

	/**
	 * Start internal H2 server so we can query the DB from IDE
	 *
	 * @return H2 Server instance
	 * @throws SQLException
	 */
	@Bean(initMethod = "start", destroyMethod = "stop")
	public Server h2Server() throws SQLException {
		return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9099");
	}
}
