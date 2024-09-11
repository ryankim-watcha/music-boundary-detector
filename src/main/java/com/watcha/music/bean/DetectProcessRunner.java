package com.watcha.music.bean;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.tensorflow.Result;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.StdArrays;
import org.tensorflow.ndarray.buffer.FloatDataBuffer;
import org.tensorflow.types.TFloat32;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jlibrosa.audio.JLibrosa;
import com.jlibrosa.audio.exception.FileFormatNotSupportedException;
import com.jlibrosa.audio.wavFile.WavFileException;
import com.watcha.music.domain.MusicBoundaryEntity;
import com.watcha.music.repository.MusicBoundaryRepository;
import com.watcha.music.service.ShazamAPIService;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Order(3)
@Component
public class DetectProcessRunner implements ApplicationRunner, DisposableBean {

	@Autowired
	private FFMpegBean ffmpegBean;

	@Autowired
	private MusicBoundaryRepository musicBoundaryRepository;

	@Autowired
	private ShazamAPIService shazamAPIService;

	@Override
	public void run(ApplicationArguments args)
			throws IOException, InterruptedException, WavFileException, FileFormatNotSupportedException, ParseException {
		log.info("DetectProcessRunner - the music boundary detector will be started soon!");
		
		String videoFilePath = "/Users/ryankim/Downloads/팟제너레이션.mp4";	// 분석할 비디오 파일 경로

		// 오디오 추출 & 모노 16kHz 변환
		String audioFilePath = ffmpegBean.extractAudioTracksAndDownMixTo16kHzMono(videoFilePath);
		
		// 음악 경계 감지 후 DB 저장
		List<MusicBoundaryEntity> list = detectMusicBoundary(audioFilePath);
		
		// 음악 경계 감지 완료 후 16kHz 오디오 파일 삭제
		Files.deleteIfExists(Paths.get(audioFilePath));
		
		// 44.1kHz 오디오 추출 (shazam API 호출을 위해)
		audioFilePath = ffmpegBean.extractAudioTracksAndDownMixTo44_1kHzMono(videoFilePath);
		
		for (MusicBoundaryEntity entity : list) {
			try {
				byte[] audioData = ffmpegBean.convertToByteArray(audioFilePath, entity.getMusicStart());
				// 아래에 음악 구간을 외부 API 호출해서 어떤 곡인지 확인 후 DB 저장 로직 추가
				String jsonResponse = shazamAPIService.detectSong(audioData);
				JsonElement jsonElement = JsonParser.parseString(jsonResponse);
				JsonObject jsonObject = jsonElement.getAsJsonObject();
				JsonObject track = jsonObject.getAsJsonObject("track");
				String title = "", subtitle = "";
				if (track != null) {	// track 필드가 존재하는지 확인
					title = track.has("title") ? track.get("title").getAsString() : "";
					subtitle = track.has("subtitle") ? track.get("subtitle").getAsString() : "";
				// shazam API에서 음악 정보를 찾지 못한 경우, 5초 슬라이딩 해서 한 번 더 시도
				} else {
					audioData = ffmpegBean.convertToByteArray(audioFilePath, formatTime(entity.getMusicStartSeconds() + 5));
					// 아래에 음악 구간을 외부 API 호출해서 어떤 곡인지 확인 후 DB 저장 로직 추가
					jsonResponse = shazamAPIService.detectSong(audioData);
					jsonElement = JsonParser.parseString(jsonResponse);
					jsonObject = jsonElement.getAsJsonObject();
					track = jsonObject.getAsJsonObject("track");
					if (track != null) {	// track 필드가 존재하는지 확인
						title = track.has("title") ? track.get("title").getAsString() : "";
						subtitle = track.has("subtitle") ? track.get("subtitle").getAsString() : "";
					} else {
						log.warn("No track information found for the music boundary entity: {}", entity);
					}
				}
				// DB에 해당 음악 구간에 대해 shazam에서 조회한 정보 저장
				entity.setTitle(title);
				entity.setSubtitle(subtitle);
				musicBoundaryRepository.saveAndFlush(entity);

			} catch (IOException ex) {
			}
		}

		// 샤잠 조회 후 44.1kHz 오디오 파일 삭제
		Files.deleteIfExists(Paths.get(audioFilePath));

		log.info("DetectProcessRunner - the music boundary detection process has been completed!");
	}

	@Override
	public void destroy() {	// destroy action
		log.info("DetectProcessRunner - the music boundary detector will be shutdown soon!");
	}

	/**
	 * 오디오 DSP & YAMNet 처리를 위한 AudioDispatcher 생성
	 * @param audioFilePath
	 * @return
	 * @throws Exception
	 */
	private AudioDispatcher getAudioDispatcher(String audioFilePath)
			throws IOException, WavFileException, FileFormatNotSupportedException {
		// pre-trainned CNN 모델의 일종인 YAMNet 모델과 레이블이 담겨 있는 경로 디렉토리
		String modelPath = "/Users/ryankim/IdeaProjects/yamnet";

		// 대소문자 구분 없는(?i) 음악 레이블 정규식 패턴
		String musicLablePattern = "(?i).*(music|instrument|orchestra|rock|funk|sing|song|metal|" 
									+ "blues|jazz|disco|grunge|guitar|banjo|harmonica|accordion|sitar|"
									+ "mandolin|ukulele|piano|harpsichord|drum|timpani|percussion|cymbal|"
									+ "hi-hat|maraca|tambourine|marimba|xylophone|trumpet|trombone|horn|"
									+ "violin|cello|string|flute|saxophone|clarinet|organ|synthesizer|"
									+ "raggae|country|opera|choir|lullaby|A capella|harmonic).*";

		// YAMNet에 추론하고 각 초 시간 단위를 인덱스화 하여 레이블들을 순차적으로 담은 리스트
		List<String> yamnetProbabilities = predictYAMNetProbabilities(audioFilePath, modelPath);
		
		// 오디오 처리를 위한 AudioDispatcher 생성
		int sampleRate = 16000;	// YAMNet 스펙: 16kHz, 1초 미만 단위 모노 오디오
		int bufferSize = 1024;
		AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(audioFilePath, sampleRate, bufferSize, 512);

	   // 멜 프리컨시 캡스트럼 계수 : 20~25ms 오디오 프레임 별로 고속 푸리에 변환 후,
		// 저주파 보다 고주파에 민감한 인간의 청각을 모델링한 멜-스케일화 한 스펙트럼에서
		// 하모닉 및 음악적 특징을 추출하여 음성 신호를 특징화하는데 사용
		MFCC mfcc = new MFCC(bufferSize, sampleRate, 13, 20, 255, 3000);

		// HighPass 필터로 300Hz이하 저주파 대역 남자 여자 대화를 걸러냄
		HighPass highPassFilter = new HighPass(255, sampleRate); // M: 85Hz ~180Hz, F: 165Hz ~ 255Hz
		
		// 남자/여자 목소리 대역을 깎아내고, MFCC 적용 (순서 주의)
		dispatcher.addAudioProcessor(highPassFilter);	// 255Hz 이하 저주파 대역 제거위해 하이패스 필터 걸고
		dispatcher.addAudioProcessor(mfcc);	// MFCC 적용

		AudioProcessor musicBoundaryDetectProcessor = new AudioProcessor() {
			boolean isMusicSegment = false;
			double musicStartTime = 0;
			double potentialMusicEndTime = 0;
			final double segmentMergeThreshold = 0.5; // 병합을 위한 시간 임계값 (0.5초)
			final double minMusicSegmentLength = 6; // 최소 음악 구간 길이 (6초)
			int consecutiveFrames = 0;
			final int minConsecutiveFrames = 4;

			@Override
			public boolean process(AudioEvent audioEvent) {
				double time = audioEvent.getTimeStamp();
				// YAMNet 결과 중 음악 레이블이 있는지 확인
				boolean isYamn = Pattern.compile(musicLablePattern).matcher(yamnetProbabilities.get((int) time + 1)).find();
				
				// MFCC 계산
				float[] mfccFeatures = mfcc.getMFCC();
				double mfccSum = 0.0;
				for (float feature : mfccFeatures) {
					mfccSum += feature;
				}
				double mfccAverage = mfccSum / mfccFeatures.length;
				
				// 음악 구간 감지 기준: MFCC + YAMNet
				if (mfccAverage > 0.3 || isYamn) {
					consecutiveFrames++;
					if (consecutiveFrames >= minConsecutiveFrames) {
						if (!isMusicSegment) {
							isMusicSegment = true;
							musicStartTime = time;
							potentialMusicEndTime = 0; // 초기화
						}
					}
				} else {
					if (isMusicSegment) {
						if (potentialMusicEndTime == 0) {
							potentialMusicEndTime = time;
						}

						// 일정 시간 내에 음악 구간이 끝나지 않으면 병합
						if (time - potentialMusicEndTime <= segmentMergeThreshold) {
							// 계속 음악 구간으로 간주
							consecutiveFrames = minConsecutiveFrames;
						} else {
							// 음악 구간이 충분히 끝났다고 판단
							double musicEndTime = potentialMusicEndTime;
							if (musicEndTime - musicStartTime >= minMusicSegmentLength) {
								MusicBoundaryEntity musicBoundaryEntity = new MusicBoundaryEntity();
								musicBoundaryEntity.setMusicStart(formatTime(musicStartTime));
								musicBoundaryEntity.setMusicEnd(formatTime(musicEndTime));
								musicBoundaryRepository.saveAndFlush(musicBoundaryEntity);
							}
							isMusicSegment = false;
							potentialMusicEndTime = 0; // 초기화
						}
					} else {
						potentialMusicEndTime = 0; // 초기화
						consecutiveFrames = 0;
					}
				}
				
				return true;
			}

			@Override
			public void processingFinished() {	// 처리 완료 후의 작업들
			}
		};

		dispatcher.addAudioProcessor(musicBoundaryDetectProcessor);
		return dispatcher;
	}

	/**
	 * 오디오 파일에서 음악 경계를 감지하고 DB에 저장 (using YAMNet Only)
	 * @param audioFilePath
	 * @return
	 * @throws IOException
	 * @throws WavFileException
	 * @throws FileFormatNotSupportedException
	 */
	private List<MusicBoundaryEntity> detectMusicBoundary(String audioFilePath) 
			throws IOException, WavFileException, FileFormatNotSupportedException {
		// pre-trainned CNN 모델의 일종인 YAMNet 모델과 레이블이 담겨 있는 경로 디렉토리
		String modelPath = "/Users/ryankim/IdeaProjects/yamnet";

		// 대소문자 구분 없는(?i) 음악 레이블 정규식 패턴
		String musicLablePattern = "(?i).*(music|instrument|orchestra|rock|funk|sing|song|metal|" 
									+ "blues|jazz|disco|grunge|guitar|banjo|harmonica|accordion|sitar|"
									+ "mandolin|ukulele|piano|harpsichord|drum|timpani|percussion|cymbal|"
									+ "hi-hat|maraca|tambourine|marimba|xylophone|trumpet|trombone|horn|"
									+ "violin|cello|string|flute|saxophone|clarinet|organ|synthesizer|"
									+ "raggae|country|opera|choir|lullaby|A capella|harmonic).*";
		int startTime = -1;
		int endTime = -1;
		int gapCount = 0;
		List<MusicBoundaryEntity> musicBoundaryList = new ArrayList<>();
		// YAMNet에 추론하고 각 초 시간 단위를 인덱스화 하여 레이블들을 순차적으로 담은 리스트
		List<String> yamnetProbabilities = predictYAMNetProbabilities(audioFilePath, modelPath);
		for(int time = 0; time < yamnetProbabilities.size(); time++) {
			boolean isMusic = Pattern.compile(musicLablePattern).matcher(yamnetProbabilities.get(time)).find();
			if (isMusic) {
				if (startTime == -1) { // 음악이 시작되는 시간 기록
					startTime = time;
				} else {
					endTime = time; // 음악이 계속 재생 중인 경우
				}
			} else {
				if (gapCount < 1) {	// 1초 이내의 빈 음악 구간은 무시
					gapCount++;
				} else {
					if (startTime != -1 && endTime - startTime > 9) {	// 10초 이상 음악 구간만 저장
						MusicBoundaryEntity entity = new MusicBoundaryEntity();
						entity.setAudioFilePath(audioFilePath);
						entity.setMusicStart(formatTime(startTime));
						entity.setMusicEnd(formatTime(endTime));
						entity.setMusicStartSeconds(startTime);
						entity.setMusicEndSeconds(endTime);
						musicBoundaryList.add(entity);
					}
					startTime = -1;	// 초기화
					endTime = -1;
					gapCount = 0;
				}
			}

			if (time == yamnetProbabilities.size() - 1) {
				if (startTime != -1 && endTime - startTime > 9) {	// 10초 이상 음악 구간만 저장
					MusicBoundaryEntity entity = new MusicBoundaryEntity();
					entity.setAudioFilePath(audioFilePath);
					entity.setMusicStart(formatTime(startTime));
					entity.setMusicEnd(formatTime(endTime));
					entity.setMusicStartSeconds(startTime);
					entity.setMusicEndSeconds(endTime);
					musicBoundaryList.add(entity);
				}
			}
		}
		
		return musicBoundaryRepository.saveAllAndFlush(musicBoundaryList);
	}

	/**
	 * 초 단위 시간을 받아서 HH:mm:ss 형식으로 변환
	 * @param seconds
	 * @return
	 */
	private String formatTime(double seconds) {
		int millis = (int) (seconds * 1000);
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("KST"));
		return sdf.format(new Date(millis));
	}
	
	/**
	 * YAMNet 모델을 사용하여 오디오 파일에서 클래스 레이블을 추론해 '초'를 인덱스로 가지는 리스트로 반환
	 * @param audioFilePath
	 * @param modelPath
	 * @return
	 */
	private List<String> predictYAMNetProbabilities(String audioFilePath, String modelPath)
			throws IOException, WavFileException, FileFormatNotSupportedException {
		// 각 초를 인덱스로 가지는 YAMNet 레이블 리스트
		List<String> timeIndexedLabelList = new ArrayList<>();
		
		// 웨이브폼 추출
		List<float[]> waveformList = extractWaveform(audioFilePath, 16000, -1);
		
		// YAMNet TensorFlow pre-trainned 모델 로드
		try(SavedModelBundle model = SavedModelBundle.load(modelPath, "serve")) {
			// 클래스 레이블 로드
			String classLabelPath = modelPath + "/assets/" + model.metaGraphDef().getAssetFileDef(0).getFilename();
			Map<Integer, String> classMap = loadClassMap(classLabelPath);
			
			// YAMNet Inference 실행
			Session session = model.session();
			for(float[] waveform : waveformList) {
				try (Tensor tensor = TFloat32.tensorOf(StdArrays.ndCopyOf(waveform))) {
					
					// YAMNet 모델에 입력 텐서(feed)와 출력 텐서(fetch) 지정
					Session.Runner runner = session.runner()
						.feed("serving_default_waveform:0", tensor) // 입력 텐서 이름 사용
						.fetch("StatefulPartitionedCall:0")  // 첫 번째 출력 텐서
						.fetch("StatefulPartitionedCall:1")  // 두 번째 출력 텐서
						.fetch("StatefulPartitionedCall:2"); // 세 번째 출력 텐서
				
					// 텐서플로우 세션 실행하여 prediction 결과 리스트에 저장해 반환
					try (Result result = runner.run()) {
						try (Tensor outputTensor = result.get(0)) {
							FloatDataBuffer floatDataBuffer = outputTensor.asRawTensor().data().asFloats();
							long[] shapeArray = outputTensor.shape().asArray();
							int numClasses = (int) shapeArray[1];
							float[] probabilities = new float[numClasses];
							floatDataBuffer.read(probabilities);
							
							// 1, 2, 3위의 확률을 가진 레이블 결합 스트링
							timeIndexedLabelList.add(getPossibleLabels(classMap, probabilities));
						}
					}
				}
			}
		}

		return timeIndexedLabelList;
	}
    
	/**
	 * 오디오 파일에서 1초 단위로 웨이브폼을 계산해 리스트로 반환
	 * @param audioFilePath
	 * @param sampleRate
	 * @param durationSeconds
	 * @return	1초 단위 순차 웨이브폼이 담긴 리스트
	 */
	private List<float[]> extractWaveform(String audioFilePath, int sampleRate, int durationSeconds)
			throws IOException, WavFileException, FileFormatNotSupportedException {
		JLibrosa jLibrosa = new JLibrosa();
		List<float[]> waveformList = new ArrayList<>();
		float[] audioFeatures = jLibrosa.loadAndRead(audioFilePath, sampleRate, durationSeconds);

		// 1초 단위 오디오 웨이브폼 개수 계산
		int totalLength = audioFeatures.length;
		int numWaveforms = (int)Math.ceil( (double)totalLength / sampleRate);
		
		for (int index = 0; index < numWaveforms; index++) {	// 1초 단위 웨이브폼 계산
			int startPos = index * sampleRate;
			int waveformLength = Math.min(sampleRate, totalLength - startPos);	// 마지막 웨이브폼은 남은 길이만큼만 사용

			// 1초 길이의 오디오 조각 추출
			float[] waveform = new float[waveformLength];
			System.arraycopy(audioFeatures, startPos, waveform, 0, waveformLength);
			waveformList.add(waveform);	// 결과 리스트에 추가
		}
		return waveformList;
	}
	
	/**
	 * YAMNet 추론 결과를 해석하여 가장 높은 확률과 그 다음 확률, 또 그 다음 확률을 가진 클래스 인덱스 반환
	 * @param probabilities
	 * @return
	 */
	private String getPossibleLabels(Map<Integer, String> classMap, float[] probabilities) {
		// 원래 인덱스를 기억하기 위해 (인덱스, 값) 형태의 리스트 생성
        List<Entry<Integer, Float>> probabilityList = new ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            probabilityList.add(new SimpleEntry<>(i, probabilities[i]));
        }

		// 값에 따라 내림차순으로 정렬
        probabilityList.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
		
		int topN = 5;	// top 5 확률 클레스 레이블 반환
		StringBuilder sb = new StringBuilder();
		IntStream.range(0, topN).forEach(i -> {
			sb.append(classMap.get(probabilityList.get(i).getKey()));
			if (i < topN - 1) {
				sb.append(":");
			}
		});
		
        return sb.toString();
    }

	/**
	 * 클래스 레이블 CSV 파일을 읽어들여 클래스 인덱스와 display_name을 담은 맵을 반환
	 * @param csvFilePath
	 * @return
	 */
	private Map<Integer, String> loadClassMap(String csvFilePath) throws IOException {
        Map<Integer, String> classMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean passFirstLine = true;
			while ((line = br.readLine()) != null) {
				if (passFirstLine) { // 첫 번째 줄이 헤더일 때 건내 뛰도록
					passFirstLine = false;
					continue;
				}
				// 쉼표 구분자, 따옴표 내부 쉼표 무시
				String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
				if (values.length >= 2) {
					try {
						int index = Integer.parseInt(values[0].trim());
						String displayName = values[2].trim();
						classMap.put(index, displayName);

					} catch (NumberFormatException e) {
						System.err.println("Invalid number format in line: " + line);
					}
				}
			}
        }
        return classMap;
    }

	/**
	 * 오디오 파일에서 1초 단위로 로그 멜 스펙트로그램 계산해 리스트로 반환
	 * @param audioFilePath
	 * @param sampleRate
	 * @param durationSeconds
	 * @return	1초 단위 순차 로그 멜 스펙트로그램이 담긴 리스트
	 */
	public List<float[][]> calculateLogMelSpectrograms(String audioFilePath, int sampleRate, int durationSeconds)
			throws IOException, WavFileException, FileFormatNotSupportedException {
		int nFFT = 512;	// 푸리에 변환 적용할 윈도 크기(샘플 수)를 결정
		int hopSize = 256;	// 연속된 윈도 사이의 이동 간격 (STFT 계산 슬라이딩 시 얼마나 겹쳐서 이동할지)
		int numMelBands = 64;
		JLibrosa jLibrosa = new JLibrosa();

		List<float[][]> logMelSpectrogramList = new ArrayList<>();
		for (float[] waveform : extractWaveform(audioFilePath, sampleRate, durationSeconds)) {	// 1초 단위 멜 스펙트로그램 계산
			// 멜 스펙트로그램 생성
			float[][] melSpectrogram = jLibrosa.generateMelSpectroGram(waveform, sampleRate, nFFT, hopSize, numMelBands);
			
			// 로그 스케일로 변환
			float minVal = Float.POSITIVE_INFINITY;
			float maxVal = Float.NEGATIVE_INFINITY;
			int rows = melSpectrogram.length;
			int cols = melSpectrogram[0].length;
			float[][] logMelSpectrogram = new float[rows][cols];
			
			for (int i = 0; i < rows; i++) {
				for (int j = 0; j < cols; j++) {
					logMelSpectrogram[i][j] = (float) Math.log10(melSpectrogram[i][j] + 1e-10);
					// 최소값과 최대값 확인 및 교환
					if (logMelSpectrogram[i][j] < minVal) minVal = logMelSpectrogram[i][j];
					if (logMelSpectrogram[i][j] > maxVal) maxVal = logMelSpectrogram[i][j];
				}
			}

			if (maxVal != minVal) {	// 정규화: Min-Max Scaling 적용 [0-1]
				float range = maxVal - minVal;
				for (int i = 0; i < rows; i++) {
					for (int j = 0; j < cols; j++) {
						logMelSpectrogram[i][j] = (logMelSpectrogram[i][j] - minVal) / range;
					}
				}
			} else {	// 모든 값을 0으로 설정 (무음 또는 모든 샘플이 같은 값일 경우)
				for (float[] element : logMelSpectrogram) {
					Arrays.fill(element, 0.0f);
				}
			}

			logMelSpectrogramList.add(logMelSpectrogram);	// 결과 리스트에 추가
		}
		
		return logMelSpectrogramList;
	}
}