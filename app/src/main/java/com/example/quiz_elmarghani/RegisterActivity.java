package com.example.quiz_elmarghani;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private EditText emailRegister, passwordRegister, confirmPasswordRegister;
    private Button btnRegister;
    private TextView tvGoToLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        emailRegister = findViewById(R.id.emailRegister);
        passwordRegister = findViewById(R.id.passwordRegister);
        confirmPasswordRegister = findViewById(R.id.confirmPasswordRegister);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        btnRegister.setOnClickListener(v -> {
            String email = emailRegister.getText().toString().trim();
            String password = passwordRegister.getText().toString().trim();
            String confirmPassword = confirmPasswordRegister.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                emailRegister.setError("Email est requis");
                return;
            }

            if (TextUtils.isEmpty(password)) {
                passwordRegister.setError("Mot de passe est requis");
                return;
            }

            if (password.length() < 6) {
                passwordRegister.setError("Le mot de passe doit avoir au moins 6 caractères");
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordRegister.setError("Les mots de passe ne correspondent pas");
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "Compte créé ! Veuillez vous connecter.", Toast.LENGTH_SHORT).show();
                            // Déconnexion car Firebase connecte l'utilisateur automatiquement après l'inscription
                            mAuth.signOut();
                            
                            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                            intent.putExtra("email", email); // On passe l'email à la page de connexion
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Erreur d'inscription: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}