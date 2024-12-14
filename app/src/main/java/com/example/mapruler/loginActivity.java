/**
 * Login activity for the app. Calls Firebase Auth to authenticate the user to sign in or create a new user.
 */
package com.example.mapruler;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Activity got logging in or signing up with Firebase authentication.
 */
public class loginActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private Button signUpButton;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the login layout
        setContentView(R.layout.login_xml);

        // Initialize FirebaseAuth instance
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        emailEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        signUpButton = findViewById(R.id.sign_up_button);
        loginButton.setVisibility(View.VISIBLE);
        signUpButton.setVisibility(View.VISIBLE);


        // Set up login button click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                //tell the user to put in email and password if they didn't
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
                                UserData.getInstance().setFirebaseUser(user);
                                if (user != null) {
                                    Intent intent = new Intent(loginActivity.this, MapsActivity.class);
                                    startActivity(intent);
                                    // Close the loginActivity so the user can't go back to it
                                    finish();
                                }
                            } else {
                                // If sign-in fails
                                Toast.makeText(loginActivity.this, "Authentication failed. Please check your email and password.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        // Set up sign up button click listener
        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                // Basic validation
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(loginActivity.this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Call FirebaseAuth to create a new user with email and password
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(loginActivity.this, task -> {
                            if (task.isSuccessful()) {
                                // Sign-up successful, navigate to MapsActivity
                                FirebaseUser user = mAuth.getCurrentUser();
                                UserData.getInstance().setFirebaseUser(user);
                                if (user != null) {
                                    Intent intent = new Intent(loginActivity.this, MapsActivity.class);
                                    startActivity(intent);
                                    // Close the loginActivity so the user can't go back to it
                                    finish();
                                }
                            } else {
                                // If sign-up fails, display a message to the user
                                Toast.makeText(loginActivity.this, "Registration failed. Please try again.", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });


    }
}
