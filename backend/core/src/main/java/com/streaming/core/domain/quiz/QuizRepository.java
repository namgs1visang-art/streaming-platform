package com.streaming.core.domain.quiz;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** keyword 빈 문자열 = 전체 */
    @Query("select q from Quiz q where lower(q.title) like lower(concat('%', :keyword, '%')) " +
           "or lower(q.question) like lower(concat('%', :keyword, '%'))")
    Page<Quiz> search(@Param("keyword") String keyword, Pageable pageable);
}
