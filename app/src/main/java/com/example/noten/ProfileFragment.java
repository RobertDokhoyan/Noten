package com.example.noten;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private ImageView avatarCircle;
    private Button changeAvatarButton, saveNicknameButton, savePasswordButton, logoutButton;
    private EditText editNickname, editPassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        avatarCircle = view.findViewById(R.id.avatar_circle);
        changeAvatarButton = view.findViewById(R.id.change_avatar_button);
        saveNicknameButton = view.findViewById(R.id.save_nickname_button);
        savePasswordButton = view.findViewById(R.id.save_password_button);
        logoutButton = view.findViewById(R.id.logout);
        editNickname = view.findViewById(R.id.edit_nickname);
        editPassword = view.findViewById(R.id.edit_password);

        // Set the color of the change avatar button
        changeAvatarButton.setBackgroundColor(getResources().getColor(R.color.light_gold));

        changeAvatarButton.setOnClickListener(v -> openGallery());
        saveNicknameButton.setOnClickListener(v -> saveNickname());
        savePasswordButton.setOnClickListener(v -> savePassword());
        logoutButton.setOnClickListener(v -> logout());

        return view;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 100);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == getActivity().RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            avatarCircle.setImageURI(imageUri);
        }
    }

    private void saveNickname() {
        String nickname = editNickname.getText().toString().trim();
        if (TextUtils.isEmpty(nickname)) {
            Toast.makeText(getContext(), "Please enter a nickname", Toast.LENGTH_SHORT).show();
        } else {
            // Logic to save the nickname
            Toast.makeText(getContext(), "Nickname saved successfully", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePassword() {
        String password = editPassword.getText().toString().trim();
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(getContext(), "Please enter a password", Toast.LENGTH_SHORT).show();
        } else if (password.length() < 6) {
            Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                user.updatePassword(password).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to update password", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void logout() {
        new AlertDialog.Builder(getContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(getContext(), Login.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    getActivity().finish();
                })
                .setNegativeButton("No", null)
                .show();
    }
}
