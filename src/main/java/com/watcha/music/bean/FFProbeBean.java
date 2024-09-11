package com.watcha.music.bean;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegStream;
import net.bramp.ffmpeg.probe.FFmpegStream.CodecType;

@Slf4j
@Order(1)
@Component
public class FFProbeBean {

    @Value("${ffprobe.filepath}")
    private String ffprobeFilepath;

    @Autowired
    private ResourceLoader resourceLoader;

    private File ffprobeFile = null;

    private FFprobe analyzer = null;

    @PostConstruct
    public void initIt() {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = resourceLoader.getResource(ffprobeFilepath).getInputStream();

            ffprobeFile = File.createTempFile("ffprobe_", ".tmp",
                    new File(System.getProperty("user.dir") + File.separator + "music"));
            ffprobeFile.setExecutable(true);
            output = new FileOutputStream(ffprobeFile);
            output = new BufferedOutputStream(output);
            IOUtils.copy(input, output);
            analyzer = new FFprobe(ffprobeFile.getAbsolutePath());

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
        if (ffprobeFile != null) {
            ffprobeFile.delete();
        }
    }

    public FFprobe getAnalyzer() {
        if (analyzer == null) {
            this.initIt();
        }

        return analyzer;
    }

    public FFmpegStream getVideoStream(String mediaPath) {
        FFmpegStream result = null;
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> videoStream = list.stream()
                        .filter(stream -> CodecType.VIDEO.equals(stream.codec_type)).findFirst();
                if (videoStream.isPresent()) {
                    result = videoStream.get();

                } else {
                    log.warn("getVideoStream() is called but the media file does not contain video stream!");
                    throw new NoSuchElementException(
                            "getVideoStream() is called but the media file does not contain video stream!");
                }

            } else {
                log.error("getVideoStream() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getVideoStream() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getVideoStream() is called but cannot probe the media file!");
        }

        log.debug("getVideoStream() is called successfully! - " + result);
        return result;
    }

    public String getVideoCodecName(String mediaPath) {
        String result = "";
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> videoStream = list.stream()
                        .filter(stream -> CodecType.VIDEO.equals(stream.codec_type)).findFirst();
                if (videoStream.isPresent()) {
                    result = videoStream.get().codec_long_name;

                } else {
                    log.warn("getVideoCodecName() is called but the media file does not contain video stream!");
                    throw new NoSuchElementException(
                            "getVideoCodecName() is called but the media file does not contain video stream!");
                }

            } else {
                log.error("getVideoCodecName() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getVideoCodecName() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getVideoCodecName() is called but cannot probe the media file!");
        }

        log.debug("getVideoCodecName() is called successfully! - " + result);
        return result;
    }

    public int getVideoWidth(String mediaPath) {
        int result = -1;
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> videoStream = list.stream()
                        .filter(stream -> CodecType.VIDEO.equals(stream.codec_type)).findFirst();
                if (videoStream.isPresent()) {
                    result = videoStream.get().width;

                } else {
                    log.warn("getVideoWidth() is called but the media file does not contain video stream!");
                    throw new NoSuchElementException(
                            "getVideoWidth() is called but the media file does not contain video stream!");
                }

            } else {
                log.error("getVideoWidth() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getVideoWidth() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getVideoWidth() is called but cannot probe the media file!");
        }

        log.debug("getVideoWidth() is called successfully! - " + result);
        return result;
    }

    public int getVideoHeight(String mediaPath) {
        int result = -1;
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> videoStream = list.stream()
                        .filter(stream -> CodecType.VIDEO.equals(stream.codec_type)).findFirst();
                if (videoStream.isPresent()) {
                    result = videoStream.get().height;

                } else {
                    log.warn("getVideoHeight() is called but the media file does not contain video stream!");
                    throw new NoSuchElementException(
                            "getVideoHeight() is called but the media file does not contain video stream!");
                }

            } else {
                log.error("getVideoHeight() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getVideoHeight() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getVideoHeight() is called but cannot probe the media file!");
        }

        log.debug("getVideoHeight() is called successfully! - " + result);
        return result;
    }

    public String getVideoFrameRate(String mediaPath) {
        String result = "";
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> videoStream = list.stream()
                        .filter(stream -> CodecType.VIDEO.equals(stream.codec_type)).findFirst();
                if (videoStream.isPresent()) {
                    result = String.format("%.2f", videoStream.get().avg_frame_rate.doubleValue());

                } else {
                    log.warn("getVideoFrameRate() is called but the media file does not contain format!");
                    throw new NoSuchElementException(
                            "getVideoFrameRate() is called but the media file does not contain format!");
                }

            } else {
                log.error("getVideoFrameRate() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getVideoFrameRate() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getVideoFrameRate() is called but cannot probe the media file!");
        }

        log.debug("getVideoFrameRate() is called successfully! - " + result);
        return result;
    }

    public String getAudioCodecName(String mediaPath) {
        String result = "";
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> audioStream = list.stream()
                        .filter(stream -> CodecType.AUDIO.equals(stream.codec_type)).findFirst();
                if (audioStream.isPresent()) {
                    result = audioStream.get().codec_long_name;

                } else {
                    log.warn("getAudioCodecName() is called but the media file does not contain audio stream!");
                    throw new NoSuchElementException(
                            "getAudioCodecName() is called but the media file does not contain audio stream!");
                }

            } else {
                log.error("getAudioCodecName() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getAudioCodecName() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getAudioCodecName() is called but cannot probe the media file!");
        }

        log.debug("getAudioCodecName() is called successfully! - " + result);
        return result;
    }

    public int getAudioSampleRate(String mediaPath) {
        int result = -1;
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                List<FFmpegStream> list = analyzer.probe(mediaPath).getStreams();
                Optional<FFmpegStream> audioStream = list.stream()
                        .filter(stream -> CodecType.AUDIO.equals(stream.codec_type)).findFirst();
                if (audioStream.isPresent()) {
                    result = audioStream.get().sample_rate;

                } else {
                    log.warn("getAudioSampleRate() is called but the media file does not contain format!");
                    throw new NoSuchElementException(
                            "getAudioSampleRate() is called but the media file does not contain format!");
                }

            } else {
                log.error("getAudioSampleRate() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getAudioSampleRate() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getAudioSampleRate() is called but cannot probe the media file!");
        }

        log.debug("getAudioSampleRate() is called successfully! - " + result);
        return result;
    }

    public double getDuration(String mediaPath) {
        double result = 0.0;
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                Optional<FFmpegFormat> format = Optional.ofNullable(analyzer.probe(mediaPath).getFormat());
                if (format.isPresent()) {
                    result = format.get().duration;

                } else {
                    log.warn("getDuration() is called but the media file does not contain format!");
                    throw new NoSuchElementException(
                            "getDuration() is called but the media file does not contain format!");
                }

            } else {
                log.error("getDuration() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getDuration() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getDuration() is called but cannot probe the media file!");
        }

        log.debug("getDuration() is called successfully! - " + result);
        return result;
    }

    public int getProbeScore(String mediaPath) {
        int result = -1;
        try {
            if (Optional.ofNullable(mediaPath).isPresent()) {
                Optional<FFmpegFormat> format = Optional.ofNullable(analyzer.probe(mediaPath).getFormat());
                if (format.isPresent()) {
                    result = format.get().probe_score;

                } else {
                    log.warn("getProbeScore() is called but the media file does not contain format!");
                    throw new NoSuchElementException(
                            "getProbeScore() is called but the media file does not contain format!");
                }

            } else {
                log.error("getProbeScore() is called but the parameter mediaPath does not exist or cannot open!");
                throw new FileNotFoundException(
                        "getProbeScore() is called but the parameter mediaPath does not exist or cannot open!");
            }

        } catch (IOException ioe) {
            log.error("getProbeScore() is called but cannot probe the media file!");
        }

        log.debug("getProbeScore() is called successfully! - " + result);
        return result;
    }
}
