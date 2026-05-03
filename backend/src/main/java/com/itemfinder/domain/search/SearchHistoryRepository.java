package com.itemfinder.domain.search;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    @Query("SELECT s.keyword FROM SearchHistory s WHERE s.searchedAt >= :since GROUP BY s.keyword ORDER BY COUNT(s.keyword) DESC LIMIT 10")
    List<String> findTop10Keywords(LocalDateTime since);
}
