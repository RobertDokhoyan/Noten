package com.example.noten;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class MyProjectsFragment extends Fragment {

    private ListView listViewProjects;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_my_projects, container, false);
        listViewProjects = rootView.findViewById(R.id.listViewProjects);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        List<Project> projects = ProjectManager.getProjects();
        ArrayAdapter<Project> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, projects);
        listViewProjects.setAdapter(adapter);

        listViewProjects.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View item, int position, long id) {
                Project project = projects.get(position);
                showSaveToGalleryDialog(project);
            }
        });
    }

    // Диалог с предложением сохранить изображение в галерею
    private void showSaveToGalleryDialog(Project project) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Save to Gallery");
        builder.setMessage("Do you want to save the image of the project '" + project.getName() + "' to the gallery?");
        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                saveProjectImageToGallery(project);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Сохранение изображения (PNG) проекта в галерею через MediaStore
    private void saveProjectImageToGallery(Project project) {
        File imageFile = new File(project.getImagePath());
        if (!imageFile.exists()) {
            Toast.makeText(getActivity(), "Image file not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (bitmap == null) {
            Toast.makeText(getActivity(), "Error decoding image", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues values = new ContentValues();
        String imageName = "notes_image_" + System.currentTimeMillis() + ".png";
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Noten");
        Uri uri = getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try (OutputStream outStream = getActivity().getContentResolver().openOutputStream(uri)) {
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)) {
                Toast.makeText(getActivity(), "Image saved to gallery", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), "Error saving image", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "IOException: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
