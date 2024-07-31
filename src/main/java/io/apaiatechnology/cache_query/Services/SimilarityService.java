package io.apaiatechnology.cache_query.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SimilarityService {

    @Value("${similarity.service.url}")
    private String similarityServiceUrl;

    @Autowired
    private final RestTemplate restTemplate;

    public SimilarityService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> getSimilarityScore(String query, String language) {
        Map<String, Object> request = new HashMap<>();
        request.put("query", query);
        request.put("language", language);

        return restTemplate.postForObject(similarityServiceUrl, request, Map.class);
    }
}
