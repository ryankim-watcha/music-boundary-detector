package com.watcha.music.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "music_boundary_table", schema = "mbdb",
	indexes = @Index(name = "idx_audio_path", columnList = "audio_file_path"))
@AllArgsConstructor
@NoArgsConstructor
public class MusicBoundaryEntity {

	@Id
    @GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", length = 36)
    private String id;

	@Column(name = "audio_file_path", length = 256)
	private String audioFilePath;

	@Column(name = "music_start", length = 8)
	private String musicStart;

	@Column(name = "music_end", length = 8)
	private String musicEnd;

	@Column(name = "music_start_seconds")
	private int musicStartSeconds;

	@Column(name = "music_end_seconds")
	private int musicEndSeconds;

	@Column(name = "title", length = 128)
	String title;

	@Column(name = "subtitle", length = 128)
	String subtitle;
}
