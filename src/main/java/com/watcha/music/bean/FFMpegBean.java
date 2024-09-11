package com.watcha.music.bean;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;

@Slf4j
@Order(2)
@Component
public class FFMpegBean {
	
    @Value("${ffmpeg.filepath}")
    private String ffmpegFilepath;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private FFProbeBean ffprobe;

    private File ffmpegFile = null;

    private FFmpeg ffmpeg = null;

    private FFmpegExecutor executor = null;

    @PostConstruct
    public void initIt() {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = resourceLoader.getResource(ffmpegFilepath).getInputStream();
            ffmpegFile = File.createTempFile("ffmpeg_", ".tmp",
                    new File(System.getProperty("user.dir") + File.separator + "music"));
            ffmpegFile.setExecutable(true);
            output = new FileOutputStream(ffmpegFile);
            output = new BufferedOutputStream(output);
            IOUtils.copy(input, output);

        } catch (IOException ioe) {
            log.warn(ioe.getMessage());

        } finally { // Close streams
            if (input != null) {
                IOUtils.closeQuietly(input);
            }
            if (output != null) {
                IOUtils.closeQuietly(output);
            }
        }
    }

    @PreDestroy
    public void cleanUp() throws Exception {
        if (ffmpegFile != null) {
            ffmpegFile.delete();
        }
    }


	/**
     * 비디오 파일 경로를 입력 받아 모든 오디오 트랙을 병합한 후 모노 채널, 16000Hz로 변환된 오디오 파일 경로를 리턴합니다.
     * @param videoFilePath 비디오 파일의 경로
     * @return 변환된 오디오 파일의 경로
     * @throws IOException FFmpeg 작업 중 예외 발생 시
     */
    public String extractAudioTracksAndDownMixTo16kHzMono(String videoFilePath) throws IOException {
        // 비디오 파일 정보를 FFprobe로 가져옵니다.
        FFmpegProbeResult probeResult = ffprobe.getAnalyzer().probe(videoFilePath);
		
        // 변환된 오디오 파일 경로 설정
        String outputAudioFilePath = "_audio_mono_16kHz.wav";

		ffmpeg = new FFmpeg(ffmpegFile.getAbsolutePath());
        // FFmpegBuilder를 사용하여 모든 오디오 트랙을 병합하여 추출하고 변환합니다.
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(probeResult) // FFprobe로 가져온 비디오 파일 정보
				.overrideOutputFiles(true)
                .addOutput(outputAudioFilePath) // 출력 파일 경로 설정
                .setAudioChannels(1) // 모노 채널로 설정
                .setAudioSampleRate(16000) // 샘플 레이트 16000Hz로 설정
                .setAudioFilter("amerge=inputs=" + getAudioStreamCount(probeResult)) // 모든 오디오 트랙을 병합
                .setFormat("wav") // 출력 형식 wav로 설정
                .done();
		
        // FFmpeg를 실행하여 변환 작업을 수행합니다.
        executor = new FFmpegExecutor(ffmpeg, ffprobe.getAnalyzer());
        executor.createJob(builder).run();

        return outputAudioFilePath;
    }

	/**
     * 비디오 파일 경로를 입력 받아 모든 오디오 트랙을 병합한 후 모노 채널, 16000Hz로 변환된 오디오 파일 경로를 리턴합니다.
     * @param videoFilePath 비디오 파일의 경로
     * @return 변환된 오디오 파일의 경로
     * @throws IOException FFmpeg 작업 중 예외 발생 시
     */
    public String extractAudioTracksAndDownMixTo44_1kHzMono(String videoFilePath) throws IOException {
        // 비디오 파일 정보를 FFprobe로 가져옵니다.
        FFmpegProbeResult probeResult = ffprobe.getAnalyzer().probe(videoFilePath);
		
        // 변환된 오디오 파일 경로 설정
        String outputAudioFilePath = "_audio_mono_44_1kHz.wav";

		ffmpeg = new FFmpeg(ffmpegFile.getAbsolutePath());
        // FFmpegBuilder를 사용하여 모든 오디오 트랙을 병합하여 추출하고 변환합니다.
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(probeResult) // FFprobe로 가져온 비디오 파일 정보
				.overrideOutputFiles(true)
                .addOutput(outputAudioFilePath) // 출력 파일 경로 설정
                .setAudioChannels(1) // 모노 채널로 설정
                .setAudioSampleRate(44100) // 샘플 레이트 16000Hz로 설정
                .setAudioFilter("amerge=inputs=" + getAudioStreamCount(probeResult)) // 모든 오디오 트랙을 병합
                .setFormat("wav") // 출력 형식 wav로 설정
                .done();
		
        // FFmpeg를 실행하여 변환 작업을 수행합니다.
        executor = new FFmpegExecutor(ffmpeg, ffprobe.getAnalyzer());
        executor.createJob(builder).run();

        return outputAudioFilePath;
    }

	/**
     * 비디오 파일 경로를 입력 받아 모든 오디오 트랙을 병합한 후 16bit 모노 채널, 44100Hz로 변환된 오디오 파일 경로를 리턴합니다.
     * @param videoFilePath 비디오 파일의 경로
     * @return 변환된 오디오 파일의 경로
     * @throws IOException FFmpeg 작업 중 예외 발생 시
     */
    public byte[] convertToByteArray(String filePath, String start) throws IOException {
        // 비디오 파일 정보를 FFprobe로 가져옵니다.
        FFmpegProbeResult probeResult = ffprobe.getAnalyzer().probe(filePath);
		
        // 변환된 오디오 파일 경로 설정
        String outputFilePath = "_output_segment.raw";

		ffmpeg = new FFmpeg(ffmpegFile.getAbsolutePath());
        // FFmpegBuilder를 사용하여 모든 오디오 트랙을 병합하여 추출하고 변환합니다.
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(probeResult) // FFprobe로 가져온 비디오 파일 정보
				.overrideOutputFiles(true)
                .addOutput(outputFilePath) // 출력 파일 경로 설정
				.setAudioCodec("pcm_s16le")
				.setAudioChannels(1)
				.addExtraArgs("-ss", start)
				.addExtraArgs("-t", "5")
				.setFormat("s16le")
                .done();
		
        // FFmpeg를 실행하여 변환 작업을 수행합니다.
        executor = new FFmpegExecutor(ffmpeg, ffprobe.getAnalyzer());
        executor.createJob(builder).run();

		// Read the converted audio data from the temporary output file
        byte[] convertedBytes;
        try (FileInputStream fis = new FileInputStream(outputFilePath)) {
            convertedBytes = fis.readAllBytes();
        }

		// 임시 파일 삭제
		Files.deleteIfExists(Paths.get(outputFilePath));

        return convertedBytes;
    }


    /**
     * 비디오 파일에서 오디오 스트림의 수를 가져옵니다.
     * @param probeResult FFmpegProbeResult 객체
     * @return 오디오 스트림의 수
     */
    private int getAudioStreamCount(FFmpegProbeResult probeResult) {
        int audioStreamCount = 0;
        for (FFmpegStream stream : probeResult.getStreams()) {
            if (stream.codec_type == FFmpegStream.CodecType.AUDIO) {
                audioStreamCount++;
            }
        }
        return audioStreamCount;
    }

    public void transcodeFromXAVC300IntraToProRes422HQ(String inputPath, String outputPath) {
        try {
            ffmpeg = new FFmpeg(ffmpegFile.getAbsolutePath());
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(inputPath)
                    .overrideOutputFiles(true)
                    .addOutput(outputPath)
                    .setVideoResolution(1920, 1080)
                    .setVideoFrameRate(FFmpeg.FPS_29_97)
                    .setVideoPixelFormat("yuv422p10le")
                    .addExtraArgs("-c:v", "prores")
                    .addExtraArgs("-profile", "3")
                    .addExtraArgs("-c:a", "pcm_s16le")
                    .done();
            System.out.println(builder.toString());

            executor = new FFmpegExecutor(ffmpeg, ffprobe.getAnalyzer());
            executor.createJob(builder).run();
            // executor.createTwoPassJob(builder).run();

        } catch (IOException ioe) {
            log.warn(ioe.getMessage());
        }
    }

    public void transcodeFromXAVC300IntraToDNxHD(String inputPath, String outputPath) {
        try {
            ffmpeg = new FFmpeg(ffmpegFile.getAbsolutePath());
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(inputPath)
                    .overrideOutputFiles(true)
                    .addOutput(outputPath)
                    .setVideoResolution(1920, 1080)
                    .setVideoFrameRate(FFmpeg.FPS_29_97)
                    .setVideoPixelFormat("yuv422p10le")
                    .addExtraArgs("-c:v", "dnxhd")
                    .addExtraArgs("-profile:v", "dnxhr_hqx")
                    .addExtraArgs("-c:a", "pcm_s16le")
                    .done();
            System.out.println(builder.toString());

            executor = new FFmpegExecutor(ffmpeg, ffprobe.getAnalyzer());
            executor.createJob(builder).run();
            // executor.createTwoPassJob(builder).run();

        } catch (IOException ioe) {
            log.warn(ioe.getMessage());
        }
    }

    /**
     * 건내받은 영상을 출력 경로에 프로레스422 프록시로 인코딩한다.
     * 원본 영상의 해상도, 프레임레이트 및 오디오 트랙은 동일하게 유지한다.
     * 
     * @param inputPath
     * @param outputPath
     */
    public void transcodeToProRes422Proxy(String inputPath, String outputPath) {
        try {
            ffmpeg = new FFmpeg(ffmpegFile.getAbsolutePath());
            FFmpegBuilder builder = new FFmpegBuilder()
                    .addExtraArgs("-hwaccel", "cuvid")
                    .setInput(inputPath)
                    .overrideOutputFiles(true)
                    .addOutput(outputPath)
                    .setVideoResolution(1920, 1080)
                    .setVideoFrameRate(FFmpeg.FPS_23_976)
                    .setVideoPixelFormat("yuv422p10le")
                    .addExtraArgs("-c:v", "prores")
                    .addExtraArgs("-profile", "0")
                    .addExtraArgs("-qscale", "4")
                    .addExtraArgs("-map", "0:v")
                    .addExtraArgs("-map", "0:a")
                    .done();
            System.out.println(builder.toString());

            executor = new FFmpegExecutor(ffmpeg, ffprobe.getAnalyzer());
            executor.createJob(builder).run();
            // executor.createTwoPassJob(builder).run();

        } catch (IOException ioe) {
            log.warn(ioe.getMessage());
        }
    }
}
