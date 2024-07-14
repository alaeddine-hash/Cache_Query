package io.apaiatechnology.cache_query.Controllers;

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

    @PostMapping("/query")
    public ResponseEntity<Map<String, String>> getResponse(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String language = request.get("language");

        Optional<Question> questionOpt = questionService.getQuestionByQueryAndLanguage(query, language);
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            if (question.getResponses().size() >= minResponsesPerQuestion) {
                Response response = questionService.getRandomResponse(question);
                return ResponseEntity.ok(Collections.singletonMap("response", response.getContent()));
            } else {
                // Handle case where there are fewer than minResponsesPerQuestion responses
                String externalResponse = fetchResponseFromExternalAPI(query, language);
                return ResponseEntity.ok(Collections.singletonMap("response", externalResponse));
            }
        } else {
            String externalResponse = fetchResponseFromExternalAPI(query, language);
            return ResponseEntity.ok(Collections.singletonMap("response", externalResponse));
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
                LOGGER.log(Level.WARNING, "Error occurred while calling external API (attempt " + attempt + "): " + e.getMessage());
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
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
        return (String) response.getBody().get("response");
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

}


