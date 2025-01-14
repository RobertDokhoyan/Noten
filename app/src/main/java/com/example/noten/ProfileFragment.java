package com.example.noten;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.fragment.app.FragmentResultListener;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

        // Добавляем слушателя для получения выбранной аватарки из AvatarSelectionFragment
        getParentFragmentManager().setFragmentResultListener("avatarSelection", this, (requestKey, result) -> {
            int selectedAvatarResId = result.getInt("selectedAvatar");
            avatarUri = Uri.parse("android.resource://" + getActivity().getPackageName() + "/" + selectedAvatarResId);
            Glide.with(this).load(avatarUri).into(avatarCircle);  // Обновляем отображение аватара
            saveAvatarToDatabase();  // Сохраняем аватарку в базе данных
        });

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
        changeAvatarButton.setOnClickListener(v -> navigateToAvatarSelection());
        saveNicknameButton.setOnClickListener(v -> saveNickname());
        changePasswordButton.setOnClickListener(v -> changePassword());
        logoutButton.setOnClickListener(v -> logout());
    }

    private void navigateToAvatarSelection() {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new AvatarSelectionFragment())
                .addToBackStack(null)
                .commit();
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
        databaseReference.child(userId).setValue(userData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(getContext(), "Data saved successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Failed to save data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            databaseReference.child(user.getUid()).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    DataSnapshot dataSnapshot = task.getResult();
                    String nickname = dataSnapshot.child("nickname").getValue(String.class);
                    String avatarUrl = dataSnapshot.child("avatarUrl").getValue(String.class);

                    if (nickname != null) {
                        editNickname.setText(nickname);
                    }
                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(this).load(avatarUrl).into(avatarCircle);  // Загрузка аватара из базы данных
                    }
                }
            });
        }
    }

    private void saveAvatarToDatabase() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && avatarUri != null) {
            String userId = user.getUid();
            Map<String, Object> userData = new HashMap<>();
            userData.put("avatarUrl", avatarUri.toString());

            databaseReference.child(userId).updateChildren(userData).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(getContext(), "Avatar saved successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Failed to save avatar", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void changePassword() {
        String oldPassword = editOldPassword.getText().toString().trim();
        String newPassword = editNewPassword.getText().toString().trim();
        String confirmNewPassword = editConfirmNewPassword.getText().toString().trim();

        if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmNewPassword)) {
            Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            Toast.makeText(getContext(), "New password and confirm password do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);

            // Reauthenticate the user
            user.reauthenticate(credential).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // If reauthentication is successful, change the password
                    user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            Toast.makeText(getContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Password update failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Toast.makeText(getContext(), "Old password is incorrect", Toast.LENGTH_SHORT).show();
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
        Intent intent = new Intent(getContext(), Login.class);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
    }
}
