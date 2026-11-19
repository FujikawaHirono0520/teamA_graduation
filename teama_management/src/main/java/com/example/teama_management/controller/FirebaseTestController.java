package com.example.teama_management.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FirebaseTestController {

    private final Firestore firestore;

    public FirebaseTestController(Firestore firestore) {
        this.firestore = firestore;
    }

    @GetMapping("/firebase-test")
    public String test() throws Exception {
        DocumentReference ref = firestore.collection("test").document("hello");
        ref.set(Map.of("message", "Firestoreにつながりました")).get();
        return ref.get().get().getString("message");
    }
}