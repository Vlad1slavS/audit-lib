package io.github.auditlib.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    @GetMapping("/simple")
    public ResponseEntity<String> simpleGet() {
        return ResponseEntity.ok("Simple response");
    }

    @PostMapping("/process")
    public ResponseEntity<String> processData(@RequestBody String input) {
        String result = testService.processData(input);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/calculate")
    public ResponseEntity<Integer> calculate(@RequestParam Integer a, @RequestParam Integer b) {
        Integer result = testService.calculateSum(a, b);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/error")
    public ResponseEntity<String> errorEndpoint(@RequestBody String input) {
        String result = testService.processWithError(input);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/external-call")
    public ResponseEntity<String> makeExternalCall() {
        try {
            return ResponseEntity.ok("External call successful");
        } catch (Exception e) {
            return ResponseEntity.ok("External call failed: " + e.getMessage());
        }
    }
}