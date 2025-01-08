package com.example.noten;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private ImageView avatarCircle;
    private Button changeAvatarButton, saveNicknameButton, changePasswordButton, logoutButton;
    private EditText editNickname, editOldPassword, editNewPassword, editConfirmNewPassword;

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private Uri avatarUri;

    private static final int REQUEST_CODE_GALLERY = 100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");
        storageReference = FirebaseStorage.getInstance().getReference("Avatars");

        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return null;
        }

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(view);
        loadUserData();

        setButtonListeners();

        return view;
    }

    private void initializeViews(View view) {
        avatarCircle = view.findViewById(R.id.avatar_circle);
        changeAvatarButton = view.findViewById(R.id.change_avatar_button);
        saveNicknameButton = view.findViewById(R.id.save_nickname_button);
        changePasswordButton = view.findViewById(R.id.save_password_button);
        logoutButton = view.findViewById(R.id.logout);
        editNickname = view.findViewById(R.id.edit_nickname);
        editOldPassword = view.findViewById(R.id.edit_old_password);
        editNewPassword = view.findViewById(R.id.edit_new_password);
        editConfirmNewPassword = view.findViewById(R.id.edit_confirm_new_password);
    }

    private void setButtonListeners() {
        changeAvatarButton.setOnClickListener(v -> openGallery());
        saveNicknameButton.setOnClickListener(v -> saveNickname());
        changePasswordButton.setOnClickListener(v -> changePassword());
        logoutButton.setOnClickListener(v -> logout());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_CODE_GALLERY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_GALLERY && resultCode == getActivity().RESULT_OK && data != null) {
            avatarUri = data.getData();
            avatarCircle.setImageURI(avatarUri);
        }
    }

    private void saveNickname() {
        String nickname = editNickname.getText().toString().trim();

        if (TextUtils.isEmpty(nickname)) {
            Toast.makeText(getContext(), "Please enter a nickname", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        Map<String, Object> userData = new HashMap<>();
        userData.put("nickname", nickname);

        if (avatarUri != null) {
            uploadAvatarAndSaveData(userId, userData);
        } else {
            saveToDatabase(userId, userData);
        }
    }

    private void uploadAvatarAndSaveData(String userId, Map<String, Object> userData) {
        StorageReference avatarRef = storageReference.child(userId + ".jpg");
        avatarRef.putFile(avatarUri).addOnSuccessListener(taskSnapshot -> avatarRef.getDownloadUrl().addOnSuccessListener(uri -> {
            userData.put("avatarUrl", uri.toString());
            saveToDatabase(userId, userData);
        })).addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to upload avatar", Toast.LENGTH_SHORT).show());
    }

    private void saveToDatabase(String userId, Map<String, Object> userData) {
        Log.d("ProfileFragment", "UserId: " + userId);  // Логирование userId
        Log.d("ProfileFragment", "User data: " + userData.toString());  // Логирование данных

        databaseReference.child(userId).setValue(userData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(getContext(), "Data saved successfully", Toast.LENGTH_SHORT).show();
            } else {
                Exception e = task.getException();
                if (e != null) {
                    Log.e("ProfileFragment", "Failed to save data: " + e.getMessage());  // Логирование ошибки
                }
                Toast.makeText(getContext(), "Failed to save data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();  // Получаем текущего пользователя
        if (user != null) {  // Проверяем, если пользователь авторизован
            databaseReference.child(user.getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {  // Если запрос успешен и данные существуют
                    DataSnapshot dataSnapshot = task.getResult();
                    String nickname = dataSnapshot.child("nickname").getValue(String.class);  // Загружаем никнейм
                    String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);  // Загружаем URL аватара

                    if (nickname != null) {
                        editNickname.setText(nickname);  // Устанавливаем значение в EditText
                    }
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(this).load(avatarUrl).into(avatarCircle);  // Загружаем аватар через Glide
                    }
                } else {
                    Toast.makeText(getContext(), "No data found or failed to load", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Если пользователь не авторизован, показываем сообщение и перенаправляем на экран входа
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            redirectToLogin();
        }
    }

    private void changePassword() {
        String oldPassword = editOldPassword.getText().toString().trim();
        String newPassword = editNewPassword.getText().toString().trim();
        String confirmNewPassword = editConfirmNewPassword.getText().toString().trim();

        // Проверка на пустые поля
        if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmNewPassword)) {
            Toast.makeText(getContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Проверка, что новый пароль и подтверждение пароля совпадают
        if (!newPassword.equals(confirmNewPassword)) {
            Toast.makeText(getContext(), "New password and confirmation don't match", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            redirectToLogin();
            return;
        }

        // Проверка старого пароля и обновление нового
        reauthenticateAndUpdatePassword(user, oldPassword, newPassword);
    }

    private void reauthenticateAndUpdatePassword(FirebaseUser user, String oldPassword, String newPassword) {
        // Создаем объект EmailAuthProvider для старого пароля
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);

        // Переаутентификация с использованием старых учетных данных
        user.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updatePassword(newPassword);
                    } else {
                        Toast.makeText(getContext(), "Old password is incorrect", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updatePassword(String newPassword) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.updatePassword(newPassword).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(getContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Failed to update password", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void logout() {
        new AlertDialog.Builder(getContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();
                    redirectToLogin();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void redirectToLogin() {
        Toast.makeText(getContext(), "Redirecting to login", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getContext(), Login.class);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
    }
}
