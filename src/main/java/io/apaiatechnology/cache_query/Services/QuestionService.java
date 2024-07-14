package io.apaiatechnology.cache_query.Services;

import io.apaiatechnology.cache_query.Entities.Question;
import io.apaiatechnology.cache_query.Entities.Response;
import io.apaiatechnology.cache_query.Repositoies.QuestionRepository;
import io.apaiatechnology.cache_query.Repositoies.ResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class QuestionService {
    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ResponseRepository responseRepository;

    private final Random random = new Random();


    public Optional<Question> getQuestionByQueryAndLanguage(String query, String language) {
        return questionRepository.findByQueryAndLanguage(query, language);
    }

    public Question saveQuestion(Question question) {
        return questionRepository.save(question);
    }

    public Response getRandomResponse(Question question) {
        List<Response> responses = question.getResponses();
        return responses.get(random.nextInt(responses.size()));
    }
}
