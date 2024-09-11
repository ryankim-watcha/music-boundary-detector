package com.watcha.music.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.watcha.music.domain.MusicBoundaryEntity;

@Repository
public interface MusicBoundaryRepository extends JpaRepository<MusicBoundaryEntity, String> {
	
}
