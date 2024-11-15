package com.example.mapruler;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class loginActivity extends AppCompatActivity {

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;

    private FirebaseAuth mAuth; // Firebase Authentication instance

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_xml);  // Load the login layout

        // Initialize FirebaseAuth instance
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        usernameEditText = findViewById(R.id.username);  // Email input field
        passwordEditText = findViewById(R.id.password);  // Password input field
        loginButton = findViewById(R.id.login_button);// Login button
        loginButton.setVisibility(View.VISIBLE);


        // Set up login button click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = usernameEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                // Basic validation
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(loginActivity.this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Call FirebaseAuth to sign in with email and password
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(loginActivity.this, task -> {
                            if (task.isSuccessful()) {
                                // Sign-in successful, navigate to MapsActivity
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    // Now, navigate to MapsActivity
                                    Intent intent = new Intent(loginActivity.this, MapsActivity.class);
                                    startActivity(intent);
                                    finish();  // Close the loginActivity so the user can't go back to it
                                }
                            } else {
                                // If sign-in fails, display a message to the user
                                Toast.makeText(loginActivity.this, "Authentication failed. Please check your email and password.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }
}
