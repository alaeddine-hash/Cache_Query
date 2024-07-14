package io.apaiatechnology.cache_query.Repositoies;

import io.apaiatechnology.cache_query.Entities.Response;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResponseRepository extends JpaRepository<Response, Long> {
}
