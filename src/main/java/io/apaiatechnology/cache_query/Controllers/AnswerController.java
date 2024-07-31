package io.apaiatechnology.cache_query.Controllers;


import io.apaiatechnology.cache_query.Entities.Question;
import io.apaiatechnology.cache_query.Entities.Response;
import io.apaiatechnology.cache_query.Services.ResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/answers")
public class AnswerController {

    @Autowired
    private ResponseService answerService;

    @GetMapping("/{question}")
    public ResponseEntity<Response> getAnswer(@PathVariable String question, @RequestParam String language) {
        Optional<Response> answer = answerService.findAnswerByQuestion(question, language);
        return answer.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Response> saveAnswer(@RequestParam Question question, @RequestParam String answerText) {
        Response answer = answerService.saveAnswer(question, answerText);
        return ResponseEntity.ok(answer);
    }
}
