package com.watcha.music.service;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ShazamAPIService {
	
	@Value("${shazam.api.key}")
	private String apiKey;

	private WebClient webClient = null;

    public ShazamAPIService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://shazam.p.rapidapi.com").build();
    }

    public String detectSong(byte[] audioData) {
        return this.webClient.post()
                .uri("/songs/v2/detect")  // API 경로
                .header("X-RapidAPI-Key", apiKey)  // RapidAPI 키를 입력
                .header("X-RapidAPI-Host", "shazam.p.rapidapi.com")
                .contentType(MediaType.TEXT_PLAIN)  // 오디오 데이터의 타입을 설정
                .bodyValue(Base64.getEncoder().encodeToString(audioData))  // POST로 보낼 데이터 (base64 문자열로 전송)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
