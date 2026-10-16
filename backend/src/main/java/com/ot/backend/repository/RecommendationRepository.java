package com.ot.backend.repository;

import com.ot.backend.entity.AIRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<AIRecommendation, Long> {

}