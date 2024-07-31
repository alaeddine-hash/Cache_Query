package io.apaiatechnology.cache_query.Controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apaiatechnology.cache_query.Entities.Question;
import io.apaiatechnology.cache_query.Entities.Response;
import io.apaiatechnology.cache_query.Exceptions.ExternalApiException;
import io.apaiatechnology.cache_query.Services.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/questions")
public class QuestionController {
    @Autowired
    private QuestionService questionService;

    private static final Logger LOGGER = Logger.getLogger(QuestionController.class.getName());

    @Value("${external.api.url}")
    private String apiUrl;

    @Value("${external.api.connectTimeout}")
    private int connectTimeout;

    @Value("${external.api.readTimeout}")
    private int readTimeout;

    @Value("${external.api.maxRetries}")
    private int maxRetries;

    @Value("${external.api.retryDelay}")
    private int retryDelay;

    @Value("${min.responses.per.question}")
    private int minResponsesPerQuestion;

    @Value("${similarity.service.url}")
    private String similarityServiceUrl;

    @PostMapping("/query")
    public ResponseEntity<Map<String, String>> getResponse(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String language = request.get("language");

        Optional<Question> questionOpt = questionService.getQuestionByQueryAndLanguage(query, language);
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            if (question.getResponses().size() >= minResponsesPerQuestion) {
                try {
                    randomDelay();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.SEVERE, "Thread was interrupted during the delay", e);
                    return ResponseEntity.status(500).body(Collections.singletonMap("error", "Internal server error"));
                }
                Response response = questionService.getRandomResponse(question);
                return ResponseEntity.ok(Collections.singletonMap("response", response.getContent()));
            } else {
                String similarityResponse = fetchSimilaritySearch(query, language);
                Map<String, Object> similarityResponseMap = parseSimilarityResponse(similarityResponse);

                double similarityScore = (double) similarityResponseMap.get("similarity_score");
                String responseLanguage = (String) similarityResponseMap.get("language");
                Integer questionId = (Integer) similarityResponseMap.get("question_id");

                boolean bool = responseLanguage.trim().equalsIgnoreCase(language.trim());

                if (similarityScore > 0.8 && responseLanguage != null && responseLanguage.trim().equalsIgnoreCase(language.trim()) && questionId != null) {
                    Optional<Question> similarQuestionOpt = questionService.getQuestionById(questionId);
                    if (similarQuestionOpt.isPresent() && similarQuestionOpt.get().getResponses().size() >= minResponsesPerQuestion) {
                        try {
                            randomDelay();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.log(Level.SEVERE, "Thread was interrupted during the delay", e);
                            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Internal server error"));
                        }
                        Response response = questionService.getRandomResponse(similarQuestionOpt.get());
                        return ResponseEntity.ok(Collections.singletonMap("response", response.getContent()));
                    } else {
                        String externalResponse = fetchResponseFromExternalAPI(query, language);
                        return ResponseEntity.ok(Collections.singletonMap("response", externalResponse));
                    }
                } else {
                    String externalResponse = fetchResponseFromExternalAPI(query, language);
                    return ResponseEntity.ok(Collections.singletonMap("response", externalResponse));
                }
            }
        } else {
            String similarityResponse = fetchSimilaritySearch(query, language);
            Map<String, Object> similarityResponseMap = parseSimilarityResponse(similarityResponse);

            double similarityScore = (double) similarityResponseMap.get("similarity_score");
            String responseLanguage = (String) similarityResponseMap.get("language");
            Integer questionId = (Integer) similarityResponseMap.get("question_id");

            if (similarityScore > 0.8 && responseLanguage != null && responseLanguage.equalsIgnoreCase(language) && questionId != null) {
                Optional<Question> similarQuestionOpt = questionService.getQuestionById(questionId);
                if (similarQuestionOpt.isPresent() && similarQuestionOpt.get().getResponses().size() >= minResponsesPerQuestion) {
                    try {
                        randomDelay();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.log(Level.SEVERE, "Thread was interrupted during the delay", e);
                        return ResponseEntity.status(500).body(Collections.singletonMap("error", "Internal server error"));
                    }
                    Response response = questionService.getRandomResponse(similarQuestionOpt.get());
                    return ResponseEntity.ok(Collections.singletonMap("response", response.getContent()));
                } else {
                    String externalResponse = fetchResponseFromExternalAPI(query, language);
                    return ResponseEntity.ok(Collections.singletonMap("response", externalResponse));
                }
            } else {
                String externalResponse = fetchResponseFromExternalAPI(query, language);
                return ResponseEntity.ok(Collections.singletonMap("response", externalResponse));
            }
        }
    }

    private String fetchResponseFromExternalAPI(String query, String language) {
        String externalResponse = null;
        int attempt = 0;
        while (attempt < maxRetries && externalResponse == null) {
            try {
                attempt++;
                externalResponse = redirectToExternalEndpoint(query, language);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                LOGGER.log(Level.WARNING, "Error occurred while calling external API (attempt " + attempt + "): " + e.getMessage(), e);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "Internal server error";
                }
            } catch (RestClientException e) {
                LOGGER.log(Level.SEVERE, "Error occurred while calling external API", e);
                throw new ExternalApiException("Error occurred while calling external API", e);
            }
        }

        if (externalResponse == null) {
            return "Timeout occurred while calling external API after multiple attempts";
        }

        saveQuestionAndResponse(query, language, externalResponse);
        return externalResponse;
    }

    private String redirectToExternalEndpoint(String query, String language) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestTemplate restTemplate = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("language", language);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            return (String) response.getBody().get("response");
        } catch (HttpServerErrorException e) {
            LOGGER.log(Level.SEVERE, "Server error occurred while calling external API", e);
            throw e;
        } catch (ResourceAccessException e) {
            LOGGER.log(Level.SEVERE, "Resource access error occurred while calling external API", e);
            throw e;
        } catch (RestClientException e) {
            LOGGER.log(Level.SEVERE, "Client error occurred while calling external API", e);
            throw e;
        }
    }

    private void saveQuestionAndResponse(String query, String language, String responseContent) {
        Optional<Question> questionOpt = questionService.getQuestionByQueryAndLanguage(query, language);

        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();

            Response response = new Response();
            response.setContent(responseContent);
            response.setQuestion(question);

            if (question.getResponses() == null) {
                question.setResponses(new ArrayList<>()); // Ensure responses collection is initialized
            }
            question.getResponses().add(response); // Add the new response to the existing question's responses

            questionService.saveQuestion(question); // Save the updated question
        } else {
            Question question = new Question();
            question.setQuery(query);
            question.setLanguage(language);

            Response response = new Response();
            response.setContent(responseContent);
            response.setQuestion(question);

            question.setResponses(Collections.singletonList(response)); // Initially set responses for new question

            questionService.saveQuestion(question); // Save the new question
        }
    }

    private void randomDelay() throws InterruptedException {
        Random random = new Random();
        int delay = 1 + random.nextInt(5); // Generate a random delay between 1 and 5 seconds
        Thread.sleep(delay * 1000); // Convert seconds to milliseconds
    }

    private String fetchSimilaritySearch(String query, String language) {
        String similarityResponse = null;
        int attempt = 0;
        while (attempt < maxRetries && similarityResponse == null) {
            try {
                attempt++;
                similarityResponse = redirectToSimilarityService(query, language);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                LOGGER.log(Level.WARNING, "Error occurred while calling similarity service (attempt " + attempt + "): " + e.getMessage());
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "Internal server error";
                }
            } catch (RestClientException e) {
                LOGGER.log(Level.SEVERE, "Error occurred while calling similarity service", e);
                throw new ExternalApiException("Error occurred while calling similarity service", e);
            }
        }

        if (similarityResponse == null) {
            return "Timeout occurred while calling similarity service after multiple attempts";
        }

        return similarityResponse;
    }

    private String redirectToSimilarityService(String query, String language) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestTemplate restTemplate = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("language", language);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(similarityServiceUrl, request, String.class);
        return response.getBody();
    }

    private Map<String, Object> parseSimilarityResponse(String similarityResponse) {
        LOGGER.info("Raw similarity response: " + similarityResponse);

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(similarityResponse, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error parsing similarity service response", e);
            return Collections.singletonMap("similarity_score", 0.0);
        }
    }
}
