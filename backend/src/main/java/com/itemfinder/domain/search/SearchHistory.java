package com.itemfinder.domain.search;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_history")
@Getter
@NoArgsConstructor
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyword;

    @Column(name = "last_crawled")
    private LocalDateTime lastCrawled;

    public SearchHistory(String keyword) {
        this.keyword = keyword;
        this.lastCrawled = LocalDateTime.now();
    }

    public void updateCrawledTime() {
        this.lastCrawled = LocalDateTime.now();
    }
}
