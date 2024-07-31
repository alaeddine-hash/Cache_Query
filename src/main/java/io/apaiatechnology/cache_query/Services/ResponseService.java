package io.apaiatechnology.cache_query.Services;



import io.apaiatechnology.cache_query.Entities.Question;
import io.apaiatechnology.cache_query.Entities.Response;
import io.apaiatechnology.cache_query.Repositoies.ResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.Optional;

@Service
public class ResponseService {

    @Autowired
    private ResponseRepository responseRepository;

    @Autowired
    private SimilarityService similarityService;


    private RestTemplate restTemplate;

    public Optional<Response> findAnswerByQuestion(String question, String language) {
        // Get similarity score and question ID from the similarity service
        Map<String, Object> similarityResponse = similarityService.getSimilarityScore(question, language);
        String questionId = (String) similarityResponse.get("question_id");
        double similarityScore = (double) similarityResponse.get("similarity_score");

        if (similarityScore > 0.8) {  // Threshold for using existing answer
            return responseRepository.findById(Long.parseLong(questionId));
        }

        return Optional.empty();
    }

    public Response saveAnswer(Question question, String answerText) {
        Response answer = new Response();
        answer.setQuestion(question);
        answer.setContent(answerText);
        return responseRepository.save(answer);
    }
}
