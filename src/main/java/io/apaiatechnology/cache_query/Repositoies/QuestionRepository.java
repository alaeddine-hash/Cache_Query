package io.apaiatechnology.cache_query.Repositoies;

import io.apaiatechnology.cache_query.Entities.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByQueryAndLanguage(String query, String language);

}
