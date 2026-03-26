package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.FeedbackTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeedbackTagRepository extends JpaRepository<FeedbackTag, Long> {

    List<FeedbackTag> findAllByOrderByIdAsc();

    List<FeedbackTag> findAllByTagNameIn(Collection<String> tagNames);
}
