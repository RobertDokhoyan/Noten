package com.example.noten;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Register extends AppCompatActivity {
    TextInputEditText editTextEmail, editTextPassword, editTextConfirmPassword;
    Button btnSendVerification, btnRegister, btnGuest;
    FirebaseAuth mAuth;
    ProgressBar progressBar;
    TextView textViewLogin;
    FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        editTextEmail = findViewById(R.id.email);
        editTextPassword = findViewById(R.id.password);
        editTextConfirmPassword = findViewById(R.id.confirm_password);
        btnSendVerification = findViewById(R.id.btn_send_verification);
        btnRegister = findViewById(R.id.btn_register);
        btnGuest = findViewById(R.id.btn_guest);
        progressBar = findViewById(R.id.progressBar);
        textViewLogin = findViewById(R.id.RegisterNow);

        // По умолчанию делаем кнопку входа (btn_register) неактивной,
        // так как аккаунт ещё не подтверждён.
        btnRegister.setEnabled(false);

        // Обработчик для отправки письма верификации и регистрации аккаунта
        btnSendVerification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressBar.setVisibility(View.VISIBLE);
                String email = editTextEmail.getText().toString().trim();
                String password = editTextPassword.getText().toString();
                String confirmPassword = editTextConfirmPassword.getText().toString();

                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(Register.this, "Enter email", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }
                if (TextUtils.isEmpty(password)) {
                    Toast.makeText(Register.this, "Enter password", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }
                if (!password.equals(confirmPassword)) {
                    Toast.makeText(Register.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                // Создаём пользователя
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(Register.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                progressBar.setVisibility(View.GONE);
                                if (task.isSuccessful()) {
                                    currentUser = mAuth.getCurrentUser();
                                    if (currentUser != null) {
                                        // Отправляем письмо верификации
                                        currentUser.sendEmailVerification()
                                                .addOnCompleteListener(Register.this, new OnCompleteListener<Void>() {
                                                    @Override
                                                    public void onComplete(@NonNull Task<Void> task) {
                                                        if (task.isSuccessful()) {
                                                            Toast.makeText(getApplicationContext(),
                                                                    "Verification email sent. Check your inbox.",
                                                                    Toast.LENGTH_SHORT).show();
                                                            // Деактивируем кнопку отправки письма,
                                                            // так как оно уже отправлено
                                                            btnSendVerification.setEnabled(false);
                                                            // Активируем кнопку входа, чтобы пользователь мог войти после верификации
                                                            btnRegister.setEnabled(true);
                                                        } else {
                                                            String errorMsg = task.getException() != null ?
                                                                    task.getException().getMessage() : "Failed to send verification email.";
                                                            Toast.makeText(Register.this, errorMsg, Toast.LENGTH_SHORT).show();
                                                        }
                                                    }
                                                });
                                    }
                                } else {
                                    String errorMsg = task.getException() != null ?
                                            task.getException().getMessage() : "Registration Failed";
                                    Toast.makeText(Register.this, errorMsg, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });

        // Обработчик для входа в аккаунт (проверка подтверждения email)
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUser != null) {
                    // Обновляем данные пользователя
                    currentUser.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (currentUser.isEmailVerified()) {
                                Toast.makeText(Register.this, "Email verified! Logging in.", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                                finish();
                            } else {
                                Toast.makeText(Register.this, "Email not verified yet. Please verify your email.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } else {
                    Toast.makeText(Register.this, "Please register first.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Переход к экрану входа (Login)
        textViewLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), Login.class));
                finish();
            }
        });

        // Обработчик для входа в режиме Guest (без аккаунта)
        btnGuest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                finish();
            }
        });
    }
}
