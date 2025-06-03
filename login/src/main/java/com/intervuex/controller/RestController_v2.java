package com.intervuex.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import com.intervuex.database.*;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.*;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.UpdateResult;
import io.github.cdimascio.dotenv.Dotenv;
import org.bson.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;



@SpringBootApplication

@CrossOrigin(origins = {"http://localhost:8443","*"})
@RestController
public class RestController_v2 {
    public static String getCompanyCodeByInterviewerId(int interviewerId) {

        Document interviewer = interviewers.collection.find(new Document("interviewer_id", interviewerId)).first();


        if (interviewer == null) {
            return null;
        }


        return interviewer.getString("company_id");
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        String name = request.get("name");

        if (email == null || password == null || name == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Missing required fields"));
        }

        long userId = authentication.collection.countDocuments() + 1;


        Document existingUser = authentication.collection.find(new Document("email", email)).first();
        if (existingUser != null) {
            return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Email already registered"));
        }


        Document authDoc = new Document("user_id", userId)
                .append("email", email)
                .append("password", password);
        authentication.collection.insertOne(authDoc);


        Document userDoc = new Document("user_id", userId)
                .append("name", name)
                .append("email", email);
        users.collection.insertOne(userDoc);

        return ResponseEntity.ok(Map.of("status", "S", "message", "User registered successfully", "user_id", userId));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Missing required fields"));
        }


        Document userDoc = authentication.collection.find(new Document("email", email)).first();

        if (userDoc == null) {
            return ResponseEntity.status(401).body(Map.of("status", "E", "message", "User not found"));
        }


        String storedPassword = userDoc.getString("password");
        if (!storedPassword.equals(password)) {
            return ResponseEntity.status(401).body(Map.of("status", "E", "message", "Incorrect password"));
        }

        Map<String, Object> response = new HashMap<>();


        if (userDoc.containsKey("interviewer_id")) {
            String userType = "interviewer";

            Number interviewerIdNumber = userDoc.get("interviewer_id", Number.class);
            long interviewerId = (interviewerIdNumber != null) ? interviewerIdNumber.longValue() : -1;

            System.out.println("Retrieved interviewer_id: " + interviewerId);

            Document interviewerDoc = interviewers.collection.find(new Document("interviewer_id", interviewerId)).first();

            if (interviewerDoc == null) {
                System.out.println("Interviewer not found in database");
            }

            String companyId = (interviewerDoc != null) ? interviewerDoc.getString("comapny_id") : null;

            System.out.println("Retrieved company_id: " + companyId);

            response.put("status", "S");
            response.put("message", "Authenticated successfully");
            response.put("user_type", userType);
            response.put("interviewer_id", interviewerId);
            response.put("company_id", companyId);

        } else if (userDoc.containsKey("user_id")) {
            response.put("status", "S");
            response.put("message", "Authenticated successfully");
            response.put("user_type", "user");
            response.put("user_id", userDoc.get("user_id"));
        } else {
            return ResponseEntity.status(500).body(Map.of("status", "E", "message", "Invalid user record"));
        }

        return ResponseEntity.ok(response);
    }


    @PostMapping("/createJob")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody Map<String, Object> request) {

        List<String> requiredFields = List.of("company_id", "location", "job_type", "experience", "remote",
                "role", "company", "salary", "posted_date",
                "job_description", "requirements", "responsibilities");


        for (String field : requiredFields) {
            if (!request.containsKey(field)) {
                return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Missing field: " + field));
            }
        }


        String companyId = request.get("company_id").toString();
        if (companyId.length() != 1 || !Character.isLetter(companyId.charAt(0))) {
            return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Invalid company_id format. Must be a single letter."));
        }


        Document lastJob = jobs.collection.find().sort(new Document("job_id", -1)).first();
        int newJobId = (lastJob != null) ? lastJob.getInteger("job_id") + 1 : 1;


        Document jobDoc = new Document("job_id", newJobId)
                .append("company_id", companyId)
                .append("location", request.get("location"))
                .append("job_type", request.get("job_type"))
                .append("experience", request.get("experience"))
                .append("remote", request.get("remote"))
                .append("role", request.get("role"))
                .append("company", request.get("company"))
                .append("salary", request.get("salary"))
                .append("posted_date", request.get("posted_date"))
                .append("job_description", request.get("job_description"))
                .append("requirements", request.get("requirements"))
                .append("responsibilities", request.get("responsibilities"));


        jobs.collection.insertOne(jobDoc);


        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Job created successfully",
                "job_id", newJobId
        ));
    }

    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> getAllJobs() {
        List<Document> jobs = new ArrayList<>();

        try (MongoCursor<Document> cursor = com.intervuex.database.jobs.collection.find().iterator()) {
            while (cursor.hasNext()) {
                jobs.add(cursor.next());
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Jobs retrieved successfully",
                "data", jobs
        ));
    }

    @PostMapping("/edit_profile")
    public ResponseEntity<Map<String, Object>> editProfile(@RequestBody Map<String, Object> request) {
        if (!request.containsKey("user_id")) {
            return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Missing user_id"));
        }

        int userId = (int) request.get("user_id");
        Document userDoc = users.collection.find(new Document("user_id", userId)).first();

        if (userDoc == null) {
            return ResponseEntity.status(404).body(Map.of("status", "E", "message", "User not found"));
        }


        Document updatedData = new Document();
        request.forEach((key, value) -> {
            if (!key.equals("user_id")) {
                updatedData.append(key, value);
            }
        });

        users.collection.updateOne(new Document("user_id", userId), new Document("$set", updatedData));

        return ResponseEntity.ok(Map.of("status", "S", "message", "Profile updated successfully"));
    }

    @GetMapping("/get_profile")
    public ResponseEntity<Map<String, Object>> getProfile(@RequestParam int user_id) {

        Document userDoc = users.collection.find(new Document("user_id", user_id)).first();

        if (userDoc == null) {
            return ResponseEntity.status(404).body(Map.of("status", "E", "message", "User not found"));
        }


        Map<String, Object> response = new HashMap<>(userDoc);

        return ResponseEntity.ok(response);
    }
    @PostMapping("/apply_job")
    public ResponseEntity<Map<String, Object>> applyForJob(@RequestBody Map<String, Object> request) {
        int jobId = (int) request.get("job_id");
        int userId = (int) request.get("user_id");
        String coverLetter = (String) request.get("coverletter");
        String companyId = (String) request.get("company_id");

        if (jobId == 0 || userId == 0 || coverLetter == null || companyId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "E", "message", "Missing required fields"));
        }


        Document lastApplication = applications.collection.find()
                .sort(new Document("application_id", -1))
                .first();
        int newApplicationId = (lastApplication != null) ? lastApplication.getInteger("application_id") + 1 : 1;


        Document application = new Document("application_id", newApplicationId)
                .append("job_id", jobId)
                .append("user_id", userId)
                .append("company_id", companyId)
                .append("coverletter", coverLetter)
                .append("status", "Under Review");


        applications.collection.insertOne(application);

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Job application submitted successfully",
                "application_id", newApplicationId
        ));
    }

    @GetMapping("/applicationsByCompany")
    public ResponseEntity<Map<String, Object>> getApplicationsByCompany(@RequestParam String company_id) {
        List<Document> applicationsList = applications.collection.find(new Document("company_id", company_id))
                .into(new ArrayList<>());

        if (applicationsList.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "S",
                    "message", "No applications found for this company",
                    "applications", Collections.emptyList()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Applications retrieved successfully",
                "applications", applicationsList
        ));
    }
    @GetMapping("/applicationsByUser")
    public ResponseEntity<Map<String, Object>> getApplicationsByCompany(@RequestParam int user_id) {
        List<Document> applicationsList = applications.collection.find(new Document("user_id", user_id))
                .into(new ArrayList<>());

        if (applicationsList.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "S",
                    "message", "No applications found for this user",
                    "applications", Collections.emptyList()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Applications retrieved successfully",
                "applications", applicationsList
        ));
    }

    @PutMapping("/schedule-interview")
    public ResponseEntity<Map<String, Object>> scheduleInterview(@RequestBody Map<String, Object> request) {
        int applicationId = (int) request.get("application_id");
        int interviewerId = (int) request.get("interviewer_id");
        String date = (String) request.get("date");
        String time = (String) request.get("time");


        String meetingId = UUID.randomUUID().toString().replace("-", "").substring(0, 15);


        Document filter = new Document("application_id", applicationId);
        Document update = new Document("$set", new Document("interviewer_id", interviewerId)
                .append("date", date)
                .append("time", time)
                .append("meeting_id", meetingId)
                .append("status", "upcoming"));

        UpdateResult result = applications.collection.updateOne(filter, update);

        if (result.getModifiedCount() == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "E",
                    "message", "Application ID not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Interview scheduled successfully",
                "meeting_id", meetingId
        ));
    }

    @PutMapping("/update-application-status")
    public ResponseEntity<Map<String, Object>> updateApplicationStatus(@RequestBody Map<String, Object> request) {
        int applicationId = (int) request.get("application_id");
        String status = (String) request.get("status");


        Set<String> allowedStatuses = Set.of("not accepted", "past", "hired", "not hired", "under_review", "upcoming");

        if (!allowedStatuses.contains(status.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "E",
                    "message", "Invalid status. Allowed values: not accepted, past, hired, not hired, under_review, upcoming"
            ));
        }


        Document filter = new Document("application_id", applicationId);
        Document update = new Document("$set", new Document("status", status));

        UpdateResult result = applications.collection.updateOne(filter, update);

        if (result.getModifiedCount() == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "E",
                    "message", "Application ID not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Application status updated successfully"
        ));
    }

    @PutMapping("/finish-interview")
    public ResponseEntity<Map<String, Object>> finishInterview(@RequestBody Map<String, Object> request) {
        int applicationId = (int) request.get("application_id");
        int score = (int) request.get("score");
        String feedback = (String) request.get("feedback");


        Document filter = new Document("application_id", applicationId);
        Document update = new Document("$set", new Document("score", score)
                .append("feedback", feedback)
                .append("status", "past"));

        UpdateResult result = applications.collection.updateOne(filter, update);

        if (result.getModifiedCount() == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "E",
                    "message", "Application ID not found"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Interview finished, details updated successfully"
        ));
    }





        @PostMapping("/generate")
        public String generateJwt(@RequestBody Map<String, Object> requestData) {
            try {
                String privateKeyPem = (String) requestData.get("private_key");
                Map<String, Object> header = (Map<String, Object>) requestData.get("header");
                Map<String, Object> payload = (Map<String, Object>) requestData.get("payload");


                PrivateKey privateKey = getPrivateKeyFromPem(privateKeyPem);

                long nowMillis = System.currentTimeMillis();
                long expMillis = nowMillis + (60 * 60 * 1000); // 1 hour expiry
                payload.put("iat", nowMillis / 1000);
                payload.put("exp", expMillis / 1000);


                String jwt = Jwts.builder()
                        .setHeader(header)
                        .setClaims(payload)
                        .signWith(privateKey, SignatureAlgorithm.RS256)
                        .compact();

                return jwt;
            } catch (Exception e) {
                return "Error generating JWT: " + e.getMessage();
            }
        }

        private PrivateKey getPrivateKeyFromPem(String pem) throws Exception {
            String privateKeyContent = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        }


    @PostMapping("/add-question")
    public ResponseEntity<Map<String, Object>> addCodingQuestion(@RequestBody Map<String, Object> request) {
        MongoCollection<Document> collection = coding_interview.collection;

        long codingId = collection.countDocuments() + 1;

        Document question = new Document("coding_id", codingId)
                .append("title", request.get("title"))
                .append("difficulty", request.get("difficulty"))
                .append("description", request.get("description"))
                .append("language", request.get("language"))
                .append("time_limit", request.get("time_limit"))
                .append("starter_code", request.get("starter_code"))
                .append("test_cases", request.get("test_cases"))
                .append("expected_outputs", request.get("expected_outputs"))
                .append("testcases_passed", 0);

        collection.insertOne(question);

        return ResponseEntity.ok(Map.of(
                "status", "S",
                "message", "Coding question added successfully",
                "coding_id", codingId
        ));
    }

    @GetMapping("/get-question")
    public ResponseEntity<Map<String, Object>> getCodingQuestion(@RequestParam int coding_id) {
        MongoCollection<Document> collection = coding_interview.collection;

        Document query = new Document("coding_id", coding_id);
        Document question = collection.find(query).first();

        if (question == null) {
            return ResponseEntity.status(404).body(Map.of("status", "E", "message", "Question not found"));
        }

        return ResponseEntity.ok(question);
    }


    @PutMapping("/update-testcases-passed")
    public ResponseEntity<Map<String, Object>> updateTestcasesPassed(@RequestBody Map<String, Object> request) {
        int codingId = (int) request.get("coding_id");
        int testcasesPassed = (int) request.get("testcases_passed");

        MongoCollection<Document> collection = coding_interview.collection;

        Document filter = new Document("coding_id", codingId);
        Document update = new Document("$set", new Document("testcases_passed", testcasesPassed));

        UpdateResult result = collection.updateOne(filter, update);

        if (result.getModifiedCount() == 0) {
            return ResponseEntity.status(404).body(Map.of("status", "E", "message", "Coding question not found"));
        }

        return ResponseEntity.ok(Map.of("status", "S", "message", "Test cases updated successfully"));
    }

























    public static void main(String[] args) {
        SpringApplication.run(RestController_v2.class, args);
    }
}








